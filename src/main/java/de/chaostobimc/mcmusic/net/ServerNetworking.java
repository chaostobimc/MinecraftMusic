package de.chaostobimc.mcmusic.net;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.server.MusicServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Server side packet handling.
 */
public final class ServerNetworking {

    private ServerNetworking() {
    }

    public static void init() {
        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            ServerPlayNetworking.registerReceiver(handler, Payloads.PlayRequest.TYPE, (payload, context) -> {
                MusicServer musicServer = MCMusic.server();
                if (musicServer != null) {
                    musicServer.handlePlay(context.player(), payload.track());
                }
            });
            ServerPlayNetworking.registerReceiver(handler, Payloads.ControlRequest.TYPE, (payload, context) -> {
                MusicServer musicServer = MCMusic.server();
                if (musicServer != null) {
                    musicServer.handleControl(context.player(), payload.action(), payload.value());
                }
            });
            ServerPlayNetworking.registerReceiver(handler, Payloads.Hello.TYPE, (payload, context) -> {
                MusicServer musicServer = MCMusic.server();
                if (musicServer != null) {
                    musicServer.sendStateTo(context.player());
                }
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            MusicServer musicServer = MCMusic.server();
            if (musicServer != null) {
                musicServer.onDisconnect(handler.getPlayer());
            }
        });
    }
}
