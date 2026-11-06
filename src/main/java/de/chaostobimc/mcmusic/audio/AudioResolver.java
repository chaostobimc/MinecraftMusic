package de.chaostobimc.mcmusic.audio;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.api.Track;
import de.chaostobimc.mcmusic.config.MusicConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Decides where the audio for a song comes from.
 */
public final class AudioResolver {

    private static final List<String> EXTENSIONS = List.of("mp3", "wav", "m4a", "ogg", "flac", "aiff", "aif");

    private AudioResolver() {
    }

    public static AudioSource resolve(Track track, MusicConfig config) throws IOException {
        String backend = config.audioBackend == null ? "auto" : config.audioBackend.toLowerCase(Locale.ROOT);

        switch (backend) {
            case "local" -> {
                return local(track, config);
            }
            case "ytdlp" -> {
                requireExternal(config);
                return new YtDlpAudioSource(track.id(), config);
            }
            case "http" -> {
                return new HttpAudioSource(streamUrl(track, config));
            }
            default -> {
                // auto: local file, then the yt-dlp pipeline, then whatever url the provider hands out
                Path local = findLocal(track, config);
                if (local != null) {
                    return new FileAudioSource(local);
                }
                if (config.allowExternalTools && YtDlpAudioSource.toolsAvailable(config)) {
                    return new YtDlpAudioSource(track.id(), config);
                }
                return new HttpAudioSource(streamUrl(track, config));
            }
        }
    }

    private static void requireExternal(MusicConfig config) throws IOException {
        if (!config.allowExternalTools) {
            throw new IOException("External tools are disabled in the config (allowExternalTools)");
        }
        if (!YtDlpAudioSource.toolsAvailable(config)) {
            throw new IOException("yt-dlp/ffmpeg not found - install them or switch the audio backend");
        }
    }

    private static String streamUrl(Track track, MusicConfig config) throws IOException {
        // The server resolves stream urls itself, the client only sends the video id.
        try {
            var provider = config.isInvidious()
                    ? new de.chaostobimc.mcmusic.provider.InvidiousProvider(config.invidiousUrl)
                    : (de.chaostobimc.mcmusic.provider.MusicProvider) new de.chaostobimc.mcmusic.provider.PipedProvider(config.pipedUrl);
            return provider.streamUrl(track.id());
        } catch (IOException exception) {
            MCMusic.LOGGER.warn("Provider could not resolve {}: {}", track.id(), exception.getMessage());
            throw exception;
        }
    }

    private static AudioSource local(Track track, MusicConfig config) throws IOException {
        Path path = findLocal(track, config);
        if (path == null) {
            throw new IOException("No local file for " + track.id() + " in " + config.libraryPath());
        }
        return new FileAudioSource(path);
    }

    private static Path findLocal(Track track, MusicConfig config) {
        Path folder = config.libraryPath();
        if (!Files.isDirectory(folder)) {
            return null;
        }
        for (String extension : EXTENSIONS) {
            Path candidate = folder.resolve(track.id() + "." + extension);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
