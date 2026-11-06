package de.chaostobimc.mcmusic.audio;

import de.chaostobimc.mcmusic.MCMusic;
import de.chaostobimc.mcmusic.config.MusicConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a YouTube Music song with {@code yt-dlp} and pipes the result through
 * {@code ffmpeg} to get a decodable 48 kHz mono WAV stream.
 *
 * <p>This is the only backend that can play arbitrary YouTube Music links, because
 * YouTube does not hand out mp3 or wav - the audio has to be transcoded once. Both tools
 * are optional; nothing is executed unless {@link MusicConfig#allowExternalTools} is on.</p>
 */
public final class YtDlpAudioSource implements AudioSource, AutoCloseable {

    private final String videoId;
    private final MusicConfig config;
    private final List<Process> processes = new ArrayList<>();

    public YtDlpAudioSource(String videoId, MusicConfig config) {
        this.videoId = videoId;
        this.config = config;
    }

    @Override
    public InputStream open() throws IOException {
        ProcessBuilder downloadBuilder = new ProcessBuilder(
                config.ytDlpPath,
                "--no-playlist",
                "--no-warnings",
                "--quiet",
                "-f", config.ytDlpFormat,
                "-o", "-",
                "https://music.youtube.com/watch?v=" + videoId);
        downloadBuilder.redirectErrorStream(false);
        Process download = downloadBuilder.start();
        processes.add(download);

        ProcessBuilder transcodeBuilder = new ProcessBuilder(
                config.ffmpegPath,
                "-hide_banner",
                "-loglevel", "error",
                "-i", "pipe:0",
                "-vn",
                "-ac", "1",
                "-ar", "48000",
                "-f", "wav",
                "-acodec", "pcm_s16le",
                "pipe:1");
        Process transcode = transcodeBuilder.start();
        processes.add(transcode);

        Thread pump = new Thread(() -> {
            try (InputStream in = download.getInputStream();
                 var out = transcode.getOutputStream()) {
                in.transferTo(out);
            } catch (IOException exception) {
                MCMusic.LOGGER.debug("yt-dlp -> ffmpeg pipe closed: {}", exception.getMessage());
            }
        }, "MCMusic-audio-pipe");
        pump.setDaemon(true);
        pump.start();

        return transcode.getInputStream();
    }

    @Override
    public String describe() {
        return "yt-dlp";
    }

    private static volatile long toolsCheckedAt;
    private static volatile boolean toolsFound;

    /** Cached for 30 seconds - spawning two processes on every song start is noticeable. */
    public static boolean toolsAvailable(MusicConfig config) {
        long now = System.currentTimeMillis();
        if (now - toolsCheckedAt < 30_000L) {
            return toolsFound;
        }
        boolean found = isExecutable(config.ytDlpPath, "--version") && isExecutable(config.ffmpegPath, "-version");
        toolsFound = found;
        toolsCheckedAt = now;
        return found;
    }

    private static boolean isExecutable(String command, String versionFlag) {
        try {
            Process process = new ProcessBuilder(command, versionFlag)
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().skip(64);
            process.destroy();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public void close() {
        for (Process process : processes) {
            process.destroy();
        }
        processes.clear();
    }
}
