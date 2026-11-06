package com.chaostobimc.minecraftmusic.net;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Steuerpaket: Start / Stop / Lautstaerke / Position.
 */
public record MusicControlPayload(Action action, float volume, float distance, String title, String artist) implements CustomPacketPayload {

    /** Maximale Laenge fuer Titel/Kuenstler im Paket (writeUtf-Limit). */
    public static final int MAX_TEXT = 128;

    public enum Action {
        START,
        STOP,
        UPDATE
    }

    public static final Type<MusicControlPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MinecraftMusic.MOD_ID, "control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MusicControlPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action());
                buf.writeFloat(payload.volume());
                buf.writeFloat(payload.distance());
                buf.writeUtf(payload.title() == null ? "" : payload.title(), MAX_TEXT);
                buf.writeUtf(payload.artist() == null ? "" : payload.artist(), MAX_TEXT);
            },
            buf -> new MusicControlPayload(
                    buf.readEnum(Action.class),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readUtf(MAX_TEXT),
                    buf.readUtf(MAX_TEXT))
    );

    public static MusicControlPayload start(float volume, float distance, String title, String artist) {
        return new MusicControlPayload(Action.START, volume, distance, truncate(title), truncate(artist));
    }

    /** YouTube-Titel koennen laenger als das Netzpaket-Limit sein. */
    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT - 1) + "…";
    }

    public static MusicControlPayload stop() {
        return new MusicControlPayload(Action.STOP, 0F, 0F, "", "");
    }

    public static MusicControlPayload update(float volume, float distance) {
        return new MusicControlPayload(Action.UPDATE, volume, distance, "", "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
