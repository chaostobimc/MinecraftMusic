package com.chaostobimc.minecraftmusic.audio;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.chaostobimc.minecraftmusic.config.ConfigManager;
import com.chaostobimc.minecraftmusic.net.MusicFramePayload;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Zieht PCM-Frames aus LavaPlayer, mischt sie auf mono, regelt die
 * Lautstaerke, encodiert sie mit Opus und schickt sie an den Server.
 * <p>
 * Der eigene Client hoert die Musik dabei kostenlos mit, weil der Server
 * die Frames auch an uns zurueckschickt (wir sind ein SVC-Nutzer wie jeder
 * andere). So bleibt Bild und Ton fuer alle identisch.
 */
public final class PlaybackPump {

    private static PlaybackPump instance;

    private final OpusEncoderWrapper encoder = new OpusEncoderWrapper();
    private LocalPlayback localPlayback;
    private final short[] monoBuffer = new short[AudioEngine.FRAME_SAMPLES];
    private final AtomicInteger sequence = new AtomicInteger();
    private final Object lock = new Object();

    private Thread thread;
    private volatile boolean running;
    private volatile boolean broadcastEnabled = true;
    private volatile int generation;

    private long framesSent;

    /** Sollzeit des naechsten Frames (20 ms Takt) - verhindert Bursts. */
    private long nextFrameAt;

    private PlaybackPump() {
    }

    /** Initialisiert die lokale Ausgabe (idempotent, thread-safe). */
    public synchronized void prepare() {
        ensureLocalPlayback();
    }

    public static synchronized PlaybackPump get() {
        if (instance == null) {
            instance = new PlaybackPump();
        }
        return instance;
    }

    /**
     * Schaltet den Voice-Chat-Broadcast. Die lokale Wiedergabe laeuft immer -
     * der SVC-Kanal filtert den Sender heraus, damit nichts doppelt klingt.
     */
    public void setBroadcastEnabled(boolean enabled) {
        this.broadcastEnabled = enabled;
        ensureLocalPlayback();
        if (localPlayback != null) {
            localPlayback.setVolume(ConfigManager.get().volume);
        }
    }

    private void ensureLocalPlayback() {
        if (localPlayback != null) {
            return;
        }
        try {
            localPlayback = new LocalPlayback();
            localPlayback.setVolume(ConfigManager.get().volume);
        } catch (Throwable t) {
            MinecraftMusic.LOGGER.warn("Lokale Wiedergabe nicht verfuegbar - nur Voice-Chat-Broadcast", t);
        }
    }

    public boolean isBroadcastEnabled() {
        return broadcastEnabled;
    }

    /** Wird beim Trackwechsel aufgerufen: Encoder und Sequenz zuruecksetzen. */
    public void beginTrack(int newGeneration, Track track) {
        synchronized (lock) {
            generation = newGeneration;
            sequence.set(0);
            framesSent = 0;
            nextFrameAt = 0L;
            encoder.reset();
        }
        ensureLocalPlayback();
        if (localPlayback != null) {
            localPlayback.reset();
        }
        ensureThread();
    }

    public void restartSync() {
        synchronized (lock) {
            sequence.set(0);
            encoder.reset();
        }
        if (localPlayback != null) {
            localPlayback.reset();
        }
    }

    public void stopStream() {
        // Nichts zu tun: der Player liefert keine Frames mehr,
        // der Server raeumt den Stream per Timeout ab.
    }

    private void ensureThread() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "MCMusic-Pump");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        nextFrameAt = 0L; // beim ersten Frame initialisieren
        while (running) {
            try {
                MusicPlayer mp = MusicPlayer.get();
                int myGeneration;
                synchronized (lock) {
                    myGeneration = generation;
                }

                if (mp.current() == null) {
                    Thread.sleep(50);
                    continue;
                }

                AudioFrame frame = mp.provideFrame();
                if (frame == null) {
                    // provideFrame() hat bereits bis zu 20 ms gewartet
                    continue;
                }

                synchronized (lock) {
                    if (myGeneration != generation) {
                        continue;
                    }
                }

                // Auf 20-ms-Takt halten: LavaPlayer puffert, wir duerfen die
                // Frames nicht in einem Schwung abschicken.
                long now = System.nanoTime();
                if (nextFrameAt == 0L) {
                    nextFrameAt = now;
                }
                long waitNanos = nextFrameAt - now;
                if (waitNanos > 0L) {
                    long millis = waitNanos / 1_000_000L;
                    int nanos = (int) (waitNanos % 1_000_000L);
                    try {
                        Thread.sleep(millis, nanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else if (waitNanos < -200_000_000L) {
                    // Mehr als 200 ms im Rueckstand (z. B. nach Pause): neu synchronisieren
                    nextFrameAt = now;
                }
                nextFrameAt += 20_000_000L; // 20 ms

                // getData() kann einen groesseren Puffer liefern; nur die
                // tatsaechlich gefuellten Bytes auswerten.
                byte[] raw = new byte[frame.getDataLength()];
                frame.getData(raw, 0);
                short[] pcm = AudioFormatBridge.bytesToShortsLe(raw);
                AudioFormatBridge.toMono(pcm, false, monoBuffer);
                float volume = ConfigManager.get().volume;
                AudioFormatBridge.applyVolume(monoBuffer, volume);

                // Lokale Wiedergabe laeuft immer mit (auch bei SVC-Broadcast).
                LocalPlayback local = localPlayback;
                if (local != null) {
                    local.setVolume(volume);
                    local.push(monoBuffer.clone());
                }

                if (!broadcastEnabled) {
                    continue;
                }

                byte[] opus = encoder.encode(monoBuffer);
                if (opus.length == 0) {
                    continue;
                }

                int seq = sequence.getAndIncrement();
                ClientNetworkingHolder.sendFrame(new MusicFramePayload(0, seq, opus));
                framesSent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                MinecraftMusic.LOGGER.error("Fehler im Wiedergabe-Pump", t);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public long framesSent() {
        return framesSent;
    }

    public void shutdown() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
        if (localPlayback != null) {
            localPlayback.close();
            localPlayback = null;
        }
    }
}
