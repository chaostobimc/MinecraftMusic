package de.chaostobimc.mcmusic;

import de.chaostobimc.mcmusic.config.MusicConfig;
import de.chaostobimc.mcmusic.net.Payloads;
import de.chaostobimc.mcmusic.net.ServerNetworking;
import de.chaostobimc.mcmusic.server.MusicServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MC Music - YouTube Music inside Minecraft.
 *
 * <p>The main entrypoint takes care of everything that runs on the logical server:
 * payload registration, the playback engine that streams audio into Simple Voice Chat
 * and the {@code /mcmusic} command.</p>
 */
public final class MCMusic implements ModInitializer {

    public static final String MOD_ID = "mcmusic";
    public static final Logger LOGGER = LoggerFactory.getLogger("MC Music");

    private static MusicServer server;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static MusicServer server() {
        return server;
    }

    @Override
    public void onInitialize() {
        MusicConfig.load();

        Payloads.registerCommon();
        ServerNetworking.init();

        ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer ->
                server = new MusicServer(minecraftServer));
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
            if (server != null) {
                server.shutdown();
                server = null;
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
            if (server != null) {
                server.tick();
            }
        });

        LOGGER.info("MC Music initialised.");
    }
}
