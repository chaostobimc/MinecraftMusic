package com.chaostobimc.minecraftmusic.audio;

/**
 * Wandelt LavaPlayer-Ausgabe (16 bit, little endian, stereo) in mono
 * 48-kHz-Frames um, wie Simple Voice Chat sie erwartet.
 */
public final class AudioFormatBridge {

    private AudioFormatBridge() {
    }

    /**
     * LavaPlayer liefert bei {@code DISCORD_PCM_S16_LE} genau 960 Samples
     * pro Kanal (= 960 Frames), also 1920 shorts. Wir mischen auf mono.
     */
    public static boolean toMono(short[] stereoOrMono, boolean stereo, short[] target) {
        if (target.length != OpusEncoderWrapper.FRAME_SAMPLES) {
            throw new IllegalArgumentException("Zielpuffer muss 960 Samples haben");
        }
        if (!stereo) {
            int copy = Math.min(stereoOrMono.length, target.length);
            System.arraycopy(stereoOrMono, 0, target, 0, copy);
            if (copy < target.length) {
                java.util.Arrays.fill(target, copy, target.length, (short) 0);
            }
            return true;
        }
        int frames = stereoOrMono.length / 2;
        if (frames < target.length) {
            java.util.Arrays.fill(target, (short) 0);
        }
        int count = Math.min(frames, target.length);
        for (int i = 0; i < count; i++) {
            int l = stereoOrMono[i * 2];
            int r = stereoOrMono[i * 2 + 1];
            target[i] = (short) ((l + r) / 2);
        }
        return true;
    }

    public static short[] bytesToShortsLe(byte[] data) {
        int samples = data.length / 2;
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = (short) ((data[i * 2] & 0xFF) | (data[i * 2 + 1] << 8));
        }
        return out;
    }

    public static short[] bytesToShortsBe(byte[] data) {
        int samples = data.length / 2;
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = (short) (((data[i * 2] & 0xFF) << 8) | (data[i * 2 + 1] & 0xFF));
        }
        return out;
    }

    public static void applyVolume(short[] pcm, float volume) {
        if (volume >= 0.999f && volume <= 1.001f) {
            return;
        }
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, pcm[i] * volume));
        }
    }
}
