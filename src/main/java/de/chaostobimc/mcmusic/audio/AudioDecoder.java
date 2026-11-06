package de.chaostobimc.mcmusic.audio;

import de.chaostobimc.mcmusic.MCMusic;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Decodes any audio format the JVM knows about (mp3 through the bundled mp3spi, plus
 * wav/aiff) into the exact format Simple Voice Chat wants: 48 kHz, mono, signed 16 bit.
 */
public final class AudioDecoder implements Runnable {

    private static final AudioFormat TARGET =
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, PcmQueue.SAMPLE_RATE, 16, 1, 2,
                    PcmQueue.SAMPLE_RATE, false);

    private final AudioSource source;
    private final PcmQueue queue;
    private final int skipSeconds;

    private volatile boolean cancelled;

    public AudioDecoder(AudioSource source, PcmQueue queue, int skipSeconds) {
        this.source = source;
        this.queue = queue;
        this.skipSeconds = Math.max(0, skipSeconds);
    }

    public void cancel() {
        cancelled = true;
    }

    @Override
    public void run() {
        try (InputStream raw = new BufferedInputStream(source.open(), 1 << 17);
             AudioInputStream input = AudioSystem.getAudioInputStream(TARGET, AudioSystem.getAudioInputStream(raw))) {

            skip(input, (long) skipSeconds * PcmQueue.SAMPLE_RATE);

            byte[] buffer = new byte[PcmQueue.FRAME_SIZE * 2 * 8];
            while (!cancelled) {
                int read = input.read(buffer);
                if (read <= 0) {
                    break;
                }
                if (read % 2 != 0) {
                    read--;
                }
                short[] samples = new short[read / 2];
                for (int i = 0; i < samples.length; i++) {
                    int low = buffer[i * 2] & 0xFF;
                    int high = buffer[i * 2 + 1];
                    samples[i] = (short) ((high << 8) | low);
                }
                queue.put(samples);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (!cancelled) {
                MCMusic.LOGGER.warn("Audio decoding failed for {}: {}", source.describe(), exception.toString());
                queue.setError(exception.getMessage() == null ? exception.toString() : exception.getMessage());
                return;
            }
        }
        queue.finish();
    }

    private void skip(AudioInputStream input, long samples) throws java.io.IOException {
        long remaining = samples * 2L;
        byte[] scratch = new byte[8192];
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                int read = input.read(scratch, 0, (int) Math.min(scratch.length, remaining));
                if (read <= 0) {
                    break;
                }
                remaining -= read;
            } else {
                remaining -= skipped;
            }
        }
    }
}
