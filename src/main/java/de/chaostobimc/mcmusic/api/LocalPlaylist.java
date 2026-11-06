package de.chaostobimc.mcmusic.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A user created playlist. Playlists live on the client and are stored as JSON.
 */
public final class LocalPlaylist {

    public String id = UUID.randomUUID().toString();
    public String name = "New playlist";
    public String description = "";
    /** Accent colour of the playlist card, ARGB. */
    public int color = 0xFF3D3DF2;
    public List<Track> tracks = new ArrayList<>();

    public LocalPlaylist() {
    }

    public LocalPlaylist(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public int size() {
        return tracks.size();
    }

    public int totalSeconds() {
        int total = 0;
        for (Track track : tracks) {
            total += Math.max(0, track.durationSeconds());
        }
        return total;
    }

    public boolean contains(String trackId) {
        for (Track track : tracks) {
            if (track.id().equals(trackId)) {
                return true;
            }
        }
        return false;
    }

    public void add(Track track) {
        remove(track.id());
        tracks.add(0, track);
    }

    public void remove(String trackId) {
        tracks.removeIf(track -> track.id().equals(trackId));
    }
}
