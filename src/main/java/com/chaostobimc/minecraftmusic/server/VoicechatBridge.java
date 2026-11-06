package com.chaostobimc.minecraftmusic.server;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Haelt die Referenz auf die Simple-Voice-Chat-Server-API.
 * Wird vom Voicechat-Plugin-Entrypoint gesetzt, sobald SVC geladen ist.
 * <p>
 * Wichtig: Unsere Mod funktioniert auch ohne SVC - dann bleibt
 * {@link #isAvailable()} einfach {@code false} und es wird nur lokal
 * auf dem eigenen Client abgespielt.
 */
public final class VoicechatBridge {

    private static final VoicechatBridge INSTANCE = new VoicechatBridge();

    private volatile VoicechatServerApi serverApi;
    private final boolean loaded;

    {
        boolean present;
        try {
            present = FabricLoader.getInstance().isModLoaded("voicechat");
        } catch (Throwable t) {
            present = false;
        }
        this.loaded = present;
    }

    private VoicechatBridge() {
    }

    public static VoicechatBridge get() {
        return INSTANCE;
    }

    public void setServerApi(VoicechatServerApi api) {
        this.serverApi = api;
        if (api != null) {
            MinecraftMusic.LOGGER.info("Simple Voice Chat erkannt - Musik kann gebroadcastet werden");
            SvcStreamHandler.registerCategory(api);
        }
    }

    public VoicechatServerApi getServerApi() {
        return serverApi;
    }

    /** Ist die Mod geladen? (Nicht: ist der Server bereit.) */
    public boolean isLoaded() {
        return loaded;
    }

    public boolean isAvailable() {
        return serverApi != null;
    }
}
