package com.chaostobimc.minecraftmusic;

import com.chaostobimc.minecraftmusic.config.ConfigManager;
import com.chaostobimc.minecraftmusic.net.MusicNetworking;
import com.chaostobimc.minecraftmusic.server.ServerMusicManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gemeinsamer Einstiegspunkt (Client + Server).
 *
 * Der Server-Teil ist dafuer verantwortlich, die vom Client erzeugten
 * Opus-Frames in einen Simple-Voice-Chat-Kanal zu schreiben. dadurch hoeren
 * alle Spieler mit Simple Voice Chat die Musik - ohne unsere Mod zu installieren.
 */
public class MinecraftMusic implements ModInitializer {

    public static final String MOD_ID = "minecraftmusic";
    public static final Logger LOGGER = LoggerFactory.getLogger("MinecraftMusic");

    private static MinecraftMusic instance;

    @Override
    public void onInitialize() {
        instance = this;
        ConfigManager.load();

        MusicNetworking.initCommon();
        ServerMusicManager.init();

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerMusicManager.get().shutdownAll());
        ServerTickEvents.END_SERVER_TICK.register(server -> ServerMusicManager.get().tick());

        LOGGER.info("Minecraft Music geladen - /mcmusic für das Interface");
    }

    public static MinecraftMusic getInstance() {
        return instance;
    }
}
