package com.chaostobimc.minecraftmusic.net;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> Client: Ist auf diesem Server ein Simple-Voice-Chat-Broadcast moeglich?
 */
public record MusicStatePayload(boolean svcAvailable, boolean listening, String info) implements CustomPacketPayload {

    /** writeUtf-Limit fuer das Info-Feld. */
    public static final int MAX_INFO = 200;

    public static final Type<MusicStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MinecraftMusic.MOD_ID, "state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MusicStatePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.svcAvailable());
                buf.writeBoolean(payload.listening());
                String info = payload.info() == null ? "" : payload.info();
                if (info.length() > MAX_INFO) {
                    info = info.substring(0, MAX_INFO - 1) + "\u2026";
                }
                buf.writeUtf(info, MAX_INFO);
            },
            buf -> new MusicStatePayload(buf.readBoolean(), buf.readBoolean(), buf.readUtf(MAX_INFO))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
