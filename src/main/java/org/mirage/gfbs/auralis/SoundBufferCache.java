package org.mirage.gfbs.auralis;

/**
 * G.F.B.S.-Auralis (gfbs_auralis) - A Minecraft Mod
 * Copyright (C) 2025-2029 Mirage-MC
 * <p>
 * This program is licensed under the MIT License.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is provided to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.openal.AL11;
import org.mirage.gfbs.auralis.utils.OggVorbisDecoder;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.system.MemoryUtil;

final class SoundBufferCache {
    private record Entry(int bufferId, AtomicInteger refs) {}
    private record StreamedEntry(List<Integer> bufferIds, AtomicInteger refs) {}

    private final Minecraft mc;
    private final AuralisAL al;
    private final int streamedChunkSize;
    private final int maxStreamedBytes;
    private final Map<ResourceLocation, Entry> cache = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, StreamedEntry> streamedCache = new ConcurrentHashMap<>();
    private final Map<Integer, ResourceLocation> bufferToPath = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> bufferIsStreamed = new ConcurrentHashMap<>();

    SoundBufferCache(Minecraft mc, AuralisAL al, int streamedChunkSize, int maxStreamedBytes) {
        this.mc = Objects.requireNonNull(mc, "mc");
        this.al = Objects.requireNonNull(al, "al");
        this.streamedChunkSize = Math.max(4096, streamedChunkSize);
        this.maxStreamedBytes = Math.max(256 * 1024, maxStreamedBytes);
    }

    int acquireBuffer(ResourceLocation soundPath) {
        Objects.requireNonNull(soundPath, "soundPath");

        Entry existing = cache.get(soundPath);
        if (existing != null) {
            existing.refs.incrementAndGet();
            return existing.bufferId();
        }

        final int[] bufferIdHolder = new int[]{-1};
        try {
            bufferIdHolder[0] = al.callBlocking(() -> {
                DecodedPcm pcm = decode(soundPath);
                if (pcm == null) {
                    throw new RuntimeException("Failed to decode sound: " + soundPath);
                }
                assert pcm.sampleRate() > 0;
                assert pcm.pcmData() != null && pcm.pcmData().remaining() > 0;
                try {
                    int id = AL11.alGenBuffers();
                    if (id == 0) {
                        throw new IllegalStateException("Failed to generate OpenAL buffer: " + AL11.alGetError());
                    }
                    AL11.alBufferData(id, pcm.alFormat(), pcm.pcmData(), pcm.sampleRate());
                    int err = AL11.alGetError();
                    if (err != AL11.AL_NO_ERROR) {
                        AL11.alDeleteBuffers(id);
                        throw new IllegalStateException("Failed to upload buffer data: " + err);
                    }
                    return id;
                } finally {
                    pcm.free();
                }
            });

            int bufferId = bufferIdHolder[0];
            Entry entry = new Entry(bufferId, new AtomicInteger(1));
            Entry prev = cache.putIfAbsent(soundPath, entry);
            if (prev != null) {
                al.submit(() -> AL11.alDeleteBuffers(bufferId));
                prev.refs.incrementAndGet();
                return prev.bufferId();
            }

            bufferToPath.put(bufferId, soundPath);
            bufferIsStreamed.put(bufferId, false);

            return bufferId;
        } catch (Exception e) {
            if (bufferIdHolder[0] != -1) {
                al.submit(() -> AL11.alDeleteBuffers(bufferIdHolder[0]));
            }
            GFBsAuralis.LOGGER.error("Failed to acquire sound buffer for: {}", soundPath, e);
            return -1;
        }
    }

    List<Integer> acquireStreamedBuffers(ResourceLocation soundPath) {
        Objects.requireNonNull(soundPath, "soundPath");

        StreamedEntry existing = streamedCache.get(soundPath);
        if (existing != null) {
            existing.refs.incrementAndGet();
            return existing.bufferIds();
        }

        final List<Integer>[] bufferIdsHolder = new List[]{List.of()};
        try {
            bufferIdsHolder[0] = al.callBlocking(() -> {
                List<Integer> ids = new ArrayList<>();
                StreamedDecodedPcm streamedPcm = decodeStreamed(soundPath, streamedChunkSize, maxStreamedBytes);
                if (streamedPcm == null) {
                    throw new RuntimeException("Failed to decode streamed sound: " + soundPath);
                }
                assert streamedPcm.sampleRate() > 0;
                assert streamedPcm.pcmChunks() != null && !streamedPcm.pcmChunks().isEmpty();

                try {
                    for (ByteBuffer chunk : streamedPcm.pcmChunks()) {
                        int id = AL11.alGenBuffers();
                        if (id == 0) {
                            throw new IllegalStateException("Failed to generate OpenAL buffer: " + AL11.alGetError());
                        }
                        AL11.alBufferData(id, streamedPcm.alFormat(), chunk, streamedPcm.sampleRate());
                        int err = AL11.alGetError();
                        if (err != AL11.AL_NO_ERROR) {
                            AL11.alDeleteBuffers(id);
                            throw new IllegalStateException("Failed to upload buffer data: " + err);
                        }
                        ids.add(id);
                    }
                    return ids;
                } catch (Exception e) {
                    for (int id : ids) {
                        AL11.alDeleteBuffers(id);
                    }
                    throw e;
                } finally {
                    streamedPcm.free();
                }
            });

            List<Integer> bufferIds = bufferIdsHolder[0];
            StreamedEntry entry = new StreamedEntry(bufferIds, new AtomicInteger(1));
            StreamedEntry prev = streamedCache.putIfAbsent(soundPath, entry);
            if (prev != null) {
                al.submit(() -> {
                    for (int id : bufferIds) {
                        AL11.alDeleteBuffers(id);
                    }
                });
                prev.refs.incrementAndGet();
                return prev.bufferIds();
            }

            for (int id : bufferIds) {
                bufferToPath.put(id, soundPath);
                bufferIsStreamed.put(id, true);
            }

            return bufferIds;
        } catch (Exception e) {
            if (!bufferIdsHolder[0].isEmpty()) {
                final List<Integer> idsToDelete = bufferIdsHolder[0];
                al.submit(() -> {
                    for (int id : idsToDelete) {
                        AL11.alDeleteBuffers(id);
                    }
                });
            }
            GFBsAuralis.LOGGER.error("Failed to acquire streamed sound buffers for: {}", soundPath, e);
            return List.of();
        }
    }

    void releaseBuffer(int bufferId) {
        ResourceLocation soundPath = bufferToPath.get(bufferId);
        if (soundPath == null) {
            return;
        }

        Boolean isStreamed = bufferIsStreamed.get(bufferId);
        if (isStreamed == null) {
            return;
        }

        if (isStreamed) {
            releaseStreamedBuffer(soundPath);
        } else {
            releaseRegularBuffer(bufferId, soundPath);
        }
    }

    private void releaseRegularBuffer(int bufferId, ResourceLocation soundPath) {
        Entry entry = cache.get(soundPath);
        if (entry == null || entry.bufferId() != bufferId) {
            bufferToPath.remove(bufferId);
            bufferIsStreamed.remove(bufferId);
            return;
        }

        int left = entry.refs.decrementAndGet();
        if (left <= 0) {
            if (cache.remove(soundPath, entry)) {
                bufferToPath.remove(bufferId);
                bufferIsStreamed.remove(bufferId);
                al.submit(() -> AL11.alDeleteBuffers(bufferId));
            }
        }
    }

    void releaseStreamedBuffers(List<Integer> bufferIds) {
        if (bufferIds == null || bufferIds.isEmpty()) return;
        ResourceLocation soundPath = bufferToPath.get(bufferIds.get(0));
        if (soundPath == null) return;
        releaseStreamedBuffer(soundPath);
    }

    private void releaseStreamedBuffer(ResourceLocation soundPath) {
        StreamedEntry entry = streamedCache.get(soundPath);
        if (entry == null) return;

        int left = entry.refs.decrementAndGet();
        if (left <= 0) {
            if (streamedCache.remove(soundPath, entry)) {
                for (int id : entry.bufferIds()) {
                    bufferToPath.remove(id);
                    bufferIsStreamed.remove(id);
                }
                al.submit(() -> {
                    for (int id : entry.bufferIds()) {
                        AL11.alDeleteBuffers(id);
                    }
                });
            }
        }
    }

    void clearAll() {
        for (Entry e : cache.values()) {
            int id = e.bufferId();
            al.submit(() -> AL11.alDeleteBuffers(id));
        }
        for (StreamedEntry e : streamedCache.values()) {
            List<Integer> ids = e.bufferIds();
            al.submit(() -> {
                for (int id : ids) {
                    AL11.alDeleteBuffers(id);
                }
            });
        }
        cache.clear();
        streamedCache.clear();
        bufferToPath.clear();
        bufferIsStreamed.clear();
    }

    private DecodedPcm decode(ResourceLocation soundPath) {
        try {
            Resource r = mc.getResourceManager().getResource(soundPath).orElseThrow(
                    () -> new IllegalArgumentException("Missing sound resource: " + soundPath)
            );
            try (InputStream in = r.open()) {
                return OggVorbisDecoder.decodeFully(in);
            } catch (Exception e) {
                GFBsAuralis.LOGGER.warn("Failed to decode OGG: {}", soundPath, e);
                throw new RuntimeException("Failed to decode OGG: " + soundPath + " ;E: " + e);
            }
        } catch (IllegalArgumentException e) {
            GFBsAuralis.LOGGER.warn("Missing sound resource: {} ;E: {}", soundPath, e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode OGG: " + soundPath + " ;E: " + e);
        }
    }

    private StreamedDecodedPcm decodeStreamed(ResourceLocation soundPath, int chunkSize, int maxBytes) {
        ByteBuffer chunk = MemoryUtil.memAlloc(chunkSize);
        try {
            Resource r = mc.getResourceManager().getResource(soundPath).orElseThrow(
                    () -> new IllegalArgumentException("Missing sound resource: " + soundPath)
            );
            try (InputStream in = r.open();
                 OggVorbisDecoder.StreamDecoder decoder = OggVorbisDecoder.createStreamDecoder(in)) {
                
                StreamedDecodedPcm streamedPcm = new StreamedDecodedPcm(
                        decoder.getAlFormat(),
                        decoder.getSampleRate(),
                        chunkSize
                );

                try {
                    int total = 0;
                    while (true) {
                        chunk.clear();
                        int bytesDecoded = decoder.decodeChunk(chunk);
                        if (bytesDecoded <= 0) {
                            break;
                        }
                        total += bytesDecoded;
                        if (total > maxBytes) {
                            throw new IllegalStateException("Streamed sound exceeds limit: " + soundPath);
                        }
                        chunk.flip();
                        ByteBuffer chunkCopy = MemoryUtil.memAlloc(bytesDecoded);
                        chunkCopy.put(chunk).flip();
                        streamedPcm.addChunk(chunkCopy);
                    }
                    return streamedPcm;
                } catch (Exception e) {
                    streamedPcm.free();
                    throw e;
                }
            } catch (Exception e) {
                GFBsAuralis.LOGGER.warn("Failed to decode streamed OGG: {}", soundPath, e);
                throw new RuntimeException("Failed to decode streamed OGG: " + soundPath + " ;E: " + e);
            }
        } catch (IllegalArgumentException e) {
            GFBsAuralis.LOGGER.warn("Missing sound resource: {} ;E: {}", soundPath, e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode streamed OGG: " + soundPath + " ;E: " + e);
        } finally {
            MemoryUtil.memFree(chunk);
        }
    }
}
