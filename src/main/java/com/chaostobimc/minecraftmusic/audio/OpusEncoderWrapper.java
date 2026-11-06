package com.chaostobimc.minecraftmusic.audio;

import org.concentus.OpusApplication;
import org.concentus.OpusEncoder;

/**
 * Duenner Wrapper um Concentus (pure Java Opus), damit wir PCM-Frames
 * in genau dem Format erzeugen, das Simple Voice Chat erwartet:
 * 48 kHz, mono, 20 ms = 960 Samples pro Frame.
 */
public final class OpusEncoderWrapper {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SAMPLES = 960; // 20 ms
    public static final int MAX_PAYLOAD = 1275;

    private final OpusEncoder encoder;
    private final byte[] buffer = new byte[MAX_PAYLOAD];

    public OpusEncoderWrapper() {
        try {
            this.encoder = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
            this.encoder.setUseInbandFEC(true);
            this.encoder.setPacketLossPercent(5);
            this.encoder.setBitrate(96000);
        } catch (Exception e) {
            throw new IllegalStateException("Opus-Encoder konnte nicht erstellt werden", e);
        }
    }

    public byte[] encode(short[] pcm) {
        try {
            int length = encoder.encode(pcm, 0, FRAME_SAMPLES, buffer, 0, buffer.length);
            if (length <= 0) {
                return new byte[0];
            }
            byte[] out = new byte[length];
            System.arraycopy(buffer, 0, out, 0, length);
            return out;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public void reset() {
        try {
            encoder.resetState();
        } catch (Exception ignored) {
        }
    }

    public void close() {
    }
}
