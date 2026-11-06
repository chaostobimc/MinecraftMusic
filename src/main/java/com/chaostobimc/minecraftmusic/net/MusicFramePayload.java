package com.chaostobimc.minecraftmusic.net;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Ein einzelner Opus-Frame (20 ms) vom Client zum Server.
 * Der Server schreibt ihn in einen Simple-Voice-Chat-Audiokanal.
 */
public record MusicFramePayload(int streamId, int sequence, byte[] opus) implements CustomPacketPayload {

    public static final int MAX_OPUS_BYTES = 1275;

    public static final Type<MusicFramePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MinecraftMusic.MOD_ID, "frame"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MusicFramePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                int stream = payload.streamId();
                buf.writeVarInt(stream & 0xFFFF);
                buf.writeVarInt(payload.sequence());
                buf.writeVarInt(payload.opus().length);
                buf.writeBytes(payload.opus());
            },
            buf -> new MusicFramePayload(
                    buf.readVarInt() & 0xFFFF,
                    buf.readVarInt(),
                    readOpus(buf))
    );

    private static byte[] readOpus(RegistryFriendlyByteBuf buf) {
        int length = buf.readVarInt();
        if (length < 0 || length > MAX_OPUS_BYTES) {
            throw new IllegalArgumentException("Ungueltige Opus-Framegroesse: " + length);
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        return data;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
