package com.chaostobimc.minecraftmusic.audio;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail;
import dev.lavalink.youtube.clients.MusicWithThumbnail;
import dev.lavalink.youtube.clients.WebEmbeddedWithThumbnail;
import dev.lavalink.youtube.clients.WebWithThumbnail;
import dev.lavalink.youtube.clients.skeleton.Client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Kapselt LavaPlayer + youtube-source.
 * <p>
 * Wir lassen LavaPlayer direkt in mono / 48 kHz / 16 bit PCM dekodieren -
 * genau das Format, das Simple Voice Chat fuer Opus braucht.
 */
public final class AudioEngine {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SAMPLES = 960;

    private static AudioEngine instance;

    private final AudioPlayerManager manager;
    private final YoutubeAudioSourceManager youtube;
    private final ExecutorService loader = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "MCMusic-Loader");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean failed;
    private volatile String failureReason = "";

    // LavaPlayers eingebautes YouTube ist deprecated, muss aber per Klassenangabe
    // ausgeschlossen werden - dafuer verweist der Aufruf auf den Typ.
    @SuppressWarnings("deprecation")
    private AudioEngine() {
        DefaultAudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioConfiguration configuration = playerManager.getConfiguration();
        configuration.setOutputFormat(new Pcm16AudioDataFormat(1, SAMPLE_RATE, FRAME_SAMPLES, false));
        configuration.setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        configuration.setOpusEncodingQuality(10);
        playerManager.setFrameBufferDuration(200);

        // Eingebautes (veraltetes) YouTube ausschliessen - wir nutzen youtube-source.
        AudioSourceManagers.registerRemoteSources(playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);

        Client[] clients = new Client[]{
                new MusicWithThumbnail(),
                new WebWithThumbnail(),
                new AndroidVrWithThumbnail(),
                new WebEmbeddedWithThumbnail()
        };
        this.youtube = new YoutubeAudioSourceManager(true, clients);
        playerManager.registerSourceManager(youtube);

        this.manager = playerManager;
    }

    public static synchronized AudioEngine get() {
        if (instance == null) {
            instance = new AudioEngine();
        }
        return instance;
    }

    public AudioPlayerManager manager() {
        return manager;
    }

    public boolean isFailed() {
        return failed;
    }

    public String failureReason() {
        return failureReason;
    }

    /** Suche auf YouTube Music. Ergebnis kommt auf einem Worker-Thread. */
    public void search(String query, int limit, Consumer<Result> callback) {
        if (query == null || query.isBlank()) {
            callback.accept(Result.error("Leere Suche"));
            return;
        }
        loader.submit(() -> {
            String identifier = "ytmsearch:" + query.trim();
            List<Track> tracks = new ArrayList<>();
            try {
                manager.loadItem(identifier, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        tracks.add(Track.from(track));
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        int i = 0;
                        for (AudioTrack t : playlist.getTracks()) {
                            if (i++ >= limit) {
                                break;
                            }
                            tracks.add(Track.from(t));
                        }
                    }

                    @Override
                    public void noMatches() {
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        MinecraftMusic.LOGGER.warn("Suche fehlgeschlagen: {}", exception.getMessage());
                    }
                }).get();
            } catch (Throwable t) {
                MinecraftMusic.LOGGER.warn("Suche abgebrochen", t);
                failed = true;
                failureReason = String.valueOf(t.getMessage());
                callback.accept(Result.error("Suche fehlgeschlagen: " + t.getMessage()));
                return;
            }
            callback.accept(Result.ok(tracks));
        });
    }

    /** Laedt einen einzelnen Titel (z. B. aus Favoriten) nach. */
    public void loadById(String videoId, Consumer<Result> callback) {
        if (videoId == null || videoId.isBlank()) {
            callback.accept(Result.error("Ungueltige ID"));
            return;
        }
        String identifier = normalizeVideoId(videoId);
        loader.submit(() -> {
            List<Track> tracks = new ArrayList<>();
            try {
                manager.loadItem(identifier, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        tracks.add(Track.from(track));
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        if (!playlist.getTracks().isEmpty()) {
                            tracks.add(Track.from(playlist.getTracks().get(0)));
                        }
                    }

                    @Override
                    public void noMatches() {
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        MinecraftMusic.LOGGER.warn("Laden fehlgeschlagen: {}", exception.getMessage());
                    }
                }).get();
            } catch (Throwable t) {
                callback.accept(Result.error("Titel konnte nicht geladen werden"));
                return;
            }
            callback.accept(Result.ok(tracks));
        });
    }

    /**
     * Favoriten speichern je nach Herkunft eine reine Video-ID oder eine
     * komplette Watch-URL. Beides muss wieder ladbar sein.
     */
    static String normalizeVideoId(String raw) {
        String value = raw.trim();
        int vIndex = value.indexOf("v=");
        if (value.contains("youtube.com/watch") && vIndex >= 0) {
            String rest = value.substring(vIndex + 2);
            int amp = rest.indexOf('&');
            return amp >= 0 ? rest.substring(0, amp) : rest;
        }
        int shortIndex = value.indexOf("youtu.be/");
        if (shortIndex >= 0) {
            String rest = value.substring(shortIndex + 9);
            int query = rest.indexOf('?');
            return query >= 0 ? rest.substring(0, query) : rest;
        }
        return value;
    }

    /** Laedt eine YouTube-Playlist (URL oder ID). */
    public void loadPlaylist(String url, int limit, Consumer<Result> callback) {
        loader.submit(() -> {
            List<Track> tracks = new ArrayList<>();
            try {
                manager.loadItem(url, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        tracks.add(Track.from(track));
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        int i = 0;
                        for (AudioTrack t : playlist.getTracks()) {
                            if (i++ >= limit) {
                                break;
                            }
                            tracks.add(Track.from(t));
                        }
                    }

                    @Override
                    public void noMatches() {
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        MinecraftMusic.LOGGER.warn("Playlist-Laden fehlgeschlagen: {}", exception.getMessage());
                    }
                }).get();
            } catch (Throwable t) {
                callback.accept(Result.error("Playlist konnte nicht geladen werden"));
                return;
            }
            callback.accept(Result.ok(tracks));
        });
    }

    public void shutdown() {
        try {
            manager.shutdown();
            loader.shutdownNow();
        } catch (Throwable ignored) {
        }
    }

    /** Ergebnis eines Ladevorgangs. */
    public record Result(List<Track> tracks, String error) {

        public static Result ok(List<Track> tracks) {
            return new Result(tracks == null ? Collections.emptyList() : tracks, null);
        }

        public static Result error(String message) {
            return new Result(Collections.emptyList(), message);
        }

        public boolean isError() {
            return error != null;
        }
    }
}
