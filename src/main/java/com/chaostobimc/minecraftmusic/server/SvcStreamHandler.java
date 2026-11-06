package com.chaostobimc.minecraftmusic.server;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Kapselt alle Aufrufe der Simple-Voice-Chat-API.
 * <p>
 * Dieser Audiokanal laeuft komplett serverseitig: der Server schickt die
 * Opus-Frames als normale SVC-Soundpakete an alle Spieler in Reichweite.
 * Deshalb brauchen Zuhoerer ausser Simple Voice Chat keine weitere Mod.
 */
public final class SvcStreamHandler {

    /** Volume-Category, damit Spieler die Musik separat regeln koennen. */
    public static final String CATEGORY_ID = "mcmusic";

    private static boolean categoryRegistered;

    private SvcStreamHandler() {
    }

    private static VoicechatServerApi api() {
        return VoicechatBridge.get().getServerApi();
    }

    public static void registerCategory(VoicechatServerApi api) {
        if (categoryRegistered || api == null) {
            return;
        }
        try {
            api.registerVolumeCategory(api.volumeCategoryBuilder()
                    .setId(CATEGORY_ID)
                    .setName("Minecraft Music")
                    .setNameTranslationKey("minecraftmusic.voicechat.category")
                    .setDescription("Musik, die ueber Minecraft Music abgespielt wird")
                    .setDescriptionTranslationKey("minecraftmusic.voicechat.category.description")
                    .build());
            categoryRegistered = true;
            MinecraftMusic.LOGGER.info("SVC-Lautstärkekategorie '{}' registriert", CATEGORY_ID);
        } catch (Throwable t) {
            MinecraftMusic.LOGGER.warn("SVC-Lautstärkekategorie konnte nicht registriert werden", t);
        }
    }

    /** Erzeugt den locational Kanal am Standort des Spielers. */
    public static boolean open(ClientMusicStream stream, ServerPlayer player, float distance) {
        VoicechatServerApi api = api();
        if (api == null) {
            return false;
        }
        try {
            ServerLevel level = api.fromServerLevel(player.level());
            Vec3 pos = player.position();
            Position position = api.createPosition(pos.x, pos.y + 1.2D, pos.z);

            LocationalAudioChannel channel = api.createLocationalAudioChannel(stream.getChannelId(), level, position);
            if (channel == null) {
                return false;
            }
            channel.setDistance(distance <= 0 ? 64F : distance);
            channel.setCategory(CATEGORY_ID);
            // Der Sender hoert die Musik lokal (eigene OpenAL-Wiedergabe).
            // Ohne diesen Filter wuerde er sie zusaetzlich ueber SVC hoeren -> doppelt.
            // Wichtig: SVC uebergibt hier seine eigene ServerPlayer-API, nicht die
            // Minecraft-Klasse - die UUID kommt deshalb aus dem API-Objekt.
            UUID senderId = player.getUUID();
            channel.setFilter(serverPlayer -> serverPlayer != null
                    && !senderId.equals(serverPlayer.getUuid()));
            stream.setChannel(channel);
            return true;
        } catch (Throwable t) {
            MinecraftMusic.LOGGER.error("SVC-Kanal konnte nicht erzeugt werden", t);
            return false;
        }
    }

    public static void send(ClientMusicStream stream, byte[] opus) {
        LocationalAudioChannel channel = stream.getChannel();
        if (channel == null) {
            return;
        }
        try {
            channel.send(opus);
        } catch (Throwable t) {
            MinecraftMusic.LOGGER.error("Opus-Frame konnte nicht gesendet werden", t);
        }
    }

    public static void moveTo(ClientMusicStream stream, ServerPlayer player) {
        LocationalAudioChannel channel = stream.getChannel();
        VoicechatServerApi api = api();
        if (channel == null || api == null) {
            return;
        }
        try {
            Vec3 pos = player.position();
            channel.updateLocation(api.createPosition(pos.x, pos.y + 1.2D, pos.z));
        } catch (Throwable t) {
            MinecraftMusic.LOGGER.debug("Kanalposition konnte nicht aktualisiert werden", t);
        }
    }

    public static void update(ClientMusicStream stream, ServerPlayer player, float distance, float volume) {
        LocationalAudioChannel channel = stream.getChannel();
        if (channel == null) {
            return;
        }
        try {
            if (distance > 0) {
                channel.setDistance(distance);
            }
            moveTo(stream, player);
        } catch (Throwable t) {
            MinecraftMusic.LOGGER.debug("Kanalupdate fehlgeschlagen", t);
        }
    }

    public static void close(ClientMusicStream stream) {
        LocationalAudioChannel channel = stream.getChannel();
        if (channel == null) {
            return;
        }
        try {
            channel.flush();
        } catch (Throwable ignored) {
        }
        stream.setChannel(null);
    }

    /** Hoert der Spieler selbst mit (SVC installiert + verbunden)? */
    public static boolean hasListener(ServerPlayer player) {
        VoicechatServerApi api = api();
        if (api == null) {
            return false;
        }
        try {
            VoicechatConnection connection = api.getConnectionOf(player.getUUID());
            return connection != null && connection.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }
}
