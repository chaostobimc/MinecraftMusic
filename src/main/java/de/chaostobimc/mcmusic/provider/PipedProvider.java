package de.chaostobimc.mcmusic.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.chaostobimc.mcmusic.api.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to a <a href="https://github.com/TeamPiped/Piped">Piped</a> API instance.
 */
public final class PipedProvider implements MusicProvider {

    private final String base;

    public PipedProvider(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        this.base = trimmed;
    }

    @Override
    public String name() {
        return "Piped";
    }

    @Override
    public String baseUrl() {
        return base;
    }

    @Override
    public List<Track> search(String query, int limit) throws IOException {
        JsonObject root = JsonHttp.getObject(base + "/search?q=" + JsonHttp.encode(query) + "&filter=music_songs");
        List<Track> tracks = new ArrayList<>();
        JsonArray items = root.has("items") ? root.getAsJsonArray("items") : new JsonArray();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            Track track = fromStream(element.getAsJsonObject());
            if (track != null) {
                tracks.add(track);
            }
            if (tracks.size() >= limit) {
                break;
            }
        }
        if (tracks.isEmpty()) {
            // Some instances do not support the music_songs filter, fall back to everything.
            JsonObject fallback = JsonHttp.getObject(base + "/search?q=" + JsonHttp.encode(query) + "&filter=videos");
            JsonArray videos = fallback.has("items") ? fallback.getAsJsonArray("items") : new JsonArray();
            for (JsonElement element : videos) {
                if (!element.isJsonObject()) {
                    continue;
                }
                Track track = fromStream(element.getAsJsonObject());
                if (track != null) {
                    tracks.add(track);
                }
                if (tracks.size() >= limit) {
                    break;
                }
            }
        }
        return tracks;
    }

    @Override
    public List<Track> playlist(String playlistId, int limit) throws IOException {
        JsonObject root = JsonHttp.getObject(base + "/playlists/" + JsonHttp.encode(playlistId));
        List<Track> tracks = new ArrayList<>();
        JsonArray items = root.has("relatedStreams") ? root.getAsJsonArray("relatedStreams") : new JsonArray();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            Track track = fromStream(element.getAsJsonObject());
            if (track != null) {
                tracks.add(track);
            }
            if (tracks.size() >= limit) {
                break;
            }
        }
        return tracks;
    }

    @Override
    public String streamUrl(String videoId) throws IOException {
        JsonObject root = JsonHttp.getObject(base + "/streams/" + JsonHttp.encode(videoId));
        if (!root.has("audioStreams")) {
            throw new IOException("No audio streams for " + videoId);
        }
        String best = null;
        long bestBitrate = -1;
        for (JsonElement element : root.getAsJsonArray("audioStreams")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject stream = element.getAsJsonObject();
            String url = JsonHttp.string(stream, "url", "");
            long bitrate = JsonHttp.integer(stream, "bitrate", 0);
            String mime = JsonHttp.string(stream, "mimeType", "");
            // Prefer sources the java sound decoder can handle.
            if (url.isEmpty()) {
                continue;
            }
            boolean playable = mime.contains("mpeg") || mime.contains("wav") || mime.contains("mp3");
            long score = bitrate + (playable ? 10_000_000L : 0);
            if (score > bestBitrate) {
                bestBitrate = score;
                best = url;
            }
        }
        if (best == null) {
            throw new IOException("No usable audio stream for " + videoId);
        }
        return best;
    }

    private static Track fromStream(JsonObject stream) {
        String url = JsonHttp.string(stream, "url", "");
        String id = idFromPath(url);
        if (id.isEmpty()) {
            id = JsonHttp.string(stream, "id", "");
        }
        String title = JsonHttp.string(stream, "title", "");
        if (id.isEmpty() && title.isEmpty()) {
            return null;
        }
        String uploader = JsonHttp.string(stream, "uploaderName", JsonHttp.string(stream, "uploader", ""));
        int duration = JsonHttp.integer(stream, "duration", 0);
        String thumbnail = JsonHttp.string(stream, "thumbnail", "");
        if (thumbnail.isEmpty()) {
            thumbnail = "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
        }
        return new Track(id, title, uploader, duration, thumbnail);
    }

    static String idFromPath(String path) {
        if (path == null) {
            return "";
        }
        int index = path.indexOf("v=");
        if (index >= 0 && index + 2 < path.length()) {
            String id = path.substring(index + 2);
            int amp = id.indexOf('&');
            return amp >= 0 ? id.substring(0, amp) : id;
        }
        return "";
    }
}
