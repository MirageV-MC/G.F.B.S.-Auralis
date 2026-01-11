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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryStack;
import org.mirage.gfbs_auralis.api.AuralisSoundEvent;
import org.mirage.gfbs_auralis.api.AuralisSoundInstance;
import org.mirage.gfbs_auralis.api.AuralisSoundListener;

import java.nio.IntBuffer;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

final class AuralisSoundInstanceImpl implements AuralisSoundInstance {
    private final AuralisAL al;

    private final int alBuffer;
    private final SoundBufferCache bufferCache;
    private final OpenALSourcePool sourcePool;

    private volatile float volume = 1.0f;
    private volatile float pitch = 1.0f;
    private volatile float speed = 1.0f;

    private volatile boolean isStatic = false;
    private volatile boolean looping = false;

    private volatile Vec3 position = Vec3.ZERO;

    private volatile float minDistance = 1.0f;
    private volatile float maxDistance = 48.0f;

    private volatile @Nullable OpenALSourcePool.SourceHandle source;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile int priority = 50;
    private final Set<AuralisSoundListener> listeners = new CopyOnWriteArraySet<>();

    AuralisSoundInstanceImpl(AuralisAL al, int alBuffer, SoundBufferCache bufferCache, OpenALSourcePool sourcePool) {
        this.al = Objects.requireNonNull(al, "al");
        this.alBuffer = alBuffer;
        this.bufferCache = Objects.requireNonNull(bufferCache, "bufferCache");
        this.sourcePool = Objects.requireNonNull(sourcePool, "sourcePool");
    }

    @Override
    public boolean isBound() {
        return source != null;
    }

    void bind() {
        if (source != null) return;
        if (alBuffer == -1) return;

        OpenALSourcePool.SourceHandle h = sourcePool.acquire();
        this.source = h;
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

                AL11.alSourcei(sourceId, AL11.AL_BUFFER, alBuffer);
                applyAllParams(sourceId);
                AL11.alSourcei(sourceId, AL11.AL_LOOPING, looping ? AL11.AL_TRUE : AL11.AL_FALSE);

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
            AL11.alSourceStop(sourceId);
            AL11.alSourcei(sourceId, AL11.AL_BUFFER, 0);
        });

        this.source = null;
        paused.set(false);
        sourcePool.release(h);

        fireEvent(AuralisSoundEvent.UNBIND);
    }

    @Override
    public void play() {
        if (alBuffer == -1) return;
        OpenALSourcePool.SourceHandle h = requireBound();
        paused.set(false);
        final int sourceId = h.sourceId();

        al.submit(() -> {
            if (source != null && source.sourceId() == sourceId) {
                applyAllParams(sourceId);

                AL11.alSource3f(sourceId, AL11.AL_VELOCITY, 0f, 0f, 0f);

                int attached = AL11.alGetSourcei(sourceId, AL11.AL_BUFFER);
                if (attached != alBuffer) {
                    int state = AL11.alGetSourcei(sourceId, AL11.AL_SOURCE_STATE);
                    if (state == AL11.AL_PLAYING || state == AL11.AL_PAUSED) {
                        AL11.alSourceStop(sourceId);
                    }
                    AL11.alSourcei(sourceId, AL11.AL_BUFFER, 0);
                    AL11.alSourcei(sourceId, AL11.AL_BUFFER, alBuffer);
                }

                AL11.alSourcePlay(sourceId);
            }
        });

        fireEvent(AuralisSoundEvent.PLAY);
    }

    @Override
    public void pause() {
        if (alBuffer == -1) return;
        OpenALSourcePool.SourceHandle h = requireBound();
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
        if (alBuffer == -1) return;
        OpenALSourcePool.SourceHandle h = requireBound();
        paused.set(false);
        final int sourceId = h.sourceId();

        al.submit(() -> {
            if (source != null && source.sourceId() == sourceId) {
                AL11.alSourceStop(sourceId);
                AL11.alSourceRewind(sourceId);
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
        this.volume = Math.max(0.0f, volume);
        pushParamsIfBound();
        return this;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public AuralisSoundInstance setPitch(float pitch) {
        this.pitch = clamp(pitch, 0.01f, 8.0f);
        pushParamsIfBound();
        return this;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public AuralisSoundInstance setSpeed(float speed) {
        this.speed = clamp(speed, 0.01f, 8.0f);
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
        this.position = Objects.requireNonNull(pos, "pos");
        pushParamsIfBound();
        return this;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    @Override
    public AuralisSoundInstance setMinDistance(float dist) {
        this.minDistance = Math.max(0.0f, dist);
        pushParamsIfBound();
        return this;
    }

    @Override
    public float getMinDistance() {
        return minDistance;
    }

    @Override
    public AuralisSoundInstance setMaxDistance(float dist) {
        this.maxDistance = Math.max(0.0f, dist);
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
                } catch (Exception ignored) {}
            });

            paused.set(false);
            sourcePool.release(h);
            fireEvent(AuralisSoundEvent.FORCE_STOP);
        }
        bufferCache.releaseBuffer(alBuffer);
    }

    void onEvicted() {
        fireEvent(AuralisSoundEvent.EVICTED);
        unbind();
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

    void applyDistanceAttenuationOnALThread(Vec3 listenerPos) {
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return;
        final int sourceId = h.sourceId();

        if (isStatic) {
            AL11.alSourcef(sourceId, AL11.AL_GAIN, volume);
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

        AL11.alSourcef(sourceId, AL11.AL_GAIN, volume * factor);
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

    private OpenALSourcePool.SourceHandle requireBound() {
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) {
            throw new IllegalStateException("AuralisSoundInstance has no bound source. Call AuralisSoundInstance.bind(instance) first.");
        }
        return h;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
