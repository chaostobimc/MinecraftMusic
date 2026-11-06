package de.chaostobimc.mcmusic.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plays a file from the local music library folder.
 */
public final class FileAudioSource implements AudioSource {

    private final Path path;

    public FileAudioSource(Path path) {
        this.path = path;
    }

    @Override
    public InputStream open() throws IOException {
        return Files.newInputStream(path);
    }

    public Path path() {
        return path;
    }

    @Override
    public String describe() {
        return path.getFileName().toString();
    }
}
