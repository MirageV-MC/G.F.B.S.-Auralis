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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryStack;
import org.mirage.gfbs.auralis.api.AuralisSoundEvent;
import org.mirage.gfbs.auralis.api.AuralisSoundInstance;
import org.mirage.gfbs.auralis.api.AuralisSoundListener;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class AuralisSoundInstanceImpl implements AuralisSoundInstance {
    private final AuralisAL al;

    private final int alBuffer;
    private final List<Integer> alStreamedBuffers;
    private final SoundBufferCache bufferCache;
    private final OpenALSourcePool sourcePool;

    private volatile float volume = 1.0f;
    private volatile float smoothedVolume = 1.0f;
    private volatile float pitch = 1.0f;
    private volatile float speed = 1.0f;

    private volatile boolean isStatic = false;
    private volatile boolean looping = false;
    private volatile boolean isStreamed = false;

    private volatile Vec3 position = Vec3.ZERO;

    private volatile float minDistance = 1.0f;
    private volatile float maxDistance = 48.0f;

    private volatile @Nullable OpenALSourcePool.SourceHandle source;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile int priority = 50;
    private final Set<AuralisSoundListener> listeners = new CopyOnWriteArraySet<>();
    private final AtomicInteger bufferIndex = new AtomicInteger(0);
    private final AtomicBoolean pendingBind = new AtomicBoolean(false);
    private final AtomicBoolean pendingPlay = new AtomicBoolean(false);
    private final AtomicBoolean startedPlayback = new AtomicBoolean(false);
    private final AtomicBoolean pendingNaturalDispose = new AtomicBoolean(false);
    private final AtomicBoolean pendingEngineRemoval = new AtomicBoolean(false);

    AuralisSoundInstanceImpl(AuralisAL al, int alBuffer, SoundBufferCache bufferCache, OpenALSourcePool sourcePool) {
        this.al = Objects.requireNonNull(al, "al");
        this.alBuffer = alBuffer;
        this.alStreamedBuffers = new ArrayList<>();
        this.bufferCache = Objects.requireNonNull(bufferCache, "bufferCache");
        this.sourcePool = Objects.requireNonNull(sourcePool, "sourcePool");
        this.isStreamed = false;
    }

    AuralisSoundInstanceImpl(AuralisAL al, List<Integer> alStreamedBuffers, SoundBufferCache bufferCache, OpenALSourcePool sourcePool) {
        this.al = Objects.requireNonNull(al, "al");
        this.alBuffer = -1;
        this.alStreamedBuffers = Objects.requireNonNull(alStreamedBuffers, "alStreamedBuffers");
        this.bufferCache = Objects.requireNonNull(bufferCache, "bufferCache");
        this.sourcePool = Objects.requireNonNull(sourcePool, "sourcePool");
        this.isStreamed = true;
    }

    @Override
    public boolean isBound() {
        return source != null;
    }

    void bind() {
        if (source != null) return;
        if (!isStreamed && alBuffer == -1) return;
        if (isStreamed && alStreamedBuffers.isEmpty()) return;

        OpenALSourcePool.SourceHandle h = sourcePool.acquire();
        if (h == null) {
            pendingBind.set(true);
            return;
        }
        this.source = h;
        pendingBind.set(false);
        final int sourceId = h.sourceId();

        sourcePool.sourceToInstance.put(h, this);

        al.submit(() -> {
            if (source != null && source.sourceId() == sourceId) {
                AL11.alGetError();

                int state = AL11.alGetSourcei(sourceId, AL11.AL_SOURCE_STATE);
                if (state == AL11.AL_PLAYING || state == AL11.AL_PAUSED) {
                    AL11.alSourceStop(sourceId);
                }

                int queued = AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_QUEUED);
                if (queued > 0) {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer tmp = stack.mallocInt(queued);
                        AL11.alSourceUnqueueBuffers(sourceId, tmp);
                    } catch (Throwable ignored) {}
                }

                AL11.alSourcei(sourceId, AL11.AL_BUFFER, 0);
                AL11.alSourceRewind(sourceId);

                if (isStreamed) {
                    AL11.alSourcei(sourceId, AL11.AL_LOOPING, looping ? AL11.AL_TRUE : AL11.AL_FALSE);
                    queueInitialBuffers(sourceId);
                } else {
                    AL11.alSourcei(sourceId, AL11.AL_BUFFER, alBuffer);
                    AL11.alSourcei(sourceId, AL11.AL_LOOPING, looping ? AL11.AL_TRUE : AL11.AL_FALSE);
                }

                applyAllParams(sourceId);
                AL11.alSource3f(sourceId, AL11.AL_VELOCITY, 0f, 0f, 0f);

                AL11.alSourceRewind(sourceId);
            }
        });

        fireEvent(AuralisSoundEvent.BIND);
    }

    void unbind() {
        OpenALSourcePool.SourceHandle h = this.source;
        if (h == null) return;
        final int sourceId = h.sourceId();

        ((OpenALSourcePool) sourcePool).sourceToInstance.remove(h);

        al.executeBlocking(() -> {
            try {
                AL11.alSourceStop(sourceId);
                AL11.alSourcei(sourceId, AL11.AL_BUFFER, 0);
                
                int queued = AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_QUEUED);
                if (queued > 0) {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer tmp = stack.mallocInt(queued);
                        AL11.alSourceUnqueueBuffers(sourceId, tmp);
                    } catch (Throwable ignored) {}
                }
            } catch (Exception ignored) {}
        });

        this.source = null;
        paused.set(false);
        bufferIndex.set(0);
        sourcePool.release(h);
        pendingBind.set(false);
        pendingPlay.set(false);
        startedPlayback.set(false);

        fireEvent(AuralisSoundEvent.UNBIND);
    }

    @Override
    public void play() {
        if (!isStreamed && alBuffer == -1) return;
        if (isStreamed && alStreamedBuffers.isEmpty()) return;

        OpenALSourcePool.SourceHandle h = source;
        if (h == null) {
            pendingBind.set(true);
            pendingPlay.set(true);
            startedPlayback.set(true);
            return;
        }
        paused.set(false);
        startedPlayback.set(true);
        final int sourceId = h.sourceId();

        al.submit(() -> {
            if (source != null && source.sourceId() == sourceId) {
                applyAllParams(sourceId);
                AL11.alSource3f(sourceId, AL11.AL_VELOCITY, 0f, 0f, 0f);

                if (isStreamed) {
                    int processed = AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_PROCESSED);
                    if (processed > 0) {
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            IntBuffer tmp = stack.mallocInt(processed);
                            AL11.alSourceUnqueueBuffers(sourceId, tmp);
                        } catch (Throwable ignored) {}
                    }
                    bufferIndex.set(0);
                    queueInitialBuffers(sourceId);
                } else {
                    int attached = AL11.alGetSourcei(sourceId, AL11.AL_BUFFER);
                    if (attached != alBuffer) {
                        int state = AL11.alGetSourcei(sourceId, AL11.AL_SOURCE_STATE);
                        if (state == AL11.AL_PLAYING || state == AL11.AL_PAUSED) {
                            AL11.alSourceStop(sourceId);
                        }
                        AL11.alSourcei(sourceId, AL11.AL_BUFFER, 0);
                        AL11.alSourcei(sourceId, AL11.AL_BUFFER, alBuffer);
                    }
                }

                AL11.alSourcePlay(sourceId);
            }
        });

        fireEvent(AuralisSoundEvent.PLAY);
    }

    @Override
    public void pause() {
        if (!isStreamed && alBuffer == -1) return;
        if (isStreamed && alStreamedBuffers.isEmpty()) return;

        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return;
        paused.set(true);
        final int sourceId = h.sourceId();

        al.submit(() -> {
            if (source != null && source.sourceId() == sourceId) {
                AL11.alSourcePause(sourceId);
            }
        });

        fireEvent(AuralisSoundEvent.PAUSE);
    }

    @Override
    public void stop() {
        if (!isStreamed && alBuffer == -1) return;
        if (isStreamed && alStreamedBuffers.isEmpty()) return;

        OpenALSourcePool.SourceHandle h = source;
        if (h == null) {
            pendingBind.set(false);
            pendingPlay.set(false);
            startedPlayback.set(false);
            return;
        }
        paused.set(false);
        startedPlayback.set(false);
        final int sourceId = h.sourceId();

        al.submit(() -> {
            if (source != null && source.sourceId() == sourceId) {
                AL11.alSourceStop(sourceId);
                AL11.alSourceRewind(sourceId);
                
                if (isStreamed) {
                    int queued = AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_QUEUED);
                    if (queued > 0) {
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            IntBuffer tmp = stack.mallocInt(queued);
                            AL11.alSourceUnqueueBuffers(sourceId, tmp);
                        } catch (Throwable ignored) {}
                    }
                    bufferIndex.set(0);
                }
            }
        });

        fireEvent(AuralisSoundEvent.STOP);
    }

    @Override
    public boolean isPlaying() {
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return false;
        return al.callBlocking(() -> AL11.alGetSourcei(h.sourceId(), AL11.AL_SOURCE_STATE) == AL11.AL_PLAYING);
    }

    @Override
    public boolean isPaused() {
        return paused.get();
    }

    @Override
    public AuralisSoundInstance setVolume(float volume) {
        float v = Float.isFinite(volume) ? volume : 0.0f;
        this.volume = Math.max(0.0f, v);
        pushParamsIfBound();
        return this;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public AuralisSoundInstance setPitch(float pitch) {
        float p = Float.isFinite(pitch) ? pitch : 1.0f;
        this.pitch = clamp(p, 0.01f, 8.0f);
        pushParamsIfBound();
        return this;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public AuralisSoundInstance setSpeed(float speed) {
        float s = Float.isFinite(speed) ? speed : 1.0f;
        this.speed = clamp(s, 0.01f, 8.0f);
        pushParamsIfBound();
        return this;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public AuralisSoundInstance setStatic(boolean isStatic) {
        this.isStatic = isStatic;
        pushParamsIfBound();
        return this;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public AuralisSoundInstance setPosition(Vec3 pos) {
        Vec3 p = Objects.requireNonNull(pos, "pos");
        if (!isFinite(p)) p = Vec3.ZERO;
        this.position = p;
        pushParamsIfBound();
        return this;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    @Override
    public AuralisSoundInstance setMinDistance(float dist) {
        float d = Float.isFinite(dist) ? dist : 0.0f;
        this.minDistance = Math.max(0.0f, d);
        pushParamsIfBound();
        return this;
    }

    @Override
    public float getMinDistance() {
        return minDistance;
    }

    @Override
    public AuralisSoundInstance setMaxDistance(float dist) {
        float d = Float.isFinite(dist) ? dist : 0.0f;
        this.maxDistance = Math.max(0.0f, d);
        pushParamsIfBound();
        return this;
    }

    @Override
    public float getMaxDistance() {
        return maxDistance;
    }

    @Override
    public AuralisSoundInstance setLooping(boolean looping) {
        this.looping = looping;
        OpenALSourcePool.SourceHandle h = source;
        if (h != null) {
            final int sourceId = h.sourceId();
            al.submit(() -> {
                if (source != null && source.sourceId() == sourceId) {
                    AL11.alSourcei(sourceId, AL11.AL_LOOPING, looping ? AL11.AL_TRUE : AL11.AL_FALSE);
                }
            });
        }
        return this;
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    @Override
    public AuralisSoundInstance setPriority(int priority) {
        this.priority = clamp(priority, 0, 100);
        return this;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public AuralisSoundInstance addListener(AuralisSoundListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    @Override
    public AuralisSoundInstance removeListener(AuralisSoundListener listener) {
        listeners.remove(listener);
        return this;
    }

    private void fireEvent(AuralisSoundEvent event) {
        for (AuralisSoundListener listener : listeners) {
            try {
                listener.onSoundEvent(this, event);
            } catch (Exception e) {
                GFBsAuralis.LOGGER.error("Error in sound listener: {}", e.getMessage(), e);
            }
        }
    }

    void forceStopAndFree() {
        OpenALSourcePool.SourceHandle h = this.source;
        if (h != null) {
            this.source = null;

            ((OpenALSourcePool) sourcePool).sourceToInstance.remove(h);

            al.executeBlocking(() -> {
                try {
                    AL11.alSourceStop(h.sourceId());
                    AL11.alSourcei(h.sourceId(), AL11.AL_BUFFER, 0);
                    
                    int queued = AL11.alGetSourcei(h.sourceId(), AL11.AL_BUFFERS_QUEUED);
                    if (queued > 0) {
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            IntBuffer tmp = stack.mallocInt(queued);
                            AL11.alSourceUnqueueBuffers(h.sourceId(), tmp);
                        } catch (Throwable ignored) {}
                    }
                } catch (Exception ignored) {}
            });

            paused.set(false);
            bufferIndex.set(0);
            sourcePool.release(h);
            pendingBind.set(false);
            pendingPlay.set(false);
            startedPlayback.set(false);
            fireEvent(AuralisSoundEvent.FORCE_STOP);
            fireEvent(AuralisSoundEvent.UNBIND);
        }
        freeBuffers();
        pendingEngineRemoval.set(true);
    }

    void onEvicted() {
        fireEvent(AuralisSoundEvent.EVICTED);
        forceStopAndFree();
    }

    private void pushParamsIfBound() {
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return;
        final int sourceId = h.sourceId();
        al.submit(() -> {
            if (source != null && source.sourceId() == sourceId) {
                applyAllParams(sourceId);
                AL11.alSource3f(sourceId, AL11.AL_VELOCITY, 0f, 0f, 0f);
            }
        });
    }

    void applyVelocityZeroOnALThread() {
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return;
        final int sourceId = h.sourceId();
        AL11.alSource3f(sourceId, AL11.AL_VELOCITY, 0f, 0f, 0f);
    }

    void applyDistanceAttenuationOnALThread(Vec3 listenerPos, float attenuationExponent, float volumeSmoothing) {
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return;
        final int sourceId = h.sourceId();

        float s = clamp(volumeSmoothing, 0.0f, 1.0f);
        float sv = smoothedVolume + (volume - smoothedVolume) * s;
        smoothedVolume = sv;

        if (isStatic) {
            AL11.alSourcef(sourceId, AL11.AL_GAIN, sv);
            return;
        }

        Vec3 src = position;
        double dx = src.x - listenerPos.x;
        double dy = src.y - listenerPos.y;
        double dz = src.z - listenerPos.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float minD = Math.max(0.0f, minDistance);
        float maxD = Math.max(0.0f, maxDistance);

        float factor;
        if (maxD <= minD) {
            factor = (d <= minD) ? 1.0f : 0.0f;
        } else if (d <= minD) {
            factor = 1.0f;
        } else if (d >= maxD) {
            factor = 0.0f;
        } else {
            factor = 1.0f - (float) ((d - minD) / (maxD - minD));
        }

        float exp = Math.max(0.0001f, attenuationExponent);
        float shaped = (factor <= 0.0f) ? 0.0f : (factor >= 1.0f ? 1.0f : (float) Math.pow(factor, exp));
        AL11.alSourcef(sourceId, AL11.AL_GAIN, sv * shaped);
    }

    void updateStreamedBuffers() {
        if (!isStreamed || source == null) return;
        
        final int sourceId = source.sourceId();
        al.submit(() -> {
            if (source != null && source.sourceId() == sourceId) {
                updateStreamedBuffersOnALThread(sourceId);
            }
        });
    }

    void updateStreamedBuffersOnALThread() {
        if (!al.isOnALThread()) return;
        if (!isStreamed) return;
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return;
        updateStreamedBuffersOnALThread(h.sourceId());
    }

    private void updateStreamedBuffersOnALThread(int sourceId) {
        int processed = AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_PROCESSED);
        if (processed > 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer tmp = stack.mallocInt(processed);
                AL11.alSourceUnqueueBuffers(sourceId, tmp);
            } catch (Throwable ignored) {}
        }

        int queued = AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_QUEUED);
        int size = alStreamedBuffers.size();
        while (queued < 3 && size > 0) {
            int idx = bufferIndex.getAndIncrement();
            int bufferId;
            if (looping) {
                bufferId = alStreamedBuffers.get(idx % size);
            } else {
                if (idx >= size) break;
                bufferId = alStreamedBuffers.get(idx);
            }
            AL11.alSourceQueueBuffers(sourceId, bufferId);
            queued++;
        }

        if (!paused.get() && queued > 0) {
            int state = AL11.alGetSourcei(sourceId, AL11.AL_SOURCE_STATE);
            if (state != AL11.AL_PLAYING && state != AL11.AL_PAUSED) {
                AL11.alSourcePlay(sourceId);
            }
        }
    }

    private void applyAllParams(int sourceId) {
        AL11.alSourcef(sourceId, AL11.AL_GAIN, volume);

        float effectivePitch = clamp(pitch * speed, 0.01f, 8.0f);
        AL11.alSourcef(sourceId, AL11.AL_PITCH, effectivePitch);

        if (isStatic) {
            AL11.alSourcei(sourceId, AL11.AL_SOURCE_RELATIVE, AL11.AL_TRUE);
            AL11.alSource3f(sourceId, AL11.AL_POSITION, 0f, 0f, 0f);

            AL11.alSourcef(sourceId, AL11.AL_ROLLOFF_FACTOR, 0f);
            AL11.alSourcef(sourceId, AL11.AL_REFERENCE_DISTANCE, 1.0f);
            AL11.alSourcef(sourceId, AL11.AL_MAX_DISTANCE, 1000000.0f);
        } else {
            Vec3 p = position;
            AL11.alSourcei(sourceId, AL11.AL_SOURCE_RELATIVE, AL11.AL_FALSE);
            AL11.alSource3f(sourceId, AL11.AL_POSITION, (float) p.x, (float) p.y, (float) p.z);

            AL11.alSourcef(sourceId, AL11.AL_ROLLOFF_FACTOR, 0f);
            AL11.alSourcef(sourceId, AL11.AL_REFERENCE_DISTANCE, 1.0f);
            AL11.alSourcef(sourceId, AL11.AL_MAX_DISTANCE, 1000000.0f);
        }

        AL11.alSource3f(sourceId, AL11.AL_VELOCITY, 0f, 0f, 0f);
    }

    private void queueInitialBuffers(int sourceId) {
        if (alStreamedBuffers.isEmpty()) return;
        int size = alStreamedBuffers.size();
        for (int i = 0; i < Math.min(3, size); i++) {
            int idx = bufferIndex.getAndIncrement();
            int bufferId;
            if (looping) {
                bufferId = alStreamedBuffers.get(idx % size);
            } else {
                if (idx >= size) break;
                bufferId = alStreamedBuffers.get(idx);
            }
            AL11.alSourceQueueBuffers(sourceId, bufferId);
        }
    }

    boolean processPendingBindAndPlay() {
        if (!pendingBind.get()) return false;
        if (source != null) {
            pendingBind.set(false);
            if (pendingPlay.getAndSet(false)) {
                play();
            }
            return true;
        }
        bind();
        if (source != null) {
            pendingBind.set(false);
            if (pendingPlay.getAndSet(false)) {
                play();
            }
            return true;
        }
        return false;
    }

    boolean disposeIfNaturallyStoppedOnALThread() {
        if (!al.isOnALThread()) return false;
        if (!startedPlayback.get()) return false;
        if (paused.get()) return false;
        if (looping) return false;
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return false;
        int sourceId = h.sourceId();
        int state = AL11.alGetSourcei(sourceId, AL11.AL_SOURCE_STATE);
        if (state != AL11.AL_STOPPED) return false;

        source = null;
        sourcePool.sourceToInstance.remove(h);

        try {
            AL11.alSourceStop(sourceId);
            AL11.alSourcei(sourceId, AL11.AL_BUFFER, 0);
            int queued = AL11.alGetSourcei(sourceId, AL11.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer tmp = stack.mallocInt(queued);
                    AL11.alSourceUnqueueBuffers(sourceId, tmp);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        paused.set(false);
        bufferIndex.set(0);
        pendingBind.set(false);
        pendingPlay.set(false);
        startedPlayback.set(false);
        pendingNaturalDispose.set(true);
        sourcePool.release(h);
        return true;
    }

    boolean finalizeNaturalDisposeIfNeeded() {
        if (!pendingNaturalDispose.compareAndSet(true, false)) return false;
        fireEvent(AuralisSoundEvent.STOP);
        fireEvent(AuralisSoundEvent.UNBIND);
        freeBuffers();
        pendingEngineRemoval.set(true);
        return true;
    }

    boolean consumePendingEngineRemoval() {
        return pendingEngineRemoval.compareAndSet(true, false);
    }

    void freeBuffers() {
        if (isStreamed) {
            bufferCache.releaseStreamedBuffers(alStreamedBuffers);
        } else {
            bufferCache.releaseBuffer(alBuffer);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }


    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
