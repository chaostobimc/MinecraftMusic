package de.chaostobimc.mcmusic.client.playback;

import de.chaostobimc.mcmusic.MCMusic;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays the music through the local speakers.
 *
 * <p>The line runs at the exact format Simple Voice Chat uses internally
 * (48 kHz, mono, 16 bit signed), so the same 20 ms frames can be handed to the speakers and
 * to the voice chat stream without any conversion. {@link #write} blocks until the sound card
 * has room for the frame, which is what paces the whole playback loop in real time.</p>
 */
public final class LocalSpeaker {

    private static final AudioFormat FORMAT = new AudioFormat(48_000f, 16, 1, true, false);

    private final byte[] bytes = new byte[960 * 2];
    private SourceDataLine line;

    /** @return true when a line could be opened, false when there is no audio device. */
    public boolean open() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                MCMusic.LOGGER.debug("No local audio line for 48 kHz mono, playing through voice chat only");
                return false;
            }
            SourceDataLine opened = (SourceDataLine) AudioSystem.getLine(info);
            opened.open(FORMAT, 960 * 2 * 6);
            opened.start();
            line = opened;
            return true;
        } catch (Exception exception) {
            MCMusic.LOGGER.warn("Could not open the local audio output: {}", exception.getMessage());
            line = null;
            return false;
        }
    }

    public boolean isOpen() {
        SourceDataLine current = line;
        return current != null && current.isOpen();
    }

    /** Writes one frame (960 samples). Blocks until the sound card is ready again. */
    public void write(short[] samples) {
        SourceDataLine current = line;
        if (current == null) {
            return;
        }
        int count = Math.min(samples.length, 960);
        for (int i = 0; i < count; i++) {
            short sample = samples[i];
            bytes[i * 2] = (byte) (sample & 0xFF);
            bytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        try {
            current.write(bytes, 0, count * 2);
        } catch (Exception exception) {
            MCMusic.LOGGER.debug("Local audio output failed: {}", exception.getMessage());
            close();
        }
    }

    public void flush() {
        SourceDataLine current = line;
        if (current != null) {
            try {
                current.flush();
            } catch (Exception ignored) {
                // the line is about to be closed anyway
            }
        }
    }

    public void close() {
        SourceDataLine current = line;
        line = null;
        if (current == null) {
            return;
        }
        try {
            current.stop();
            current.flush();
            current.close();
        } catch (Exception exception) {
            MCMusic.LOGGER.debug("Closing the local audio output failed: {}", exception.getMessage());
        }
    }
}
