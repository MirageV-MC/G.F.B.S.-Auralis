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
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL11;
import org.mirage.gfbs.auralis.api.AuralisSoundInstance;
import org.mirage.gfbs.auralis.api.IAuralisEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AuralisEngine implements IAuralisEngine {
    private final Minecraft mc;
    private final AuralisAL al;

    private final OpenALSourcePool sourcePool;
    private final SoundBufferCache bufferCache;
    private final float attenuationExponent;
    private final float volumeSmoothing;

    private final ConcurrentMap<AuralisSoundInstance, AuralisSoundInstanceImpl> instances = new ConcurrentHashMap<>();

    public AuralisEngine(
            Minecraft mc,
            AuralisAL al,
            int maxSources,
            int streamedChunkSize,
            int maxStreamedBytes,
            float attenuationExponent,
            float volumeSmoothing
    ) {
        this.mc = Objects.requireNonNull(mc, "mc");
        this.al = Objects.requireNonNull(al, "al");

        this.sourcePool = new OpenALSourcePool(al, maxSources);
        this.bufferCache = new SoundBufferCache(mc, al, streamedChunkSize, maxStreamedBytes);
        this.attenuationExponent = attenuationExponent;
        this.volumeSmoothing = volumeSmoothing;
    }

    @Override
    public AuralisSoundInstance create(SoundEvent soundEvent) {
        return create(soundEvent, false);
    }

    public AuralisSoundInstance createStreamed(SoundEvent soundEvent) {
        return create(soundEvent, true);
    }

    private AuralisSoundInstance create(SoundEvent soundEvent, boolean streamed) {
        Objects.requireNonNull(soundEvent, "soundEvent");
        ResourceLocation eventId = soundEvent.getLocation();

        try {
            Sound chosen = resolveToConcreteSound(eventId);
            ResourceLocation raw = chosen.getLocation();
            String ns = raw.getNamespace();
            String path = raw.getPath();
            boolean hasSoundsPrefix = path.startsWith("sounds/");
            boolean hasOggSuffix = path.endsWith(".ogg");
            String normalizedPath;
            if (hasSoundsPrefix && hasOggSuffix) {
                normalizedPath = path;
            } else if (hasSoundsPrefix) {
                normalizedPath = path + ".ogg";
            } else if (hasOggSuffix) {
                normalizedPath = "sounds/" + path;
            } else {
                normalizedPath = "sounds/" + path + ".ogg";
            }
            ResourceLocation soundPath = new ResourceLocation(ns, normalizedPath);

            AuralisSoundInstanceImpl inst;
            if (streamed) {
                var bufferIds = bufferCache.acquireStreamedBuffers(soundPath);
                if (bufferIds.isEmpty()) {
                    int bufferId = bufferCache.acquireBuffer(soundPath);
                    if (bufferId == -1) {
                        throw new RuntimeException("Failed to acquire valid buffer for sound: " + soundPath);
                    }
                    inst = new AuralisSoundInstanceImpl(al, bufferId, bufferCache, sourcePool);
                } else {
                    inst = new AuralisSoundInstanceImpl(al, bufferIds, bufferCache, sourcePool);
                }
            } else {
                int bufferId = bufferCache.acquireBuffer(soundPath);
                if (bufferId == -1) {
                    throw new RuntimeException("Failed to acquire valid buffer for sound: " + soundPath);
                }
                inst = new AuralisSoundInstanceImpl(al, bufferId, bufferCache, sourcePool);
            }

            instances.put(inst, inst);
            return inst;
        } catch (Exception e) {
            GFBsAuralis.LOGGER.error("Failed to create sound instance for: {} ;E: {}", eventId, e.getMessage());
            return new AuralisSoundInstanceImpl(al, -1, bufferCache, sourcePool);
        }
    }

    private Sound resolveToConcreteSound(ResourceLocation soundEventId) {
        SoundManager sm = mc.getSoundManager();
        @Nullable WeighedSoundEvents events = sm.getSoundEvent(soundEventId);
        if (events == null) {
            throw new IllegalArgumentException("Unknown SoundEvent: " + soundEventId);
        }

        RandomSource rnd = RandomSource.create();
        Sound s = events.getSound(rnd);
        if (s == SoundManager.EMPTY_SOUND) {
            throw new IllegalStateException("SoundEvent resolved to EMPTY_SOUND: " + soundEventId);
        }
        return s;
    }

    @Override
    public void bind(AuralisSoundInstance instance) {
        AuralisSoundInstanceImpl impl = requireImpl(instance);
        impl.bind();
    }

    @Override
    public void unbind(AuralisSoundInstance instance) {
        AuralisSoundInstanceImpl impl = requireImpl(instance);
        try {
            impl.unbind();
        } finally {
            try {
                impl.freeBuffers();
            } finally {
                instances.remove(impl);
            }
        }
    }

    @Override
    public void tick() {
        for (AuralisSoundInstanceImpl inst : instances.values()) {
            try {
                inst.processPendingBindAndPlay();
            } catch (Throwable ignored) {
            }
        }

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 listenerPos = cam.getPosition();
        float pitch = cam.getXRot();
        float yaw = cam.getYRot();
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 up = Vec3.directionFromRotation(pitch - 90.0F, yaw);

        al.submit(() -> {
            AL11.alDopplerFactor(0.0f);

            AL11.alListener3f(AL11.AL_POSITION, (float) listenerPos.x, (float) listenerPos.y, (float) listenerPos.z);
            AL11.alListener3f(AL11.AL_VELOCITY, 0f, 0f, 0f);

            float[] ori = new float[]{
                    (float) forward.x, (float) forward.y, (float) forward.z,
                    (float) up.x, (float) up.y, (float) up.z
            };
            AL11.alListenerfv(AL11.AL_ORIENTATION, ori);

            for (AuralisSoundInstanceImpl inst : instances.values()) {
                inst.updateStreamedBuffersOnALThread();
                inst.disposeIfNaturallyStoppedOnALThread();
                inst.applyVelocityZeroOnALThread();
                inst.applyDistanceAttenuationOnALThread(listenerPos, attenuationExponent, volumeSmoothing);
            }
        });

        sourcePool.tickRecycleEndedSources();

        List<AuralisSoundInstanceImpl> toRemove = new ArrayList<>();
        for (AuralisSoundInstanceImpl inst : instances.values()) {
            try {
                if (inst.finalizeNaturalDisposeIfNeeded() || inst.consumePendingEngineRemoval()) {
                    toRemove.add(inst);
                }
            } catch (Throwable ignored) {
            }
        }
        for (AuralisSoundInstanceImpl inst : toRemove) {
            instances.remove(inst);
        }
    }

    @Override
    public void shutdown() {
        for (AuralisSoundInstanceImpl inst : instances.values()) {
            try {
                inst.forceStopAndFree();
            } catch (Throwable ignored) {
            }
        }
        instances.clear();

        bufferCache.clearAll();
        sourcePool.close();
        AuralisAL.stopAndClearGlobal();
    }

    private AuralisSoundInstanceImpl requireImpl(AuralisSoundInstance instance) {
        if (instance instanceof AuralisSoundInstanceImpl impl) return impl;
        AuralisSoundInstanceImpl mapped = instances.get(instance);
        if (mapped != null) return mapped;
        throw new IllegalArgumentException("Not an Auralis engine instance: " + instance);
    }
}
