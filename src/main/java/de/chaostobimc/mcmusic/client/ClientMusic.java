package de.chaostobimc.mcmusic.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.api.LocalPlaylist;
import de.chaostobimc.mcmusic.api.Track;
import de.chaostobimc.mcmusic.net.Payloads;
import de.chaostobimc.mcmusic.provider.Providers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client side state: what the server is playing, the local library (favourites, playlists,
 * history), the play queue and the search results.
 */
public final class ClientMusic {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ClientMusic INSTANCE = new ClientMusic();

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "MCMusic-client");
        thread.setDaemon(true);
        return thread;
    });

    public Payloads.PlaybackState state = Payloads.PlaybackState.idle("");

    public final List<Track> favorites = new ArrayList<>();
    public final List<LocalPlaylist> playlists = new ArrayList<>();
    public final List<Track> recentlyPlayed = new ArrayList<>();

    public final List<Track> queue = new ArrayList<>();
    public int queueIndex = -1;
    public boolean shuffle;
    public boolean repeat;

    public final List<Track> results = new ArrayList<>();
    public String lastQuery = "";
    public String searchError = "";
    public boolean searching;

    /** Set when we want to move on to the next song after the current one ended. */
    private String lastStateTrackId = "";
    private String notice = "";
    private long noticeUntil;

    private boolean loaded;
    /** Set when the user pressed stop, so the idle state does not start the next song. */
    private boolean stoppedByUser;

    private ClientMusic() {
    }

    public static ClientMusic get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // server state
    // ------------------------------------------------------------------

    public void onState(Payloads.PlaybackState newState) {
        String previousTrack = lastStateTrackId;
        boolean hadTrack = !previousTrack.isEmpty();
        state = newState;

        if (newState.hasTrack()) {
            lastStateTrackId = newState.trackId();
            if (!newState.trackId().equals(previousTrack)) {
                Track track = new Track(newState.trackId(), newState.title(), newState.artist(),
                        newState.durationSeconds(), "");
                addRecent(track);
            }
        } else {
            lastStateTrackId = "";
            if (hadTrack && newState.error().isEmpty()) {
                onTrackEnded();
            }
            if (!newState.error().isEmpty()) {
                notify(newState.error());
            }
        }
    }

    private void onTrackEnded() {
        if (stoppedByUser) {
            stoppedByUser = false;
            return;
        }
        if (repeat && !queue.isEmpty()) {
            playTrack(queue.get(Math.max(0, queueIndex)));
            return;
        }
        next(true);
    }

    // ------------------------------------------------------------------
    // playback control
    // ------------------------------------------------------------------

    public void play(Track track) {
        queue.clear();
        queue.add(track);
        queueIndex = 0;
        playTrack(track);
    }

    public void playQueue(List<Track> tracks, int index) {
        if (tracks.isEmpty()) {
            return;
        }
        queue.clear();
        queue.addAll(tracks);
        queueIndex = Math.max(0, Math.min(index, queue.size() - 1));
        playTrack(queue.get(queueIndex));
    }

    private void playTrack(Track track) {
        stoppedByUser = false;
        if (!send(new Payloads.PlayRequest(track))) {
            return;
        }
        addRecent(track);
    }

    public void togglePlayPause() {
        if (!state.hasTrack()) {
            // nothing playing - start the queue, or the favourites as a fallback
            if (!queue.isEmpty()) {
                playTrack(queue.get(Math.max(0, Math.min(queueIndex, queue.size() - 1))));
            } else if (!favorites.isEmpty()) {
                playQueue(new ArrayList<>(favorites), 0);
            }
            return;
        }
        send(state.playing() ? Payloads.ControlRequest.of("pause") : Payloads.ControlRequest.of("resume"));
    }

    public void next() {
        next(false);
    }

    private void next(boolean auto) {
        if (queue.isEmpty()) {
            return;
        }
        if (shuffle && queue.size() > 1) {
            int next = queueIndex;
            while (next == queueIndex) {
                next = (int) (Math.random() * queue.size());
            }
            queueIndex = next;
        } else {
            queueIndex++;
            if (queueIndex >= queue.size()) {
                if (!auto || repeat) {
                    queueIndex = 0;
                } else {
                    queueIndex = queue.size() - 1;
                    return;
                }
            }
        }
        playTrack(queue.get(queueIndex));
    }

    public void previous() {
        if (queue.isEmpty()) {
            return;
        }
        queueIndex--;
        if (queueIndex < 0) {
            queueIndex = queue.size() - 1;
        }
        playTrack(queue.get(queueIndex));
    }

    public void seek(int seconds) {
        if (state.hasTrack()) {
            send(new Payloads.ControlRequest("seek", Math.max(0, seconds)));
        }
    }

    public void stop() {
        stoppedByUser = true;
        send(Payloads.ControlRequest.of("stop"));
    }

    /** 0..1 - forwarded to the server, which applies it to the outgoing stream. */
    public void setVolume(float volume) {
        float clamped = Math.max(0f, Math.min(1f, volume));
        de.chaostobimc.mcmusic.config.MusicConfig.get().volume = clamped;
        send(new Payloads.ControlRequest("volume", (int) Math.round(clamped * 100)));
    }

    public void requestState() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.getConnection() == null || !ClientPlayNetworking.canSend(Payloads.Hello.TYPE)) {
            return;
        }
        ClientPlayNetworking.send(Payloads.Hello.INSTANCE);
    }

    private boolean send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) {
            notify("Not connected to a world.");
            return false;
        }
        if (!ClientPlayNetworking.canSend(payload.type())) {
            notify("This server does not run MC Music - playback needs the mod on the server.");
            return false;
        }
        ClientPlayNetworking.send(payload);
        return true;
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    public void search(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            results.clear();
            searchError = "";
            return;
        }
        lastQuery = trimmed;
        searching = true;
        searchError = "";
        executor.submit(() -> {
            List<Track> found = List.of();
            String error = "";
            try {
                found = Providers.search(trimmed, 40);
            } catch (Exception exception) {
                error = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            }
            List<Track> result = found;
            String failure = error;
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                results.clear();
                results.addAll(result);
                searchError = failure;
                searching = false;
            });
        });
    }

    /** Resolves a pasted link or id into a track and plays it. */
    public void playFromUrl(String input) {
        String id = Providers.parseTrackId(input);
        if (id.isEmpty()) {
            notify("That is not a YouTube Music link or id.");
            return;
        }
        Track track = new Track(id, "YouTube Music", "Unknown artist", 0,
                "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg");
        queue.clear();
        queue.add(track);
        queueIndex = 0;
        playTrack(track);
    }

    // ------------------------------------------------------------------
    // library
    // ------------------------------------------------------------------

    public boolean isFavorite(String trackId) {
        for (Track track : favorites) {
            if (track.id().equals(trackId)) {
                return true;
            }
        }
        return false;
    }

    public void toggleFavorite(Track track) {
        if (isFavorite(track.id())) {
            favorites.removeIf(existing -> existing.id().equals(track.id()));
        } else {
            favorites.add(0, track);
        }
        save();
    }

    public void toggleFavoriteCurrent() {
        if (state.hasTrack()) {
            toggleFavorite(new Track(state.trackId(), state.title(), state.artist(), state.durationSeconds(), ""));
        }
    }

    public LocalPlaylist createPlaylist(String name) {
        LocalPlaylist playlist = new LocalPlaylist(name, de.chaostobimc.mcmusic.client.ui.Theme.accentFor(playlists.size()));
        playlists.add(playlist);
        save();
        return playlist;
    }

    public void deletePlaylist(LocalPlaylist playlist) {
        playlists.remove(playlist);
        save();
    }

    public void addToPlaylist(LocalPlaylist playlist, Track track) {
        playlist.add(track);
        save();
        notify("Added to " + playlist.name);
    }

    private void addRecent(Track track) {
        if (track == null || track.id().isEmpty()) {
            return;
        }
        recentlyPlayed.removeIf(existing -> existing.id().equals(track.id()));
        recentlyPlayed.add(0, track);
        while (recentlyPlayed.size() > 30) {
            recentlyPlayed.remove(recentlyPlayed.size() - 1);
        }
        save();
    }

    // ------------------------------------------------------------------
    // notifications
    // ------------------------------------------------------------------

    public void notify(String message) {
        notice = message == null ? "" : message;
        noticeUntil = System.currentTimeMillis() + 5000L;
    }

    public String notice() {
        return System.currentTimeMillis() < noticeUntil ? notice : "";
    }

    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------

    private static final class Library {
        List<Track> favorites = new ArrayList<>();
        List<LocalPlaylist> playlists = new ArrayList<>();
        List<Track> recent = new ArrayList<>();
        boolean shuffle;
        boolean repeat;
    }

    public void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = path();
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Library library = GSON.fromJson(reader, Library.class);
            if (library != null) {
                if (library.favorites != null) {
                    favorites.addAll(library.favorites);
                }
                if (library.playlists != null) {
                    playlists.addAll(library.playlists);
                }
                if (library.recent != null) {
                    recentlyPlayed.addAll(library.recent);
                }
                shuffle = library.shuffle;
                repeat = library.repeat;
            }
        } catch (Exception exception) {
            MCMusic.LOGGER.warn("Could not read the local music library", exception);
        }
    }

    public void save() {
        Library library = new Library();
        library.favorites = new ArrayList<>(favorites);
        library.playlists = new ArrayList<>(playlists);
        library.recent = new ArrayList<>(recentlyPlayed);
        library.shuffle = shuffle;
        library.repeat = repeat;
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(library, writer);
            }
        } catch (Exception exception) {
            MCMusic.LOGGER.warn("Could not save the local music library", exception);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("mcmusic-library.json");
    }
}
