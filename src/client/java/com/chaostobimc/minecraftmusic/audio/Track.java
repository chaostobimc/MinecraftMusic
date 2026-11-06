package com.chaostobimc.minecraftmusic.audio;

import com.chaostobimc.minecraftmusic.config.ModConfig;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.Objects;

/**
 * Ein Song im UI. Haelt die LavaPlayer-Referenz zum Abspielen und die
 * Metadaten, die in Favoriten/Playlists gespeichert werden.
 */
public final class Track {

    public final String id;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final String artworkUrl;

    /** Nur zur Laufzeit gesetzt, nicht persistiert. */
    private transient AudioTrack audioTrack;

    public Track(String id, String title, String artist, String album, long durationMs, String artworkUrl) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "Unbekannter Titel" : title;
        this.artist = artist == null || artist.isBlank() ? "Unbekannter Künstler" : artist;
        this.album = album == null ? "" : album;
        this.durationMs = durationMs;
        this.artworkUrl = artworkUrl == null ? "" : artworkUrl;
    }

    public static Track from(AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        return new Track(info.identifier, info.title, info.author, "", info.length, info.artworkUrl)
                .withAudioTrack(track);
    }

    public Track withAudioTrack(AudioTrack track) {
        this.audioTrack = track;
        return this;
    }

    public AudioTrack audioTrack() {
        return audioTrack;
    }

    public boolean hasAudio() {
        return audioTrack != null;
    }

    public ModConfig.StoredTrack toStored() {
        return new ModConfig.StoredTrack(id, title, artist, album, durationMs, artworkUrl);
    }

    public static Track fromStored(ModConfig.StoredTrack stored) {
        if (stored == null) {
            return null;
        }
        return new Track(stored.id, stored.title, stored.artist, stored.album, stored.durationMs, stored.artworkUrl);
    }

    public String[] toStoredArray() {
        return new String[]{id, title, artist, album, String.valueOf(durationMs), artworkUrl};
    }

    public static Track fromStoredArray(String[] values) {
        if (values == null || values.length < 6) {
            return null;
        }
        long duration;
        try {
            duration = Long.parseLong(values[4]);
        } catch (NumberFormatException e) {
            duration = 0L;
        }
        return new Track(values[0], values[1], values[2], values[3], duration, values[5]);
    }

    public String durationText() {
        if (durationMs <= 0) {
            return "--:--";
        }
        long totalSeconds = durationMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes >= 60) {
            return String.format("%d:%02d:%02d", minutes / 60, minutes % 60, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Track other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
