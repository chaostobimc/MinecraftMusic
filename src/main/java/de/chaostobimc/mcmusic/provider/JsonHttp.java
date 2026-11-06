package de.chaostobimc.mcmusic.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Tiny blocking JSON-over-HTTP helper used by the music providers. All calls happen on
 * background threads, never on the render thread.
 */
public final class JsonHttp {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private JsonHttp() {
    }

    public static String get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "MCMusic/1.0 (Minecraft mod)")
                .header("Accept", "application/json,text/plain,*/*")
                .GET()
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", exception);
        }
    }

    public static JsonElement getJson(String url) throws IOException {
        String body = get(url);
        try {
            return JsonParser.parseString(body);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid JSON from " + url, exception);
        }
    }

    public static JsonObject getObject(String url) throws IOException {
        JsonElement element = getJson(url);
        if (!element.isJsonObject()) {
            throw new IOException("Expected a JSON object from " + url);
        }
        return element.getAsJsonObject();
    }

    public static String string(JsonObject object, String key, String fallback) {
        if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
            try {
                return object.get(key).getAsString();
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static int integer(JsonObject object, String key, int fallback) {
        if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
            try {
                return object.get(key).getAsInt();
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
