package de.chaostobimc.mcmusic.voicechat;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.api.Track;
import de.chaostobimc.mcmusic.audio.AudioDecoder;
import de.chaostobimc.mcmusic.audio.AudioSource;
import de.chaostobimc.mcmusic.audio.PcmQueue;
import de.chaostobimc.mcmusic.config.MusicConfig;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One song playing through Simple Voice Chat.
 *
 * <p>The decoded PCM is pushed into a {@link StaticAudioChannel} that targets every
 * connected player, so anybody with Simple Voice Chat hears the music - no extra mod
 * and no client side download required.</p>
 */
public final class PlaybackSession {

    public static final String VOLUME_CATEGORY = "mcmusic";

    private final VoicechatServerApi api;
    private final Track track;
    private final AudioSource source;
    private final MusicConfig config;

    private volatile PcmQueue queue = new PcmQueue(256);
    private final AtomicLong framesPlayed = new AtomicLong();
    private final Set<UUID> targeted = new HashSet<>();

    private StaticAudioChannel channel;
    private OpusEncoder encoder;
    private AudioPlayer player;
    private AudioDecoder decoder;
    private Thread decodeThread;

    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile int seekOffset;

    public PlaybackSession(VoicechatServerApi api, Track track, AudioSource source, MusicConfig config) {
        this.api = api;
        this.track = track;
        this.source = source;
        this.config = config;
    }

    public void start(Collection<ServerPlayer> listeners) throws Exception {
        channel = api.createStaticAudioChannel(UUID.randomUUID());
        if (channel == null) {
            throw new IllegalStateException("Simple Voice Chat refused to create an audio channel");
        }
        channel.setCategory(VOLUME_CATEGORY);
        updateListeners(listeners);

        encoder = api.createEncoder();
        player = api.createAudioPlayer(channel, encoder, this::nextFrame);
        player.setOnStopped(this::onPlayerStopped);
        player.startPlaying();

        beginDecoding(0);
    }

    private void beginDecoding(int skipSeconds) {
        // a fresh queue per decoder run so a seek never mixes old and new samples
        queue = new PcmQueue(256);
        decoder = new AudioDecoder(source, queue, skipSeconds);
        decodeThread = new Thread(decoder, "MCMusic-decode-" + track.id());
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    /** Keeps the target list of the channel in sync with the players on the server. */
    public synchronized void updateListeners(Collection<ServerPlayer> listeners) {
        if (channel == null) {
            return;
        }
        for (ServerPlayer player : listeners) {
            UUID id = player.getUUID();
            if (targeted.contains(id)) {
                continue;
            }
            VoicechatConnection connection = api.getConnectionOf(id);
            if (connection != null && connection.isInstalled() && connection.isConnected()) {
                channel.addTarget(connection);
                targeted.add(id);
            }
        }
    }

    /** Called by Simple Voice Chat every 20 ms on its own thread. */
    private short[] nextFrame() {
        if (stopped) {
            return null;
        }
        if (paused) {
            return new short[PcmQueue.FRAME_SIZE];
        }
        PcmQueue current = queue;
        short[] frame = current.takeFrame();
        if (frame == null) {
            return null;
        }
        double volume = Math.max(0.0, Math.min(1.0, config.volume));
        if (volume < 0.999) {
            for (int i = 0; i < frame.length; i++) {
                frame[i] = (short) (frame[i] * volume);
            }
        }
        framesPlayed.addAndGet(frame.length);
        return frame;
    }

    private void onPlayerStopped() {
        MCMusic.LOGGER.debug("Playback of {} finished", track.title());
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void seek(int seconds) {
        int target = Math.max(0, Math.min(config.maxTrackSeconds, seconds));
        seekOffset = target;
        queue.close();
        framesPlayed.set((long) target * PcmQueue.SAMPLE_RATE);
        beginDecoding(target);
    }

    public void stop() {
        stopped = true;
        if (decoder != null) {
            decoder.cancel();
        }
        queue.close();
        try {
            if (player != null) {
                player.stopPlaying();
            }
        } catch (Exception exception) {
            MCMusic.LOGGER.debug("Stopping audio player failed: {}", exception.getMessage());
        }
        try {
            if (channel != null) {
                channel.flush();
            }
        } catch (Exception exception) {
            MCMusic.LOGGER.debug("Flushing channel failed: {}", exception.getMessage());
        }
        try {
            if (encoder != null) {
                encoder.close();
            }
        } catch (Exception exception) {
            MCMusic.LOGGER.debug("Closing encoder failed: {}", exception.getMessage());
        }
        source.close();
    }

    public Track track() {
        return track;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isFinished() {
        return stopped || (queue.isFinished() && queue.bufferedFrames() == 0);
    }

    public boolean isLoading() {
        return !stopped && queue.bufferedFrames() <= 1 && !queue.isFinished();
    }

    public int positionSeconds() {
        return (int) (framesPlayed.get() / PcmQueue.SAMPLE_RATE);
    }

    public int listenCount() {
        return targeted.size();
    }

    public String error() {
        return queue.error();
    }

    public String sourceName() {
        return source.describe();
    }
}
