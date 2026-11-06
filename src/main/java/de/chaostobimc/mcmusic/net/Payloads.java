package de.chaostobimc.mcmusic.net;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.api.Track;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * All custom packets of the mod.
 *
 * <p>Searching happens client side (no round trip), so the wire protocol is small:
 * the client asks the server to play something and the server keeps everybody in sync
 * about what is currently playing.</p>
 */
public final class Payloads {

    private Payloads() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.serverboundPlay().register(PlayRequest.TYPE, PlayRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ControlRequest.TYPE, ControlRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(PlaybackState.TYPE, PlaybackState.CODEC);
    }

    public static void registerClientboundForClient() {
        // nothing else to do - clientbound payloads are registered in registerCommon()
    }

    // ------------------------------------------------------------------
    // client -> server
    // ------------------------------------------------------------------

    /** Ask the server to play a song. */
    public record PlayRequest(Track track) implements CustomPacketPayload {
        public static final Type<PlayRequest> TYPE = new Type<>(MCMusic.id("play"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayRequest> CODEC = StreamCodec.composite(
                Track.CODEC, PlayRequest::track,
                PlayRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** pause / resume / stop / seek (value = seconds) / next. */
    public record ControlRequest(String action, int value) implements CustomPacketPayload {
        public static final Type<ControlRequest> TYPE = new Type<>(MCMusic.id("control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ControlRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ControlRequest::action,
                ByteBufCodecs.VAR_INT, ControlRequest::value,
                ControlRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static ControlRequest of(String action) {
            return new ControlRequest(action, 0);
        }
    }

    /** Sent when the GUI opens so the server can answer with the current state. */
    public record Hello() implements CustomPacketPayload {
        public static final Hello INSTANCE = new Hello();
        public static final Type<Hello> TYPE = new Type<>(MCMusic.id("hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // server -> client
    // ------------------------------------------------------------------

    /** Full playback snapshot, broadcast whenever something changes and twice a second. */
    public record PlaybackState(String trackId, String title, String artist, int durationSeconds,
                                int positionSeconds, boolean playing, boolean loading, int listeners,
                                String source, String error) implements CustomPacketPayload {
        public static final Type<PlaybackState> TYPE = new Type<>(MCMusic.id("state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlaybackState> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PlaybackState::trackId,
                ByteBufCodecs.STRING_UTF8, PlaybackState::title,
                ByteBufCodecs.STRING_UTF8, PlaybackState::artist,
                ByteBufCodecs.VAR_INT, PlaybackState::durationSeconds,
                ByteBufCodecs.VAR_INT, PlaybackState::positionSeconds,
                ByteBufCodecs.BOOL, PlaybackState::playing,
                ByteBufCodecs.BOOL, PlaybackState::loading,
                ByteBufCodecs.VAR_INT, PlaybackState::listeners,
                ByteBufCodecs.STRING_UTF8, PlaybackState::source,
                ByteBufCodecs.STRING_UTF8, PlaybackState::error,
                PlaybackState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static PlaybackState idle(String error) {
            return new PlaybackState("", "", "", 0, 0, false, false, 0, "", error);
        }

        public boolean hasTrack() {
            return !trackId.isEmpty();
        }
    }
}
