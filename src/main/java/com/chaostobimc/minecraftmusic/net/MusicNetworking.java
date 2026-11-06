package com.chaostobimc.minecraftmusic.net;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.chaostobimc.minecraftmusic.server.ServerMusicManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registrierung der Custom-Payloads (beide Seiten).
 */
public final class MusicNetworking {

    private MusicNetworking() {
    }

    public static void initCommon() {
        PayloadTypeRegistry.serverboundPlay().register(MusicFramePayload.TYPE, MusicFramePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicControlPayload.TYPE, MusicControlPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MusicStatePayload.TYPE, MusicStatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MusicControlPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerMusicManager.get().handleControl(player, payload);
        });

        ServerPlayNetworking.registerGlobalReceiver(MusicFramePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            ServerMusicManager.get().handleFrame(player, payload);
        });

        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) ->
                ServerMusicManager.get().handleJoin(listener.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                ServerMusicManager.get().handleDisconnect(listener.getPlayer()));

        MinecraftMusic.LOGGER.debug("Musik-Netzwerk registriert");
    }
}
