package de.chaostobimc.mcmusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.chaostobimc.mcmusic.MCMusic;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Single configuration file ({@code config/mcmusic.json}).
 *
 * <p>MC Music is a client side mod: search, decoding, playback and the voice chat stream
 * all happen on your own computer. Nothing in here is ever sent to the server.</p>
 */
public final class MusicConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MusicConfig instance = new MusicConfig();

    // ---- providers (search and stream lookup, all from this client) ----
    /** "piped" or "invidious". */
    public String providerType = "piped";
    /** Base url of a Piped API instance, e.g. https://pipedapi.kavin.rocks */
    public String pipedUrl = "https://pipedapi.kavin.rocks";
    /** Base url of an Invidious instance, e.g. https://inv.nadeko.net */
    public String invidiousUrl = "https://inv.nadeko.net";
    /** Used when no provider instance is reachable. */
    public String fallbackInstance = "https://pipedapi.kavin.rocks";

    // ---- audio (local decoding) ----
    /**
     * Where the audio comes from: "auto", "ytdlp", "http" or "local".
     * <ul>
     *     <li><b>auto</b> - try a direct provider stream, then the yt-dlp pipeline, then the local library</li>
     *     <li><b>ytdlp</b> - yt-dlp + ffmpeg installed on this PC, needed for real YouTube Music playback</li>
     *     <li><b>http</b> - only play direct mp3/wav urls handed out by the provider</li>
     *     <li><b>local</b> - only play files from {@link #libraryFolder}</li>
     * </ul>
     */
    public String audioBackend = "auto";
    /** External tools are only executed if this is enabled. */
    public boolean allowExternalTools = true;
    public String ytDlpPath = "yt-dlp";
    public String ffmpegPath = "ffmpeg";
    /** Telemetry free yt-dlp arguments, keep the audio extraction simple. */
    public String ytDlpFormat = "bestaudio/best";
    /** Folder (relative to the world/game directory) with local audio files named &lt;videoId&gt;.mp3/.wav. */
    public String libraryFolder = "mcmusic-library";

    // ---- playback ----
    /** 0..1, applied before the audio is mixed into the voice chat stream. */
    public double volume = 0.65;
    /** Hard limit so a single stream cannot run forever. */
    public int maxTrackSeconds = 900;
    /**
     * Mix the music into your outgoing Simple Voice Chat stream, so every player that can
     * hear your voice also hears the music. Purely client side - the server only needs the
     * normal Simple Voice Chat mod.
     */
    public boolean broadcastToVoicechat = true;
    /** Play the music through your own speakers as well. */
    public boolean localPlayback = true;

    // ---- client ui ----
    public boolean darkTheme = true;
    public String language = "en_us";
    public boolean reduceMotion = false;

    public static MusicConfig get() {
        return instance;
    }

    public static void load() {
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                MusicConfig loaded = GSON.fromJson(reader, MusicConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception exception) {
                MCMusic.LOGGER.warn("Could not read {} - using defaults", path, exception);
            }
        }
        instance.save();
    }

    public void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            MCMusic.LOGGER.warn("Could not write {}", path, exception);
        }
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("mcmusic.json");
    }

    /** Folder with local audio files. */
    public Path libraryPath() {
        return FabricLoader.getInstance().getGameDir().resolve(libraryFolder);
    }

    public String providerUrl() {
        return "invidious".equalsIgnoreCase(providerType) ? invidiousUrl : pipedUrl;
    }

    public boolean isInvidious() {
        return "invidious".equalsIgnoreCase(providerType);
    }
}
