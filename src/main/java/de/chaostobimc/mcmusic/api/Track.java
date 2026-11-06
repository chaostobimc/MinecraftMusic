package de.chaostobimc.mcmusic.api;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A song. Everything the UI needs travels with the payload, so clients do not have to
 * ask the server again for metadata.
 *
 * @param id              the YouTube video id (11 characters)
 * @param title           song title
 * @param artist          artist / channel name
 * @param durationSeconds length in seconds, {@code 0} if unknown
 * @param thumbnailUrl    url of the cover art, may be empty
 */
public record Track(String id, String title, String artist, int durationSeconds, String thumbnailUrl) {

    public static final StreamCodec<RegistryFriendlyByteBuf, Track> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Track::id,
            ByteBufCodecs.STRING_UTF8, Track::title,
            ByteBufCodecs.STRING_UTF8, Track::artist,
            ByteBufCodecs.VAR_INT, Track::durationSeconds,
            ByteBufCodecs.STRING_UTF8, Track::thumbnailUrl,
            Track::new
    );

    public Track {
        if (id == null) {
            id = "";
        }
        if (title == null || title.isBlank()) {
            title = "Unknown title";
        }
        if (artist == null || artist.isBlank()) {
            artist = "Unknown artist";
        }
        if (thumbnailUrl == null) {
            thumbnailUrl = "";
        }
        if (durationSeconds < 0) {
            durationSeconds = 0;
        }
    }

    public String watchUrl() {
        return "https://music.youtube.com/watch?v=" + id;
    }

    /** "3:42" - or "--:--" when the length is unknown. */
    public String durationText() {
        return formatTime(durationSeconds);
    }

    public static String formatTime(int seconds) {
        if (seconds <= 0) {
            return "--:--";
        }
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    /** "0:07" style playback position. */
    public static String formatPosition(int seconds) {
        int value = Math.max(0, seconds);
        return String.format("%d:%02d", value / 60, value % 60);
    }
}
