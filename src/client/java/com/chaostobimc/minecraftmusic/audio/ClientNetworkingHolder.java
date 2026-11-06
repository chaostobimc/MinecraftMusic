package com.chaostobimc.minecraftmusic.audio;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.chaostobimc.minecraftmusic.net.MusicControlPayload;
import com.chaostobimc.minecraftmusic.net.MusicFramePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Kapselt das Senden zum Server und schluckt Fehler, wenn man
 * im Singleplayer/ohne Server spielt.
 */
public final class ClientNetworkingHolder {

    // Erst wenn der Server unser State-Paket geschickt hat, wissen wir,
    // dass er die Mod geladen hat. Vorher nur lokal abspielen.
    private static volatile boolean serverSupportsMusic;
    private static volatile boolean svcAvailable;

    private ClientNetworkingHolder() {
    }

    /** Verbunden mit einem Server, der MinecraftMusic installiert hat. */
    public static boolean isServerReachable() {
        if (!serverSupportsMusic) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || !mc.getConnection().getConnection().isConnected()) {
            return false;
        }
        // Im Singleplayer laeuft der Server mit, Broadcast bleibt moeglich.
        return true;
    }

    public static void sendFrame(MusicFramePayload payload) {
        try {
            if (ClientPlayNetworking.canSend(MusicFramePayload.TYPE)) {
                ClientPlayNetworking.send(payload);
            }
        } catch (Throwable t) {
            MinecraftMusic.LOGGER.debug("Frame konnte nicht gesendet werden", t);
        }
    }

    public static void sendControl(MusicControlPayload payload) {
        try {
            if (ClientPlayNetworking.canSend(MusicControlPayload.TYPE)) {
                ClientPlayNetworking.send(payload);
            }
        } catch (Throwable t) {
            MinecraftMusic.LOGGER.debug("Steuerpaket konnte nicht gesendet werden", t);
        }
    }

    public static void setServerSupportsMusic(boolean supported) {
        serverSupportsMusic = supported;
    }

    public static void setSvcAvailable(boolean available) {
        svcAvailable = available;
    }

    public static boolean isSvcAvailable() {
        return svcAvailable;
    }
}
