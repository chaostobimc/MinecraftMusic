package com.chaostobimc.minecraftmusic.audio;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.chaostobimc.minecraftmusic.config.ConfigManager;
import com.chaostobimc.minecraftmusic.config.ModConfig;
import com.chaostobimc.minecraftmusic.net.MusicControlPayload;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Wiedergabelogik: Queue, Shuffle, Repeat, Position.
 * Liefert PCM-Frames an den {@link PlaybackPump}, der sie encodiert
 * und (optional) an den Server schickt.
 */
public final class MusicPlayer {

    private static MusicPlayer instance;

    private final AudioPlayer player;
    private final List<Track> queue = new CopyOnWriteArrayList<>();
    private final List<Track> history = new ArrayList<>();
    private final List<Consumer<Track>> trackListeners = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private int queueIndex = -1;
    private volatile Track current;
    private boolean stopping;
    private volatile int playGeneration;
    /** Zaehlt Nachladevorgaenge, damit ein spaetes Ergebnis keinen neueren Titel ueberschreibt. */
    private int resolveGeneration;

    private MusicPlayer() {
        PlaybackPump.get().prepare();
        this.player = AudioEngine.get().manager().createPlayer();
        this.player.setVolume(100); // Lautstaerke regeln wir selbst pro Frame
        this.player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackEnd(AudioPlayer audioPlayer, AudioTrack track, AudioTrackEndReason endReason) {
                if (endReason.mayStartNext && !stopping) {
                    Minecraft.getInstance().execute(() -> nextInternal(true));
                }
            }

            @Override
            public void onTrackException(AudioPlayer audioPlayer, AudioTrack track,
                                         com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                MinecraftMusic.LOGGER.warn("Wiedergabefehler: {}", exception.getMessage());
                if (exception.severity == com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON) {
                    Minecraft.getInstance().execute(() -> nextInternal(true));
                }
            }
        });
    }

    public static synchronized MusicPlayer get() {
        if (instance == null) {
            instance = new MusicPlayer();
        }
        return instance;
    }

    public AudioPlayer lavaPlayer() {
        return player;
    }

    // ------------------------------------------------------------------
    //  Queue
    // ------------------------------------------------------------------

    public void playNow(Track track) {
        if (track == null) {
            return;
        }
        player.stopTrack();
        stopping = false;
        queue.clear();
        history.clear();
        queue.add(track);
        queueIndex = 0;
        startTrack(track);
    }

    public void playQueue(List<Track> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        stopping = false;
        queue.clear();
        history.clear();
        queue.addAll(tracks);
        queueIndex = Math.max(0, Math.min(startIndex, queue.size() - 1));
        startTrack(queue.get(queueIndex));
    }

    public void enqueue(Track track) {
        if (track == null) {
            return;
        }
        queue.add(track);
        if (current == null) {
            queueIndex = queue.size() - 1;
            startTrack(track);
        }
    }

    private void startTrack(Track track) {
        if (track == null) {
            return;
        }
        if (!track.hasAudio()) {
            // Aus dem Speicher geladener Track: nachladen. Der Callback laeuft auf einem
            // Loader-Thread, deshalb wird das Ergebnis auf den Spiel-Thread umgehaengt.
            int generation = ++resolveGeneration;
            AudioEngine.get().loadById(track.id, result -> Minecraft.getInstance().execute(() -> {
                if (generation != resolveGeneration) {
                    return; // Inzwischen wurde etwas anderes gestartet.
                }
                if (result.isError() || result.tracks().isEmpty()) {
                    MinecraftMusic.LOGGER.warn("Konnte '{}' nicht laden", track.title);
                    nextInternal(true);
                    return;
                }
                Track loaded = result.tracks().get(0);
                startResolved(new Track(track.id, track.title, track.artist, track.album,
                        track.durationMs, track.artworkUrl).withAudioTrack(loaded.audioTrack()));
            }));
            return;
        }
        resolveGeneration++;
        startResolved(track);
    }

    private synchronized void startResolved(Track track) {
        current = track;
        playGeneration++;
        // LavaPlayer-Tracks sind nach dem Abspielen verbraucht (Position, Zustand).
        // Fuer Wiederholung/erneutes Abspielen braucht es eine frische Kopie.
        AudioTrack toPlay = track.audioTrack().makeClone();
        player.playTrack(toPlay);

        PlaybackPump.get().beginTrack(playGeneration, track);
        sendControlStart(track);

        for (Consumer<Track> listener : trackListeners) {
            listener.accept(track);
        }
    }

    /** Meldet den Stream fuer den aktuellen Titel an, wenn Broadcasting aktiv ist. */
    private void sendControlStart(Track track) {
        boolean broadcast = ConfigManager.get().svcBroadcast && ClientNetworkingHolder.isServerReachable();
        PlaybackPump.get().setBroadcastEnabled(broadcast);
        if (broadcast) {
            announceCurrent();
        }
    }

    // ------------------------------------------------------------------
    //  Steuerung
    // ------------------------------------------------------------------

    public void pause() {
        player.setPaused(true);
    }

    public void resume() {
        player.setPaused(false);
        // Waehrend der Pause kommen keine Frames an, der Server raeumt den
        // Stream per Timeout ab. Deshalb hier neu anmelden.
        Track track = current;
        if (track != null) {
            sendControlStart(track);
        }
    }

    public void togglePause() {
        if (player.isPaused()) {
            resume();
        } else {
            pause();
        }
    }

    public boolean isPaused() {
        return player.isPaused();
    }

    public void stop() {
        stopping = true;
        player.stopTrack();
        queue.clear();
        queueIndex = -1;
        current = null;
        PlaybackPump.get().stopStream();
        ClientNetworkingHolder.sendControl(MusicControlPayload.stop());
    }

    public void next() {
        stopping = false;
        nextInternal(false);
    }

    private void nextInternal(boolean auto) {
        if (queue.isEmpty()) {
            current = null;
            PlaybackPump.get().stopStream();
            ClientNetworkingHolder.sendControl(MusicControlPayload.stop());
            return;
        }

        ModConfig config = ConfigManager.get();
        boolean repeatOne = config.repeatMode == 2 && auto;
        if (repeatOne && current != null) {
            startTrack(current);
            return;
        }

        if (config.shuffle && queue.size() > 1) {
            int nextIndex = random.nextInt(queue.size());
            if (nextIndex == queueIndex) {
                nextIndex = (nextIndex + 1) % queue.size();
            }
            queueIndex = nextIndex;
        } else {
            queueIndex++;
            if (queueIndex >= queue.size()) {
                if (config.repeatMode == 1) {
                    queueIndex = 0;
                } else {
                    queueIndex = -1;
                    current = null;
                    PlaybackPump.get().stopStream();
                    ClientNetworkingHolder.sendControl(MusicControlPayload.stop());
                    return;
                }
            }
        }
        startTrack(queue.get(queueIndex));
    }

    public void previous() {
        if (queue.isEmpty()) {
            return;
        }
        stopping = false;
        queueIndex = Math.max(0, queueIndex - 1);
        startTrack(queue.get(queueIndex));
    }

    public void seek(long positionMs) {
        AudioTrack playing = player.getPlayingTrack();
        if (playing != null) {
            playing.setPosition(Math.max(0, positionMs));
            PlaybackPump.get().restartSync();
        }
    }

    /**
     * Schaltet den Voice-Chat-Broadcast ein/aus und meldet den Stream
     * entsprechend an oder ab. Die lokale Wiedergabe laeuft weiter.
     */
    public void setBroadcastEnabled(boolean enabled) {
        ModConfig config = ConfigManager.get();
        config.svcBroadcast = enabled;
        ConfigManager.save();

        PlaybackPump.get().setBroadcastEnabled(enabled && ClientNetworkingHolder.isServerReachable());

        if (current == null) {
            return;
        }
        if (enabled && ClientNetworkingHolder.isServerReachable()) {
            announceCurrent();
        } else {
            ClientNetworkingHolder.sendControl(MusicControlPayload.stop());
        }
    }

    /** Meldet den laufenden Titel beim Server an (Stream oeffnen). */
    private void announceCurrent() {
        Track track = current;
        if (track == null) {
            return;
        }
        ModConfig config = ConfigManager.get();
        ClientNetworkingHolder.sendControl(MusicControlPayload.start(
                config.volume, config.broadcastDistance, track.title, track.artist));
    }

    public void toggleShuffle() {
        ModConfig config = ConfigManager.get();
        config.shuffle = !config.shuffle;
        ConfigManager.save();
    }

    public void cycleRepeat() {
        ModConfig config = ConfigManager.get();
        config.repeatMode = (config.repeatMode + 1) % 3;
        ConfigManager.save();
    }

    public void setVolume(float volume) {
        ModConfig config = ConfigManager.get();
        config.volume = Math.max(0F, Math.min(1F, volume));
        ConfigManager.save();
        if (current != null && broadcastEnabled()) {
            ClientNetworkingHolder.sendControl(
                    MusicControlPayload.update(config.volume, config.broadcastDistance));
        }
    }

    private boolean broadcastEnabled() {
        ModConfig config = ConfigManager.get();
        return config.svcBroadcast && ClientNetworkingHolder.isServerReachable();
    }

    // ------------------------------------------------------------------
    //  Status
    // ------------------------------------------------------------------

    public Track current() {
        return current;
    }

    public boolean isPlaying() {
        return current != null && !player.isPaused() && player.getPlayingTrack() != null;
    }

    public long positionMs() {
        AudioTrack track = player.getPlayingTrack();
        return track == null ? 0L : track.getPosition();
    }

    public List<Track> queueSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    public List<Track> historySnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public void addTrackListener(Consumer<Track> listener) {
        trackListeners.add(listener);
    }

    public void removeTrackListener(Consumer<Track> listener) {
        trackListeners.remove(listener);
    }

    /**
     * Holt den naechsten fertigen PCM-Frame fuer den Pump-Thread.
     * <p>
     * Wichtig: {@code player.provide()} blockiert <b>nicht</b> - es pollt
     * einen Puffer und liefert sofort null, wenn noch nichts dekodiert ist.
     * Ohne Timeout wuerde der Pump-Thread in einer engen Schleife laufen und
     * den Server mit Frames fluten. Deshalb hier mit 20-ms-Timeout warten.
     */
    public AudioFrame provideFrame() {
        try {
            return player.provide(20, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
