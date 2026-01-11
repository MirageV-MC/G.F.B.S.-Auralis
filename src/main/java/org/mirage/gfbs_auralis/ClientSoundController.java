package org.mirage.gfbs_auralis;

/**
 * Client-side sound instance registry & controller.
 * <p>
 * IMPORTANT: This class intentionally does NOT reference any client-only classes (e.g. Minecraft)
 * so that it can live in the common source set safely. It is only invoked from client-bound
 * network packet handlers.
 */

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.mirage.gfbs_auralis.api.AuralisApi;
import org.mirage.gfbs_auralis.api.AuralisSoundInstance;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientSoundController {
    private ClientSoundController() {}

    private static final Map<String, AuralisSoundInstance> INSTANCES = new ConcurrentHashMap<>();

    public static void play(
            String id,
            ResourceLocation soundEventId,
            float volume,
            float pitch,
            float speed,
            boolean isStatic,
            Vec3 position,
            boolean looping,
            int priority,
            float minDistance,
            float maxDistance
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(soundEventId, "soundEventId");
        Objects.requireNonNull(position, "position");

        // Ensure sane distances
        float minD = Math.max(0.01f, minDistance);
        float maxD = Math.max(minD, maxDistance);

        // Stop+unbind any previous instance with same id
        AuralisSoundInstance old = INSTANCES.remove(id);
        if (old != null) {
            try {
                old.stop();
            } catch (Throwable ignored) {}
            try {
                AuralisSoundInstance.unbind(old);
            } catch (Throwable ignored) {}
        }

        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundEventId);
        if (soundEvent == null) {
            GFBsAuralis.LOGGER.error("[Auralis] Unknown SoundEvent id: {}", soundEventId);
            return;
        }

        // If engine isn't initialized yet (e.g. early login), this becomes a no-op placeholder.
        if (!AuralisApi.isInitialized()) {
            GFBsAuralis.LOGGER.warn("[Auralis] Engine not initialized yet; ignoring play request for {}", soundEventId);
            return;
        }

        AuralisSoundInstance instance = AuralisApi.create(soundEvent);
        instance
                .setVolume(volume)
                .setPitch(pitch)
                .setSpeed(speed)
                .setStatic(isStatic)
                .setPosition(position)
                .setLooping(looping)
                .setPriority(priority)
                .setMinDistance(minD)
                .setMaxDistance(maxD);

        AuralisSoundInstance.bind(instance);
        instance.play();
        INSTANCES.put(id, instance);
    }

    public static void pause(String id) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.pause();
    }

    public static void stop(String id) {
        AuralisSoundInstance inst = INSTANCES.remove(id);
        if (inst == null) return;
        try {
            inst.stop();
        } finally {
            try {
                AuralisSoundInstance.unbind(inst);
            } catch (Throwable ignored) {}
        }
    }

    public static void setVolume(String id, float volume) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setVolume(volume);
    }

    public static void setPitch(String id, float pitch) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setPitch(pitch);
    }

    public static void setSpeed(String id, float speed) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setSpeed(speed);
    }

    public static void setPosition(String id, Vec3 pos) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setPosition(pos);
    }

    public static void setStatic(String id, boolean isStatic) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setStatic(isStatic);
    }

    public static void setLooping(String id, boolean looping) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setLooping(looping);
    }

    public static void setPriority(String id, int priority) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setPriority(priority);
    }

    public static void setMinDistance(String id, float distance) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setMinDistance(Math.max(0.01f, distance));
    }

    public static void setMaxDistance(String id, float distance) {
        AuralisSoundInstance inst = INSTANCES.get(id);
        if (inst != null) inst.setMaxDistance(Math.max(0.01f, distance));
    }
}
