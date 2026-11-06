package com.chaostobimc.minecraftmusic.config;

import com.chaostobimc.minecraftmusic.MinecraftMusic;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig config;
    private static Path path;

    private ConfigManager() {
    }

    public static void load() {
        path = FabricLoader.getInstance().getConfigDir().resolve(MinecraftMusic.MOD_ID + ".json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, ModConfig.class);
            } catch (IOException | RuntimeException e) {
                MinecraftMusic.LOGGER.error("Konfiguration konnte nicht gelesen werden - nutze Standardwerte", e);
                config = null;
            }
        }
        if (config == null) {
            config = new ModConfig();
            save();
        }
    }

    public static ModConfig get() {
        if (config == null) {
            load();
        }
        return config;
    }

    public static void save() {
        if (config == null || path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            MinecraftMusic.LOGGER.error("Konfiguration konnte nicht gespeichert werden", e);
        }
    }
}
