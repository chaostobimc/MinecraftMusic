package com.chaostobimc.minecraftmusic.server;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.chaostobimc.minecraftmusic.net.MusicControlPayload;
import com.chaostobimc.minecraftmusic.net.MusicFramePayload;
import com.chaostobimc.minecraftmusic.net.MusicStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Seite des Musik-Broadcasts.
 * <p>
 * Der Client liefert fertig encodete Opus-Frames (20 ms, 48 kHz, mono).
 * Der Server schreibt sie ueber die Simple-Voice-Chat-API in einen
 * locational Kanal am Standort des Spielers - damit hoeren alle
 * SVC-Nutzer in Reichweite die Musik, ganz ohne MinecraftMusic zu installieren.
 */
public class ServerMusicManager {

    /**
     * Nach dieser Zeit ohne Frame gilt ein Stream als beendet.
     * Grosszuegig gewaehlt, damit kurze Pausen oder Netzwerk-Hickser
     * den Kanal nicht schliessen (der Client meldet sich beim Fortsetzen neu an).
     */
    private static final long STREAM_TIMEOUT_MS = 10_000L;

    private static final ServerMusicManager INSTANCE = new ServerMusicManager();

    private final Map<UUID, ClientMusicStream> streams = new ConcurrentHashMap<>();

    private ServerMusicManager() {
    }

    public static ServerMusicManager get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    public static void init() {
        MinecraftMusic.LOGGER.debug("Server-Musikmanager bereit");
    }

    public void handleJoin(ServerPlayer player) {
        // Beim Join mitteilen, ob Broadcasting ueberhaupt moeglich ist.
        boolean svc = VoicechatBridge.get().isAvailable();
        MusicStatePayload payload = new MusicStatePayload(
                svc,
                SvcStreamHandler.hasListener(player),
                svc ? "Simple Voice Chat verbunden" : "Simple Voice Chat nicht verfuegbar");
        ServerPlayNetworking.send(player, payload);
    }

    public void handleDisconnect(ServerPlayer player) {
        stopStream(player.getUUID());
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ClientMusicStream> entry : streams.entrySet()) {
            ClientMusicStream stream = entry.getValue();
            if (stream.isTimedOut(now, STREAM_TIMEOUT_MS)) {
                MinecraftMusic.LOGGER.debug("Stream von {} abgelaufen", entry.getKey());
                stopStream(entry.getKey());
            }
        }
    }

    public void shutdownAll() {
        for (UUID id : streams.keySet()) {
            stopStream(id);
        }
    }

    // ------------------------------------------------------------------
    //  Pakete vom Client
    // ------------------------------------------------------------------

    public void handleControl(ServerPlayer player, MusicControlPayload payload) {
        switch (payload.action()) {
            case START -> startStream(player, payload);
            case STOP -> stopStream(player.getUUID());
            case UPDATE -> updateStream(player, payload);
            default -> {
            }
        }
    }

    private void startStream(ServerPlayer player, MusicControlPayload payload) {
        stopStream(player.getUUID());

        if (!VoicechatBridge.get().isAvailable()) {
            MinecraftMusic.LOGGER.warn("{} wollte Musik starten, aber Simple Voice Chat ist nicht geladen",
                    player.getScoreboardName());
            return;
        }
        if (!player.isAlive()) {
            return;
        }

        ClientMusicStream stream = new ClientMusicStream(player.getUUID(), UUID.randomUUID());
        stream.setTrack(payload.title(), payload.artist());

        boolean created = SvcStreamHandler.open(stream, player, payload.distance());
        if (!created) {
            MinecraftMusic.LOGGER.warn("SVC-Kanal konnte für {} nicht erstellt werden",
                    player.getScoreboardName());
            return;
        }

        streams.put(player.getUUID(), stream);
        MinecraftMusic.LOGGER.info("Musik-Stream gestartet: {} -> '{}'", player.getScoreboardName(), payload.title());
    }

    private void updateStream(ServerPlayer player, MusicControlPayload payload) {
        ClientMusicStream stream = streams.get(player.getUUID());
        if (stream == null) {
            return;
        }
        SvcStreamHandler.update(stream, player, payload.distance(), payload.volume());
    }

    public void handleFrame(ServerPlayer player, MusicFramePayload payload) {
        ClientMusicStream stream = streams.get(player.getUUID());
        if (stream == null || !stream.isActive()) {
            return;
        }
        if (payload.opus().length == 0) {
            return;
        }

        stream.touch();

        // Kanalposition regelmaessig nachziehen (Spieler bewegt sich).
        if (stream.getFrames() % 25 == 0) {
            SvcStreamHandler.moveTo(stream, player);
        }

        SvcStreamHandler.send(stream, payload.opus());
    }

    public void stopStream(UUID playerId) {
        ClientMusicStream stream = streams.remove(playerId);
        if (stream != null) {
            stream.setActive(false);
            SvcStreamHandler.close(stream);
        }
    }

    public boolean hasStream(UUID playerId) {
        return streams.containsKey(playerId);
    }

    public Vec3 getStreamPosition(UUID playerId) {
        ClientMusicStream stream = streams.get(playerId);
        if (stream == null || stream.getChannel() == null) {
            return null;
        }
        return new Vec3(stream.getChannel().getLocation().getX(),
                stream.getChannel().getLocation().getY(),
                stream.getChannel().getLocation().getZ());
    }
}
