package de.chaostobimc.mcmusic.server;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.api.Track;
import de.chaostobimc.mcmusic.audio.AudioResolver;
import de.chaostobimc.mcmusic.audio.AudioSource;
import de.chaostobimc.mcmusic.config.MusicConfig;
import de.chaostobimc.mcmusic.net.Payloads;
import de.chaostobimc.mcmusic.voicechat.PlaybackSession;
import de.chaostobimc.mcmusic.voicechat.VoicechatBridge;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Authoritative playback state of a server: what is playing, for how long and who is
 * listening. Every client only sends requests; all audio leaves the server through
 * Simple Voice Chat.
 */
public final class MusicServer {

    private static final int STATE_INTERVAL_TICKS = 10;
    private static final int LISTENER_INTERVAL_TICKS = 40;

    private final MinecraftServer server;

    private PlaybackSession session;
    private Track currentTrack;
    private UUID requester;
    private boolean loading;
    private String lastError = "";
    private String sourceName = "";

    private long ticks;
    private long generation;

    public MusicServer(MinecraftServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------
    // public API used by the network layer
    // ------------------------------------------------------------------

    public void handlePlay(ServerPlayer player, Track track) {
        if (!canControl(player)) {
            player.sendSystemMessage(Component.literal("Someone else is controlling the music right now."));
            return;
        }
        if (!VoicechatBridge.isAvailable()) {
            lastError = "Simple Voice Chat is not installed on this server";
            sendStateTo(player);
            return;
        }
        Track previous = currentTrack;
        stopSession();

        currentTrack = track;
        requester = player.getUUID();
        loading = true;
        lastError = "";
        sourceName = "";
        long request = ++generation;
        broadcastState();

        MusicConfig config = MusicConfig.get();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        Thread resolver = new Thread(() -> {
            AudioSource source = null;
            String error = null;
            try {
                source = AudioResolver.resolve(track, config);
            } catch (Exception exception) {
                error = exception.getMessage() == null ? exception.toString() : exception.getMessage();
            }
            AudioSource resolved = source;
            String failure = error;
            server.execute(() -> {
                if (request != generation) {
                    if (resolved != null) {
                        resolved.close();
                    }
                    return;
                }
                loading = false;
                if (resolved == null) {
                    lastError = failure == null ? "No audio source" : failure;
                    MCMusic.LOGGER.warn("Could not play {}: {}", track.title(), lastError);
                    broadcastState();
                    return;
                }
                try {
                    PlaybackSession newSession = new PlaybackSession(VoicechatBridge.serverApi(), track, resolved, config);
                    newSession.start(players);
                    session = newSession;
                    sourceName = newSession.sourceName();
                } catch (Exception exception) {
                    resolved.close();
                    lastError = exception.getMessage() == null ? exception.toString() : exception.getMessage();
                    MCMusic.LOGGER.warn("Starting playback failed", exception);
                }
                broadcastState();
            });
        }, "MCMusic-resolver");
        resolver.setDaemon(true);
        resolver.start();

        if (previous != null) {
            MCMusic.LOGGER.debug("Switched from {} to {}", previous.title(), track.title());
        }
    }

    public void handleControl(ServerPlayer player, String action, int value) {
        if ("volume".equals(action)) {
            MusicConfig config = MusicConfig.get();
            config.volume = Math.max(0, Math.min(100, value)) / 100.0;
            config.save();
            broadcastState();
            return;
        }
        if (session == null) {
            if ("stop".equals(action)) {
                currentTrack = null;
                broadcastState();
            }
            return;
        }
        if (!canControl(player)) {
            return;
        }
        switch (action) {
            case "pause" -> session.pause();
            case "resume" -> session.resume();
            case "seek" -> session.seek(value);
            case "stop" -> stopSession();
            default -> {
                return;
            }
        }
        broadcastState();
    }

    public void onDisconnect(ServerPlayer player) {
        updateListeners();
        if (requester != null && requester.equals(player.getUUID()) && session == null && !loading) {
            // nothing playing - nothing to do
            return;
        }
        broadcastState();
    }

    public void tick() {
        ticks++;
        if (session != null) {
            if (session.error() != null) {
                lastError = session.error();
                stopSession();
                broadcastState();
                return;
            }
            if (session.isFinished()) {
                stopSession();
                broadcastState();
                return;
            }
        }
        if (ticks % LISTENER_INTERVAL_TICKS == 0) {
            updateListeners();
        }
        if (ticks % STATE_INTERVAL_TICKS == 0) {
            broadcastState();
        }
    }

    public void shutdown() {
        stopSession();
        currentTrack = null;
        requester = null;
    }

    public void sendStateTo(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Payloads.PlaybackState.TYPE)) {
            ServerPlayNetworking.send(player, buildState());
        }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private boolean canControl(ServerPlayer player) {
        return MusicConfig.get().everyoneCanControl
                || requester == null
                || requester.equals(player.getUUID());
    }

    private void updateListeners() {
        if (session != null) {
            session.updateListeners(server.getPlayerList().getPlayers());
        }
    }

    private void stopSession() {
        generation++;
        if (session != null) {
            try {
                session.stop();
            } catch (Exception exception) {
                MCMusic.LOGGER.warn("Stopping playback failed", exception);
            }
            session = null;
        }
        loading = false;
        currentTrack = null;
        sourceName = "";
    }

    private Payloads.PlaybackState buildState() {
        if (session != null) {
            Track track = session.track();
            return new Payloads.PlaybackState(
                    track.id(), track.title(), track.artist(), track.durationSeconds(),
                    session.positionSeconds(), !session.isPaused(), session.isLoading(),
                    session.listenCount(), sourceName, session.error() == null ? "" : session.error());
        }
        if (loading && currentTrack != null) {
            return new Payloads.PlaybackState(currentTrack.id(), currentTrack.title(), currentTrack.artist(),
                    currentTrack.durationSeconds(), 0, true, true, 0, "", lastError);
        }
        return Payloads.PlaybackState.idle(lastError);
    }

    private void broadcastState() {
        Payloads.PlaybackState state = buildState();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, Payloads.PlaybackState.TYPE)) {
                ServerPlayNetworking.send(player, state);
            }
        }
    }
}
