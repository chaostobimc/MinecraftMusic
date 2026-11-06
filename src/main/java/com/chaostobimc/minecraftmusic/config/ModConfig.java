package com.chaostobimc.minecraftmusic.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistierte Einstellungen und lokale Bibliothek.
 * Liegt in .minecraft/config/minecraftmusic.json
 */
public class ModConfig {

    // --- Wiedergabe ---
    public boolean svcBroadcast = true;
    public float volume = 0.80f;
    public float broadcastDistance = 64.0f;
    public boolean shuffle = false;
    public int repeatMode = 0; // 0 = aus, 1 = alles, 2 = ein Titel
    public boolean highQualityThumbnails = true;

    // --- UI ---
    public boolean compactRows = false;

    // --- Bibliothek ---
    public List<StoredTrack> favorites = new ArrayList<>();
    public List<StoredPlaylist> playlists = new ArrayList<>();
    public List<StoredTrack> recentlyPlayed = new ArrayList<>();

    public StoredPlaylist getOrCreatePlaylist(String name) {
        for (StoredPlaylist p : playlists) {
            if (p.name.equalsIgnoreCase(name)) {
                return p;
            }
        }
        StoredPlaylist p = new StoredPlaylist(name);
        playlists.add(p);
        return p;
    }

    public boolean isFavorite(String videoId) {
        if (videoId == null) {
            return false;
        }
        for (StoredTrack t : favorites) {
            if (videoId.equals(t.id)) {
                return true;
            }
        }
        return false;
    }

    public boolean toggleFavorite(StoredTrack track) {
        if (track == null || track.id == null) {
            return false;
        }
        for (int i = 0; i < favorites.size(); i++) {
            if (track.id.equals(favorites.get(i).id)) {
                favorites.remove(i);
                return false;
            }
        }
        favorites.add(track);
        return true;
    }

    /** Ein Song, so wie er in Favoriten/Playlists gespeichert wird. */
    public static class StoredTrack {
        public String id;
        public String title;
        public String artist;
        public String album;
        public long durationMs;
        public String artworkUrl;

        public StoredTrack() {
        }

        public StoredTrack(String id, String title, String artist, String album, long durationMs, String artworkUrl) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.durationMs = durationMs;
            this.artworkUrl = artworkUrl;
        }
    }

    /** Eine eigene Playlist. */
    public static class StoredPlaylist {
        public String name;
        public String description = "";
        public long createdAt = System.currentTimeMillis();
        public List<StoredTrack> tracks = new ArrayList<>();

        public StoredPlaylist() {
        }

        public StoredPlaylist(String name) {
            this.name = name;
        }
    }
}
