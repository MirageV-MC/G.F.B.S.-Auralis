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
import org.mirage.gfbs_auralis.api.AuralisSoundInstance;
import org.mirage.gfbs_auralis.api.IAuralisEngine;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AuralisEngine implements IAuralisEngine {
    private final Minecraft mc;
    private final AuralisAL al;

    private final OpenALSourcePool sourcePool;
    private final SoundBufferCache bufferCache;

    private final ConcurrentMap<AuralisSoundInstance, AuralisSoundInstanceImpl> instances = new ConcurrentHashMap<>();

    public AuralisEngine(Minecraft mc, AuralisAL al, int maxSources) {
        this.mc = Objects.requireNonNull(mc, "mc");
        this.al = Objects.requireNonNull(al, "al");

        this.sourcePool = new OpenALSourcePool(al, maxSources);
        this.bufferCache = new SoundBufferCache(mc, al);
    }

    @Override
    public AuralisSoundInstance create(SoundEvent soundEvent) {
        Objects.requireNonNull(soundEvent, "soundEvent");
        ResourceLocation eventId = soundEvent.getLocation();

        Sound chosen = resolveToConcreteSound(eventId);
        ResourceLocation soundPath = chosen.getPath();
        int bufferId = bufferCache.acquireBuffer(soundPath);

        AuralisSoundInstanceImpl inst = new AuralisSoundInstanceImpl(al, bufferId, bufferCache, sourcePool);
        instances.put(inst, inst);
        return inst;
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
        impl.unbind();
    }

    @Override
    public void tick() {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 p = cam.getPosition();
        float pitch = cam.getXRot();
        float yaw = cam.getYRot();
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 up = Vec3.directionFromRotation(pitch - 90.0F, yaw);

        al.submit(() -> {
            AL11.alListener3f(AL11.AL_POSITION, (float) p.x, (float) p.y, (float) p.z);
            float[] ori = new float[] {
                    (float) forward.x, (float) forward.y, (float) forward.z,
                    (float) up.x,      (float) up.y,      (float) up.z
            };
            AL11.alListenerfv(AL11.AL_ORIENTATION, ori);
        });

        sourcePool.tickRecycleEndedSources();
    }

    @Override
    public void shutdown() {
        for (AuralisSoundInstanceImpl inst : instances.values()) {
            try {
                inst.forceStopAndFree();
            } catch (Throwable ignored) {}
        }
        instances.clear();

        bufferCache.clearAll();
        sourcePool.close();
    }

    private AuralisSoundInstanceImpl requireImpl(AuralisSoundInstance instance) {
        if (instance instanceof AuralisSoundInstanceImpl impl) return impl;
        AuralisSoundInstanceImpl mapped = instances.get(instance);
        if (mapped != null) return mapped;
        throw new IllegalArgumentException("Not an Auralis engine instance: " + instance);
    }
}
