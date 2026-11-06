package de.chaostobimc.mcmusic.audio;

import java.util.ArrayDeque;

/**
 * Bounded, thread safe hand-over between the decoder thread and the voice chat audio
 * player. The player always asks for exactly {@code 960} samples (20 ms at 48 kHz mono).
 */
public final class PcmQueue {

    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_SIZE = 960;

    private final ArrayDeque<short[]> chunks = new ArrayDeque<>();
    private final int maxChunks;

    private boolean finished;
    private boolean closed;
    private String error;

    public PcmQueue(int maxChunks) {
        this.maxChunks = Math.max(4, maxChunks);
    }

    /** Blocks while the buffer is full so decoding never runs away. */
    public synchronized void put(short[] chunk) throws InterruptedException {
        while (chunks.size() >= maxChunks && !closed) {
            wait(250L);
        }
        if (closed) {
            return;
        }
        chunks.addLast(chunk);
        notifyAll();
    }

    /**
     * @return exactly {@link #FRAME_SIZE} samples, silence while buffering, or {@code null}
     *         when the song is over.
     */
    public synchronized short[] takeFrame() {
        if (chunks.isEmpty()) {
            if (finished) {
                return null;
            }
            return new short[FRAME_SIZE];
        }
        short[] frame = new short[FRAME_SIZE];
        int filled = 0;
        while (filled < FRAME_SIZE && !chunks.isEmpty()) {
            short[] chunk = chunks.peekFirst();
            int needed = FRAME_SIZE - filled;
            if (chunk.length <= needed) {
                System.arraycopy(chunk, 0, frame, filled, chunk.length);
                filled += chunk.length;
                chunks.pollFirst();
            } else {
                System.arraycopy(chunk, 0, frame, filled, needed);
                short[] rest = new short[chunk.length - needed];
                System.arraycopy(chunk, needed, rest, 0, rest.length);
                chunks.pollFirst();
                chunks.addFirst(rest);
                filled += needed;
            }
        }
        notifyAll();
        return frame;
    }

    public synchronized void clear() {
        chunks.clear();
        notifyAll();
    }

    public synchronized void finish() {
        finished = true;
        notifyAll();
    }

    public synchronized boolean isFinished() {
        return finished && chunks.isEmpty();
    }

    public synchronized void close() {
        closed = true;
        chunks.clear();
        notifyAll();
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized void setError(String message) {
        this.error = message;
        this.finished = true;
        notifyAll();
    }

    public synchronized String error() {
        return error;
    }

    /** Frames that are still buffered, used for the "buffering" indicator. */
    public synchronized int bufferedFrames() {
        int samples = 0;
        for (short[] chunk : chunks) {
            samples += chunk.length;
        }
        return samples / FRAME_SIZE;
    }
}
