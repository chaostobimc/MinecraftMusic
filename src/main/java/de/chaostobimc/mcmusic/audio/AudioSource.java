package de.chaostobimc.mcmusic.audio;

import java.io.IOException;
import java.io.InputStream;

/**
 * Something that can produce an audio byte stream (mp3, wav, ogg, ...).
 */
public interface AudioSource extends AutoCloseable {

    InputStream open() throws IOException;

    /** Human readable description, shown in the UI while buffering. */
    String describe();

    @Override
    default void close() {
    }
}
