package com.chaostobimc.minecraftmusic.thumbnails;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Laedt Cover-Bilder im Hintergrund und registriert sie als dynamische
 * Texturen. Download + Dekodieren laufen nie im Render-Thread.
 */
public final class ThumbnailCache {

    private static final ThumbnailCache INSTANCE = new ThumbnailCache();

    private final Map<String, Identifier> ready = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Runnable> mainThreadQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "MCMusic-Thumbnails");
        thread.setDaemon(true);
        return thread;
    });

    private int counter;

    private ThumbnailCache() {
    }

    public static ThumbnailCache get() {
        return INSTANCE;
    }

    /** Muss jeden Client-Tick laufen: registriert fertige Bilder im Render-Thread. */
    public void tick() {
        Runnable task;
        int budget = 4;
        while (budget-- > 0 && (task = mainThreadQueue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                MinecraftMusic.LOGGER.debug("Thumbnail-Registrierung fehlgeschlagen", t);
            }
        }
    }

    public Identifier get(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Identifier existing = ready.get(url);
        if (existing != null) {
            return existing;
        }
        if (failed.contains(url) || inFlight.contains(url)) {
            return null;
        }
        inFlight.add(url);
        pool.submit(() -> download(url));
        return null;
    }

    private void download(String url) {
        String requestUrl = url;
        try {
            if (!requestUrl.contains("=")) {
                requestUrl = requestUrl + "=w160-h160-l90-rj";
            }
            HttpURLConnection connection = (HttpURLConnection) URI.create(requestUrl).toURL().openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("User-Agent", "MinecraftMusic/1.0");
            try (InputStream stream = connection.getInputStream()) {
                NativeImage image = NativeImage.read(stream);
                mainThreadQueue.add(() -> register(url, image));
            }
        } catch (Throwable t) {
            failed.add(url);
            MinecraftMusic.LOGGER.debug("Cover konnte nicht geladen werden: {}", url);
        } finally {
            inFlight.remove(url);
        }
    }

    private void register(String url, NativeImage image) {
        Minecraft client = Minecraft.getInstance();
        Identifier identifier = Identifier.fromNamespaceAndPath(MinecraftMusic.MOD_ID, "cover/" + (counter++));
        try {
            client.getTextureManager().register(identifier, new DynamicTexture(identifier::toString, image));
            ready.put(url, identifier);
        } catch (Throwable t) {
            image.close();
            failed.add(url);
        }
    }

    public void clear() {
        for (Identifier identifier : ready.values()) {
            try {
                Minecraft.getInstance().getTextureManager().release(identifier);
            } catch (Throwable ignored) {
            }
        }
        ready.clear();
        failed.clear();
    }
}
