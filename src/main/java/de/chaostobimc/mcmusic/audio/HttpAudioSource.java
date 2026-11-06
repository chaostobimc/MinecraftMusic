package de.chaostobimc.mcmusic.audio;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Streams a direct audio url.
 */
public final class HttpAudioSource implements AudioSource {

    private final String url;
    private final HttpClient client;

    public HttpAudioSource(String url) {
        this.url = url;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public InputStream open() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "MCMusic/1.0 (Minecraft mod)")
                .header("Range", "bytes=0-")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode() + " while opening " + url);
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while opening " + url, exception);
        }
    }

    @Override
    public String describe() {
        return "HTTP stream";
    }
}
