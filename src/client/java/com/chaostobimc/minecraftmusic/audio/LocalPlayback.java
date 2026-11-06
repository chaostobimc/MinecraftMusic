package com.chaostobimc.minecraftmusic.audio;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ShortBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Lokale Wiedergabe des eigenen Streams.
 * <p>
 * Wichtig: Der OpenAL-Context von Minecraft ist thread-lokal und gehoert dem
 * Render-Thread. Deshalb oeffnet diese Klasse ein <b>eigenes</b> Ausgabegeraet
 * mit eigenem Context auf einem eigenen Thread. Damit kann der Pump-Thread
 * Frames abliefern, ohne den Minecraft-Sound anzufassen.
 * <p>
 * Ist Simple Voice Chat aktiv, hoeren andere Spieler die Musik ueber den
 * Voice-Chat-Kanal; der Sender hoert sie hier lokal (der SVC-Kanal filtert
 * den Sender heraus, damit nichts doppelt klingt).
 */
public final class LocalPlayback {

    private static final int BUFFERS = 8;
    private static final float PREBUFFER_SECONDS = 0.25F;

    private final BlockingQueue<short[]> queue = new ArrayBlockingQueue<>(64);
    private volatile boolean running = true;
    private volatile boolean failed;
    private volatile float volume = 0.8F;
    private volatile boolean playing;

    private Thread thread;
    private long device;
    private long context;

    public LocalPlayback() {
        this.thread = new Thread(this::run, "MCMusic-LocalAudio");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0F, Math.min(1F, volume));
    }

    public void push(short[] mono) {
        if (mono == null || mono.length == 0 || failed) {
            return;
        }
        // Bei Ueberlauf aeltesten Frame verwerfen, damit der Ton nicht nachlaeuft.
        if (!queue.offer(mono)) {
            queue.poll();
            queue.offer(mono);
        }
    }

    /** Verwirft alle gepufferten Frames (z. B. bei Titelwechsel). */
    public void reset() {
        queue.clear();
    }

    public void stop() {
        queue.clear();
    }

    public boolean isStarted() {
        return playing;
    }

    public boolean isFailed() {
        return failed;
    }

    public void close() {
        running = false;
        queue.clear();
        Thread t = thread;
        if (t != null) {
            t.interrupt();
            try {
                t.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    // ------------------------------------------------------------------
    //  Audio-Thread
    // ------------------------------------------------------------------

    private void run() {
        int source = 0;
        int[] buffers = new int[BUFFERS];
        try {
            String name = ALC10.alcGetString(0L, ALC10.ALC_DEFAULT_DEVICE_SPECIFIER);
            device = ALC10.alcOpenDevice(name);
            if (device == 0L) {
                fail("Kein OpenAL-Ausgabegeraet");
                return;
            }
            context = ALC10.alcCreateContext(device, (int[]) null);
            if (context == 0L) {
                fail("OpenAL-Context konnte nicht erstellt werden");
                return;
            }
            if (!ALC10.alcMakeContextCurrent(context)) {
                fail("OpenAL-Context konnte nicht aktiviert werden");
                return;
            }
            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            AL.createCapabilities(alcCaps);

            source = AL10.alGenSources();
            if (AL10.alGetError() != AL10.AL_NO_ERROR || source == 0) {
                fail("OpenAL-Quelle konnte nicht erstellt werden");
                return;
            }
            for (int i = 0; i < BUFFERS; i++) {
                buffers[i] = AL10.alGenBuffers();
            }
            AL10.alSourcef(source, AL10.AL_PITCH, 1F);
            AL10.alSource3f(source, AL10.AL_POSITION, 0F, 0F, 0F);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcef(source, AL10.AL_GAIN, volume);

            int nextBuffer = 0;
            int preBuffered = 0;
            int preBufferFrames = Math.max(2,
                    (int) (PREBUFFER_SECONDS * AudioEngine.SAMPLE_RATE / AudioEngine.FRAME_SAMPLES));

            while (running) {
                short[] frame = queue.poll(40L, TimeUnit.MILLISECONDS);
                if (frame != null) {
                    int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                    if (queued >= BUFFERS) {
                        // Fertige Puffer zurueckholen, sonst laeuft der Ring voll.
                        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                        while (processed-- > 0) {
                            AL10.alSourceUnqueueBuffers(source);
                        }
                        queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                        if (queued >= BUFFERS) {
                            continue; // Ausgang voll - Frame verwerfen
                        }
                    }
                    int buffer = buffers[nextBuffer];
                    nextBuffer = (nextBuffer + 1) % BUFFERS;
                    ShortBuffer data = ShortBuffer.wrap(frame, 0,
                            Math.min(frame.length, AudioEngine.FRAME_SAMPLES));
                    AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, AudioEngine.SAMPLE_RATE);
                    AL10.alSourceQueueBuffers(source, buffer);
                    if (preBuffered < preBufferFrames) {
                        preBuffered++;
                    }
                }

                AL10.alSourcef(source, AL10.AL_GAIN, volume);

                int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                int queuedNow = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                if (state != AL10.AL_PLAYING && queuedNow > 0 && preBuffered >= preBufferFrames) {
                    AL10.alSourcePlay(source);
                    playing = true;
                } else if (state == AL10.AL_PLAYING) {
                    playing = true;
                } else {
                    playing = false;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            fail(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        } finally {
            try {
                if (source != 0) {
                    AL10.alSourceStop(source);
                    AL10.alDeleteSources(source);
                }
                for (int buffer : buffers) {
                    if (buffer != 0) {
                        AL10.alDeleteBuffers(buffer);
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                if (context != 0L) {
                    ALC10.alcMakeContextCurrent(0L);
                    ALC10.alcDestroyContext(context);
                }
                if (device != 0L) {
                    ALC10.alcCloseDevice(device);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void fail(String reason) {
        if (!failed) {
            failed = true;
            MinecraftMusic.LOGGER.warn("Lokale Audioausgabe nicht verfuegbar: {}", reason);
        }
    }
}
