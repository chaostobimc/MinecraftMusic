package de.chaostobimc.mcmusic.client.playback;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.api.Track;
import de.chaostobimc.mcmusic.audio.AudioDecoder;
import de.chaostobimc.mcmusic.audio.AudioResolver;
import de.chaostobimc.mcmusic.audio.AudioSource;
import de.chaostobimc.mcmusic.audio.PcmQueue;
import de.chaostobimc.mcmusic.client.NowPlaying;
import de.chaostobimc.mcmusic.config.MusicConfig;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Decodes and plays music on this client, without any server side help.
 *
 * <p>Three things happen with the same 20 ms / 960 sample frame:</p>
 * <ol>
 *     <li>it is written to the local sound card ({@link LocalSpeaker}),</li>
 *     <li>it is stored in {@link #liveFrame}, where the Simple Voice Chat plugin picks it up
 *     and mixes it into the outgoing microphone stream - that is how everybody around you
 *     hears the song,</li>
 *     <li>its length is added to the play position.</li>
 * </ol>
 *
 * <p>Everything runs on two daemon threads: one for decoding, one for playback.</p>
 */
public final class ClientPlayer {

    private static final ClientPlayer INSTANCE = new ClientPlayer();

    private final Object lock = new Object();
    private final AtomicLong framesPlayed = new AtomicLong();

    private volatile Track track;
    private volatile PcmQueue queue;
    private volatile AudioDecoder decoder;
    private volatile AudioSource source;
    private volatile LocalSpeaker speaker;
    private volatile Thread worker;

    /** Incremented on every (re)start and stop, stale threads compare against it. */
    private volatile int generation;

    private volatile boolean paused;
    private volatile boolean loading;
    private volatile boolean endOfTrack;
    private volatile String error = "";
    private volatile String sourceName = "";
    private volatile float volume = 0.65f;
    private volatile short[] liveFrame;

    private ClientPlayer() {
    }

    public static ClientPlayer get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // control
    // ------------------------------------------------------------------

    public void play(Track newTrack) {
        startPlayback(newTrack, 0);
    }

    /** Restarts the current song a few seconds in. */
    public void seek(int seconds) {
        Track current = track;
        if (current == null || !error.isEmpty()) {
            return;
        }
        startPlayback(current, Math.max(0, seconds));
    }

    public void stop() {
        halt();
        synchronized (lock) {
            track = null;
            paused = false;
            loading = false;
            endOfTrack = false;
            error = "";
            sourceName = "";
            liveFrame = null;
        }
    }

    public void pause() {
        if (track != null) {
            paused = true;
            liveFrame = null;
        }
    }

    public void resume() {
        if (track != null && error.isEmpty()) {
            paused = false;
        }
    }

    public void toggle() {
        if (paused) {
            resume();
        } else {
            pause();
        }
    }

    public void setVolume(float newVolume) {
        volume = Math.max(0f, Math.min(1f, newVolume));
    }

    /** Takes the "song ended" flag, so the queue advances exactly once. */
    public boolean consumeEndOfTrack() {
        if (endOfTrack) {
            endOfTrack = false;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // state
    // ------------------------------------------------------------------

    public Track track() {
        return track;
    }

    public boolean hasTrack() {
        return track != null;
    }

    public boolean isPlaying() {
        return track != null && !paused && error.isEmpty() && liveFrame != null;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isLoading() {
        return loading;
    }

    public int positionSeconds() {
        return (int) (framesPlayed.get() / PcmQueue.SAMPLE_RATE);
    }

    public int durationSeconds() {
        Track current = track;
        return current == null ? 0 : current.durationSeconds();
    }

    public String error() {
        return error;
    }

    public String sourceName() {
        return sourceName;
    }

    public NowPlaying snapshot() {
        Track current = track;
        if (current == null) {
            return NowPlaying.IDLE;
        }
        return new NowPlaying(current.id(), current.title(), current.artist(), current.durationSeconds(),
                positionSeconds(), isPlaying(), loading, error, sourceName,
                MusicConfig.get().broadcastToVoicechat);
    }

    /**
     * Called by Simple Voice Chat from its microphone thread - must never block.
     *
     * @return the frame that is currently playing, or {@code null} when there is nothing to mix in
     */
    public short[] nextVoiceFrame() {
        short[] frame = liveFrame;
        if (frame == null || paused || !error.isEmpty()) {
            return null;
        }
        if (!MusicConfig.get().broadcastToVoicechat) {
            return null;
        }
        if (frame.length != PcmQueue.FRAME_SIZE) {
            return null;
        }
        return frame.clone();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void startPlayback(Track newTrack, int skipSeconds) {
        halt();
        PcmQueue fresh = new PcmQueue(192);
        synchronized (lock) {
            if (newTrack == null) {
                return;
            }
            generation++;
            int gen = generation;
            track = newTrack;
            queue = fresh;
            decoder = null;
            source = null;
            paused = false;
            loading = true;
            endOfTrack = false;
            error = "";
            sourceName = "";
            liveFrame = null;
            framesPlayed.set((long) skipSeconds * PcmQueue.SAMPLE_RATE);
            Thread thread = new Thread(() -> run(fresh, newTrack, skipSeconds, gen), "MCMusic-playback");
            thread.setDaemon(true);
            worker = thread;
            thread.start();
        }
    }

    /** Stops the running playback without flagging end of track. */
    private void halt() {
        synchronized (lock) {
            generation++;
        }
        releaseResources();
    }

    private void releaseResources() {
        liveFrame = null;
        PcmQueue current = queue;
        if (current != null) {
            current.close();
        }
        AudioDecoder currentDecoder = decoder;
        if (currentDecoder != null) {
            currentDecoder.cancel();
        }
        AudioSource currentSource = source;
        if (currentSource != null) {
            currentSource.close();
        }
        LocalSpeaker currentSpeaker = speaker;
        speaker = null;
        if (currentSpeaker != null) {
            currentSpeaker.flush();
            currentSpeaker.close();
        }
    }

    private void run(PcmQueue pcm, Track current, int skipSeconds, int gen) {
        AudioDecoder decode = null;
        AudioSource resolved = null;
        try {
            resolved = AudioResolver.resolve(current, MusicConfig.get());
            source = resolved;
            sourceName = resolved.describe();
            decode = new AudioDecoder(resolved, pcm, skipSeconds);
            decoder = decode;
            Thread decodeThread = new Thread(decode, "MCMusic-decode");
            decodeThread.setDaemon(true);
            decodeThread.start();
        } catch (Exception exception) {
            String message = describeFailure(exception);
            pcm.setError(message);
            pcm.finish();
            if (gen == generation) {
                error = message;
                loading = false;
                liveFrame = null;
            }
            MCMusic.LOGGER.warn("Could not open '{}': {}", current.title(), message);
            return;
        }

        LocalSpeaker local = new LocalSpeaker();
        boolean audible = MusicConfig.get().localPlayback && local.open();
        if (gen == generation) {
            speaker = local;
        }

        while (gen == generation) {
            short[] frame;
            if (paused) {
                frame = new short[PcmQueue.FRAME_SIZE];
            } else {
                frame = pcm.takeFrame();
                if (frame == null) {
                    break;
                }
                if (loading && (pcm.bufferedFrames() > 2 || pcm.isFinished())) {
                    loading = false;
                }
            }

            applyVolume(frame);

            if (!paused && gen == generation) {
                framesPlayed.addAndGet(PcmQueue.FRAME_SIZE);
                liveFrame = frame;
            }

            if (audible) {
                local.write(frame);
            } else {
                sleepFrame();
            }
        }

        if (gen == generation) {
            loading = false;
            paused = false;
            liveFrame = null;
            String streamError = pcm.error();
            if (streamError != null && !streamError.isEmpty()) {
                error = streamError;
                endOfTrack = false;
            } else {
                endOfTrack = error.isEmpty();
            }
        }
        if (decode != null) {
            decode.cancel();
        }
        pcm.close();
        // only ever close the source of *this* run, never the one of a newer generation
        if (resolved != null) {
            resolved.close();
        }
        if (source == resolved) {
            source = null;
        }
        local.close();
        if (speaker == local) {
            speaker = null;
        }
    }

    private void applyVolume(short[] frame) {
        float gain = volume;
        if (gain >= 0.999f) {
            return;
        }
        for (int i = 0; i < frame.length; i++) {
            frame[i] = (short) (frame[i] * gain);
        }
    }

    private static void sleepFrame() {
        try {
            Thread.sleep(20L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describeFailure(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
