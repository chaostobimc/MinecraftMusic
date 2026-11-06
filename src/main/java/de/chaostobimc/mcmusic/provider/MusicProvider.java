package de.chaostobimc.mcmusic.provider;

import de.chaostobimc.mcmusic.api.Track;

import java.io.IOException;
import java.util.List;

/**
 * Metadata source (search, playlists, stream urls).
 *
 * <p>Implemented for Piped and Invidious - both are free, self hostable YouTube frontends.
 * Nothing in here talks to YouTube directly and no API key is required.</p>
 */
public interface MusicProvider {

    String name();

    String baseUrl();

    List<Track> search(String query, int limit) throws IOException;

    List<Track> playlist(String playlistId, int limit) throws IOException;

    /** Direct audio url, only used by the {@code http} audio backend. */
    String streamUrl(String videoId) throws IOException;

    default List<String> suggestions(String query) throws IOException {
        return List.of();
    }
}
