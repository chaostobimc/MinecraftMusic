package de.chaostobimc.mcmusic.client;

/**
 * Snapshot of what this client is currently playing. The UI renders this record, so the
 * music screen does not have to touch the audio threads.
 *
 * @param trackId         id of the song, empty when nothing is playing
 * @param title           title shown in the player bar
 * @param artist          artist shown in the player bar
 * @param durationSeconds length of the song, 0 if unknown
 * @param positionSeconds play position
 * @param playing         true while audio is running
 * @param loading         true while the stream is being buffered
 * @param error           error message, empty if everything is fine
 * @param source          human readable audio source ("yt-dlp", "direct stream", ...)
 * @param broadcast       true when the music is mixed into the voice chat stream
 */
public record NowPlaying(String trackId, String title, String artist, int durationSeconds,
                         int positionSeconds, boolean playing, boolean loading, String error,
                         String source, boolean broadcast) {

    public static final NowPlaying IDLE =
            new NowPlaying("", "", "", 0, 0, false, false, "", "", false);

    public boolean hasTrack() {
        return !trackId.isEmpty();
    }
}
