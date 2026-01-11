package org.mirage.gfbs_auralis;

/**
 * G.F.B.S. Mirage (mirage_gfbs) - A Minecraft Mod
 * Copyright (C) 2025-2029 Mirage-MC
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL11;
import org.mirage.gfbs_auralis.api.AuralisSoundEvent;
import org.mirage.gfbs_auralis.api.AuralisSoundInstance;
import org.mirage.gfbs_auralis.api.AuralisSoundListener;

import java.util.*;
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
    private volatile int priority = 50; // Default priority
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

        OpenALSourcePool.SourceHandle h = sourcePool.acquire();
        this.source = h;

        // Add to source-to-instance mapping
        ((OpenALSourcePool) sourcePool).sourceToInstance.put(h, this);

        al.submit(() -> {
            AL11.alSourcei(h.sourceId(), AL11.AL_BUFFER, alBuffer);
            applyAllParams(h.sourceId());
            AL11.alSourcei(h.sourceId(), AL11.AL_LOOPING, looping ? AL11.AL_TRUE : AL11.AL_FALSE);
            AL11.alSourceRewind(h.sourceId());
        });
        
        fireEvent(AuralisSoundEvent.BIND);
    }

    void unbind() {
        OpenALSourcePool.SourceHandle h = this.source;
        if (h == null) return;

        al.submit(() -> {
            AL11.alSourceStop(h.sourceId());
            AL11.alSourcei(h.sourceId(), AL11.AL_BUFFER, 0);
        });

        // Remove from source-to-instance mapping
        ((OpenALSourcePool) sourcePool).sourceToInstance.remove(h);
        
        this.source = null;
        paused.set(false);
        sourcePool.release(h);
        
        fireEvent(AuralisSoundEvent.UNBIND);
    }

    @Override
    public void play() {
        OpenALSourcePool.SourceHandle h = requireBound();
        paused.set(false);

        al.submit(() -> {
            applyAllParams(h.sourceId());
            AL11.alSourcePlay(h.sourceId());
        });
        
        fireEvent(AuralisSoundEvent.PLAY);
    }

    @Override
    public void pause() {
        OpenALSourcePool.SourceHandle h = requireBound();
        paused.set(true);

        al.submit(() -> AL11.alSourcePause(h.sourceId()));
        
        fireEvent(AuralisSoundEvent.PAUSE);
    }

    @Override
    public void stop() {
        OpenALSourcePool.SourceHandle h = requireBound();
        paused.set(false);

        al.submit(() -> {
            AL11.alSourceStop(h.sourceId());
            AL11.alSourceRewind(h.sourceId()); // 回到原点
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
            al.submit(() -> AL11.alSourcei(h.sourceId(), AL11.AL_LOOPING, looping ? AL11.AL_TRUE : AL11.AL_FALSE));
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
    
    /**
     * Fires a sound event to all registered listeners.
     * @param event The event to fire
     */
    private void fireEvent(AuralisSoundEvent event) {
        for (AuralisSoundListener listener : listeners) {
            try {
                listener.onSoundEvent(this, event);
            } catch (Exception e) {
                // Log and continue to avoid breaking other listeners
                GFBsAuralis.LOGGER.error("Error in sound listener: {}", e.getMessage(), e);
            }
        }
    }

    void forceStopAndFree() {
        if (source != null) {
            al.submit(() -> {
                OpenALSourcePool.SourceHandle h = this.source;
                if (h != null) {
                    AL11.alSourceStop(h.sourceId());
                    AL11.alSourcei(h.sourceId(), AL11.AL_BUFFER, 0);
                }
            });
            
            // Remove from source-to-instance mapping
            ((OpenALSourcePool) sourcePool).sourceToInstance.remove(this.source);
            
            this.source = null;
            paused.set(false);
            fireEvent(AuralisSoundEvent.FORCE_STOP);
        }
        bufferCache.releaseBuffer(alBuffer);
    }
    
    /**
     * Called when this sound is evicted due to low priority.
     */
    void onEvicted() {
        fireEvent(AuralisSoundEvent.EVICTED);
        unbind();
    }

    private void pushParamsIfBound() {
        OpenALSourcePool.SourceHandle h = source;
        if (h == null) return;
        al.submit(() -> applyAllParams(h.sourceId()));
    }

    private void applyAllParams(int sourceId) {
        AL11.alSourcef(sourceId, AL11.AL_GAIN, volume);

        float effectivePitch = clamp(pitch * speed, 0.01f, 8.0f);
        AL11.alSourcef(sourceId, AL11.AL_PITCH, effectivePitch);

        if (isStatic) {
            AL11.alSourcei(sourceId, AL11.AL_SOURCE_RELATIVE, AL11.AL_TRUE);
            AL11.alSource3f(sourceId, AL11.AL_POSITION, 0f, 0f, 0f);
            AL11.alSourcef(sourceId, AL11.AL_ROLLOFF_FACTOR, 0f);
        } else {
            Vec3 p = position;
            AL11.alSourcei(sourceId, AL11.AL_SOURCE_RELATIVE, AL11.AL_FALSE);
            AL11.alSource3f(sourceId, AL11.AL_POSITION, (float) p.x, (float) p.y, (float) p.z);

            AL11.alSourcef(sourceId, AL11.AL_REFERENCE_DISTANCE, minDistance);
            AL11.alSourcef(sourceId, AL11.AL_MAX_DISTANCE, maxDistance);

            AL11.alSourcef(sourceId, AL11.AL_ROLLOFF_FACTOR, 1.0f);
        }
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
