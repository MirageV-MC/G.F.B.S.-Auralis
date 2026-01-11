package org.mirage.gfbs_auralis;
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
import org.lwjgl.openal.AL11;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class OpenALSourcePool implements AutoCloseable {
    record SourceHandle(int sourceId) {}

    private final AuralisAL al;
    private final int maxSources;
    private final ArrayDeque<SourceHandle> free = new ArrayDeque<>();
    private final Set<SourceHandle> inUse = new HashSet<>();
    final Map<SourceHandle, AuralisSoundInstanceImpl> sourceToInstance = new ConcurrentHashMap<>();
    
    // Metrics
    private int poolExhaustedCount = 0;
    private int sourcesRecycledCount = 0;

    OpenALSourcePool(AuralisAL al, int maxSources) {
        this.al = Objects.requireNonNull(al, "al");
        this.maxSources = maxSources;

        al.executeBlocking(() -> {
            for (int i = 0; i < maxSources; i++) {
                int id = AL11.alGenSources();
                if (id == 0) {
                    throw new IllegalStateException("Failed to generate OpenAL source: " + AL11.alGetError());
                }
                free.addLast(new SourceHandle(id));
            }
        });
    }

    SourceHandle acquire() {
        return acquire(2000); // 默认1秒超时
    }

    /**
     * Acquires a free OpenAL source, waiting up to the specified timeout if none are available.
     * @param timeoutMillis Maximum time to wait in milliseconds
     * @return A free SourceHandle
     * @throws IllegalStateException If no sources become available within the timeout
     */
    SourceHandle acquire(long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        long remaining = timeoutMillis;
        
        while (true) {
            SourceHandle h = free.pollFirst();
            if (h != null) {
                inUse.add(h);
                return h;
            }
            
            // Try to recycle ended sources first
            tickRecycleEndedSources();
            
            // Check again after recycling
            h = free.pollFirst();
            if (h != null) {
                inUse.add(h);
                return h;
            }
            
            // Try to prioritize by evicting lowest priority non-looping sounds
            h = evictLowestPrioritySource();
            if (h != null) {
                inUse.add(h);
                return h;
            }
            
            // Wait a bit before trying again
            try {
                Thread.sleep(Math.min(50, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for free OpenAL sources.");
            }
            
            remaining = startTime + timeoutMillis - System.currentTimeMillis();
            if (remaining <= 0) {
                poolExhaustedCount++;
                throw new IllegalStateException("No free OpenAL sources (pool exhausted after waiting " + timeoutMillis + "ms).");
            }
        }
    }
    
    /**
     * Evicts the lowest priority source that is not looping.
     * @return A free SourceHandle, or null if no suitable source could be evicted
     */
    private SourceHandle evictLowestPrioritySource() {
        if (inUse.isEmpty()) {
            return null;
        }
        
        // Find the lowest priority source that is not looping
        SourceHandle lowestPrioritySource = null;
        int lowestPriority = Integer.MAX_VALUE;
        
        for (SourceHandle handle : inUse) {
            AuralisSoundInstanceImpl instance = sourceToInstance.get(handle);
            if (instance != null && !instance.isLooping()) {
                int priority = instance.getPriority();
                if (priority < lowestPriority) {
                    lowestPriority = priority;
                    lowestPrioritySource = handle;
                }
            }
        }
        
        if (lowestPrioritySource != null) {
            // Evict the lowest priority source
            AuralisSoundInstanceImpl instance = sourceToInstance.get(lowestPrioritySource);
            if (instance != null) {
                instance.onEvicted();
            }
            return lowestPrioritySource;
        }
        
        return null;
    }

    void release(SourceHandle h) {
        if (!inUse.remove(h)) return;
        free.addLast(h);
    }

    void tickRecycleEndedSources() {
        List<SourceHandle> toRelease = new ArrayList<>();
        
        // Check each in-use source
        for (SourceHandle handle : inUse) {
            try {
                // Get the current state of the source
                // Only recycle if the source is not associated with any instance
                if (sourceToInstance.get(handle) == null) {
                    int state = al.callBlocking(() -> AL11.alGetSourcei(handle.sourceId(), AL11.AL_SOURCE_STATE));
                    
                    // If the source is stopped, add it to the release list
                    if (state == AL11.AL_STOPPED) {
                        toRelease.add(handle);
                    }
                }
            } catch (Exception e) {
                // If we can't get the state, skip this source
                continue;
            }
        }
        
        // Release all stopped sources
        for (SourceHandle handle : toRelease) {
            release(handle);
        }
        
        // Update metrics
        sourcesRecycledCount += toRelease.size();
    }
    
    // Metrics access methods
    public int getMaxSources() {
        return maxSources;
    }
    
    public int getFreeSources() {
        return free.size();
    }
    
    public int getInUseSources() {
        return inUse.size();
    }
    
    public int getPoolExhaustedCount() {
        return poolExhaustedCount;
    }
    
    public int getSourcesRecycledCount() {
        return sourcesRecycledCount;
    }

    @Override
    public void close() {
        al.executeBlocking(() -> {
            for (SourceHandle h : inUse) {
                AL11.alSourceStop(h.sourceId());
                AL11.alDeleteSources(h.sourceId());
            }
            inUse.clear();

            for (SourceHandle h : free) {
                AL11.alDeleteSources(h.sourceId());
            }
            free.clear();
        });
    }
}
