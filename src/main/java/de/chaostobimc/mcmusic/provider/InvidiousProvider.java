package de.chaostobimc.mcmusic.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.chaostobimc.mcmusic.api.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to an <a href="https://github.com/iv-org/invidious">Invidious</a> instance.
 */
public final class InvidiousProvider implements MusicProvider {

    private final String base;

    public InvidiousProvider(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        this.base = trimmed;
    }

    @Override
    public String name() {
        return "Invidious";
    }

    @Override
    public String baseUrl() {
        return base;
    }

    @Override
    public List<Track> search(String query, int limit) throws IOException {
        JsonElement root = JsonHttp.getJson(base + "/api/v1/search?q=" + JsonHttp.encode(query) + "&type=video");
        if (!root.isJsonArray()) {
            throw new IOException("Unexpected search response");
        }
        List<Track> tracks = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            Track track = fromVideo(element.getAsJsonObject());
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
    public List<Track> playlist(String playlistId, int limit) throws IOException {
        JsonObject root = JsonHttp.getObject(base + "/api/v1/playlists/" + JsonHttp.encode(playlistId));
        List<Track> tracks = new ArrayList<>();
        JsonArray videos = root.has("videos") ? root.getAsJsonArray("videos") : new JsonArray();
        for (JsonElement element : videos) {
            if (!element.isJsonObject()) {
                continue;
            }
            Track track = fromVideo(element.getAsJsonObject());
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
        JsonObject root = JsonHttp.getObject(base + "/api/v1/videos/" + JsonHttp.encode(videoId));
        if (!root.has("adaptiveFormats")) {
            throw new IOException("No audio formats for " + videoId);
        }
        String best = null;
        long bestScore = -1;
        for (JsonElement element : root.getAsJsonArray("adaptiveFormats")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject format = element.getAsJsonObject();
            String mime = JsonHttp.string(format, "type", "");
            String url = JsonHttp.string(format, "url", "");
            if (url.isEmpty() || !mime.startsWith("audio")) {
                continue;
            }
            boolean playable = mime.contains("mpeg") || mime.contains("wav");
            long score = JsonHttp.integer(format, "bitrate", 0) + (playable ? 10_000_000L : 0);
            if (score > bestScore) {
                bestScore = score;
                best = url;
            }
        }
        if (best == null) {
            throw new IOException("No usable audio stream for " + videoId);
        }
        return best;
    }

    @Override
    public List<String> suggestions(String query) throws IOException {
        JsonElement root = JsonHttp.getJson(base + "/api/v1/search/suggestions?q=" + JsonHttp.encode(query));
        List<String> suggestions = new ArrayList<>();
        if (root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                suggestions.add(element.getAsString());
                if (suggestions.size() >= 8) {
                    break;
                }
            }
        }
        return suggestions;
    }

    private static Track fromVideo(JsonObject video) {
        String id = JsonHttp.string(video, "videoId", "");
        String title = JsonHttp.string(video, "title", "");
        if (id.isEmpty()) {
            return null;
        }
        String author = JsonHttp.string(video, "author", "");
        int length = JsonHttp.integer(video, "lengthSeconds", 0);
        String thumbnail = "";
        if (video.has("videoThumbnails") && video.get("videoThumbnails").isJsonArray()) {
            JsonArray thumbnails = video.getAsJsonArray("videoThumbnails");
            if (!thumbnails.isEmpty() && thumbnails.get(0).isJsonObject()) {
                thumbnail = JsonHttp.string(thumbnails.get(0).getAsJsonObject(), "url", "");
            }
        }
        if (thumbnail.isEmpty()) {
            thumbnail = "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
        }
        return new Track(id, title, author, length, thumbnail);
    }
}
