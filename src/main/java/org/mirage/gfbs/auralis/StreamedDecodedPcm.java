package org.mirage.gfbs.auralis;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class StreamedDecodedPcm {
    private final int alFormat;
    private final int sampleRate;
    private final List<ByteBuffer> pcmChunks;
    private final int chunkSize;
    private volatile boolean isClosed;

    public StreamedDecodedPcm(int alFormat, int sampleRate, int chunkSize) {
        this.alFormat = alFormat;
        this.sampleRate = sampleRate;
        this.chunkSize = chunkSize;
        this.pcmChunks = new ArrayList<>();
        this.isClosed = false;
    }

    int alFormat() { return alFormat; }
    int sampleRate() { return sampleRate; }
    int chunkSize() { return chunkSize; }
    List<ByteBuffer> pcmChunks() { return pcmChunks; }

    void addChunk(ByteBuffer chunk) {
        if (isClosed) {
            throw new IllegalStateException("StreamedDecodedPcm is closed");
        }
        pcmChunks.add(chunk);
    }

    boolean isEmpty() {
        return pcmChunks.isEmpty();
    }

    void free() {
        if (isClosed) {
            return;
        }
        for (ByteBuffer chunk : pcmChunks) {
            MemoryUtil.memFree(chunk);
        }
        pcmChunks.clear();
        isClosed = true;
    }
}