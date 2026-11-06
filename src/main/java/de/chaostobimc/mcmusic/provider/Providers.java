package de.chaostobimc.mcmusic.provider;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.api.Track;
import de.chaostobimc.mcmusic.config.MusicConfig;

import java.util.List;

/**
 * Creates the configured provider and takes care of the instance fallback.
 */
public final class Providers {

    private Providers() {
    }

    public static MusicProvider primary() {
        MusicConfig config = MusicConfig.get();
        return config.isInvidious()
                ? new InvidiousProvider(config.invidiousUrl)
                : new PipedProvider(config.pipedUrl);
    }

    public static MusicProvider fallback() {
        MusicConfig config = MusicConfig.get();
        return config.isInvidious()
                ? new InvidiousProvider(config.fallbackInstance)
                : new PipedProvider(config.fallbackInstance);
    }

    /** Runs the query against the configured instance and, if that fails, the fallback. */
    public static List<Track> search(String query, int limit) throws Exception {
        try {
            return primary().search(query, limit);
        } catch (Exception first) {
            MCMusic.LOGGER.warn("Search failed on {}: {}", primary().baseUrl(), first.getMessage());
            MusicProvider fallback = fallback();
            if (fallback.baseUrl().equals(primary().baseUrl())) {
                throw first;
            }
            return fallback.search(query, limit);
        }
    }

    public static List<Track> resolvePlaylist(String playlistId, int limit) throws Exception {
        try {
            return primary().playlist(playlistId, limit);
        } catch (Exception first) {
            MusicProvider fallback = fallback();
            if (fallback.baseUrl().equals(primary().baseUrl())) {
                throw first;
            }
            return fallback.playlist(playlistId, limit);
        }
    }

    /** Extracts a video id from a pasted youtube / youtube music url or a bare id. */
    public static String parseTrackId(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim();
        String fromPath = PipedProvider.idFromPath(value);
        if (!fromPath.isEmpty()) {
            return fromPath;
        }
        int index = value.indexOf("youtu.be/");
        if (index >= 0) {
            String id = value.substring(index + "youtu.be/".length());
            int cut = id.indexOf('?');
            return cut >= 0 ? id.substring(0, cut) : id;
        }
        if (value.matches("[A-Za-z0-9_-]{8,16}")) {
            return value;
        }
        return "";
    }
}
