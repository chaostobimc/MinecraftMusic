package com.chaostobimc.minecraftmusic;

import com.chaostobimc.minecraftmusic.audio.AudioEngine;
import com.chaostobimc.minecraftmusic.audio.ClientNetworkingHolder;
import com.chaostobimc.minecraftmusic.audio.PlaybackPump;
import com.chaostobimc.minecraftmusic.command.McMusicCommand;
import com.chaostobimc.minecraftmusic.config.ConfigManager;
import com.chaostobimc.minecraftmusic.gui.McMusicScreen;
import com.chaostobimc.minecraftmusic.net.MusicStatePayload;
import com.chaostobimc.minecraftmusic.thumbnails.ThumbnailCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Client-Einstiegspunkt: /mcmusic, Tastenbelegung, Netzwerk-Handler,
 * Vorwaermen des Audio-Stacks.
 */
public class MinecraftMusicClient implements ClientModInitializer {

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        // Async vorwaermen, damit die erste Suche nicht haengt.
        new Thread(() -> {
            try {
                AudioEngine.get();
            } catch (Throwable t) {
                MinecraftMusic.LOGGER.error("Audio-Engine konnte nicht initialisiert werden", t);
            }
        }, "MCMusic-AudioInit").start();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                McMusicCommand.register(dispatcher));

        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minecraftmusic.open",
                InputConstants.KEY_M,
                KeyMapping.Category.MISC));

        ClientPlayNetworking.registerGlobalReceiver(MusicStatePayload.TYPE, (payload, context) -> {
            ClientNetworkingHolder.setSvcAvailable(payload.svcAvailable());
            ClientNetworkingHolder.setServerSupportsMusic(true);
            MinecraftMusic.LOGGER.info("Server-Musikstatus: SVC={} ({})", payload.svcAvailable(), payload.info());
            if (!payload.svcAvailable()) {
                // Ohne Voice Chat hoert man nur lokal - Broadcast abschalten.
                PlaybackPump.get().setBroadcastEnabled(false);
            }
        });

        // Der Server meldet sich beim Join mit MusicStatePayload.
        // Bis dahin (und auf Servern ohne die Mod) bleibt der Broadcast aus.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ClientNetworkingHolder.setServerSupportsMusic(false));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientNetworkingHolder.setServerSupportsMusic(false);
            ClientNetworkingHolder.setSvcAvailable(false);
            com.chaostobimc.minecraftmusic.audio.MusicPlayer.get().stop();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new McMusicScreen());
                }
            }
            ThumbnailCache.get().tick();
        });

        MinecraftMusic.LOGGER.info("Client bereit - [M] oder /mcmusic öffnet YouTube Music");
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }
}
