package com.chaostobimc.minecraftmusic.server;

import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;

import java.util.UUID;

/**
 * Serverseitiger Zustand eines Streams: Opus-Kanal + Metadaten.
 */
public class ClientMusicStream {

    private final UUID playerId;
    private final UUID channelId;
    private LocationalAudioChannel channel;
    private String title = "";
    private String artist = "";
    private long lastFrameAt = System.currentTimeMillis();
    private long frames;
    private boolean active = true;

    public ClientMusicStream(UUID playerId, UUID channelId) {
        this.playerId = playerId;
        this.channelId = channelId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public LocationalAudioChannel getChannel() {
        return channel;
    }

    public void setChannel(LocationalAudioChannel channel) {
        this.channel = channel;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public void setTrack(String title, String artist) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
    }

    public void touch() {
        this.lastFrameAt = System.currentTimeMillis();
        this.frames++;
    }

    public long getLastFrameAt() {
        return lastFrameAt;
    }

    public long getFrames() {
        return frames;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isTimedOut(long now, long timeoutMs) {
        return now - lastFrameAt > timeoutMs;
    }
}
