package de.chaostobimc.mcmusic.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import de.chaostobimc.mcmusic.MCMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Downloads and uploads cover art.
 *
 * <p>Pictures are decoded and downscaled off-thread with ImageIO, then handed to the
 * render thread as a 96x96 texture - so scrolling never stutters and the GPU only ever
 * sees a small texture.</p>
 */
public final class CoverCache {

    private static final int SIZE = 96;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static int counter;

    private CoverCache() {
    }

    /** @return the texture for this track, or {@code null} while it is still loading. */
    public static Identifier get(String key, String url) {
        if (key == null || url == null || url.isBlank()) {
            return null;
        }
        Identifier loaded = TEXTURES.get(key);
        if (loaded != null) {
            return loaded;
        }
        if (!PENDING.contains(key) && !FAILED.contains(key)) {
            PENDING.add(key);
            startDownload(key, url);
        }
        return null;
    }

    public static void clear() {
        TEXTURES.clear();
        PENDING.clear();
        FAILED.clear();
    }

    private static void startDownload(String key, String url) {
        Thread thread = new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(12))
                        .header("User-Agent", "MCMusic/1.0 (Minecraft mod)")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode());
                }
                byte[] bytes;
                try (InputStream in = response.body()) {
                    bytes = in.readAllBytes();
                }
                byte[] png = downscale(bytes);
                Minecraft.getInstance().execute(() -> upload(key, png));
            } catch (Exception exception) {
                MCMusic.LOGGER.debug("Cover download failed for {}: {}", key, exception.toString());
                PENDING.remove(key);
                FAILED.add(key);
            }
        }, "MCMusic-cover");
        thread.setDaemon(true);
        thread.start();
    }

    private static byte[] downscale(byte[] bytes) throws Exception {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) {
            throw new IllegalStateException("Not an image");
        }
        BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, SIZE, SIZE, null);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(scaled, "png", output);
        return output.toByteArray();
    }

    private static void upload(String key, byte[] png) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            DynamicTexture texture = new DynamicTexture(() -> "mcmusic cover", image);
            Identifier id = MCMusic.id("covers/" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TEXTURES.put(key, id);
        } catch (Exception exception) {
            MCMusic.LOGGER.debug("Cover upload failed for {}", key, exception);
            FAILED.add(key);
        } finally {
            PENDING.remove(key);
        }
    }
}
