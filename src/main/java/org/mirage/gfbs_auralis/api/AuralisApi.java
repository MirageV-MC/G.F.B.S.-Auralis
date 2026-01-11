package org.mirage.gfbs_auralis.api;

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

import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

public final class AuralisApi {
    private static volatile @Nullable IAuralisEngine ENGINE;

    private AuralisApi() {}

    public static void setEngine(IAuralisEngine engine) {
        ENGINE = engine;
    }

    public static IAuralisEngine engine() {
        IAuralisEngine e = ENGINE;
        if (e == null) {
            throw new IllegalStateException(
                    "Auralis engine not initialized. This is likely because: " +
                            "1. You're trying to use Auralis on the server side (it's client-only) " +
                            "2. The mod hasn't finished loading yet " +
                            "3. The engine initialization failed for some reason"
            );
        }
        return e;
    }

    public static boolean isInitialized() {
        return ENGINE != null;
    }

    public static AuralisSoundInstance create(SoundEvent soundEvent) {
        if (ENGINE == null) {
            return new ServerPlaceholderSoundInstance();
        }
        return engine().create(soundEvent);
    }

    private static class ServerPlaceholderSoundInstance implements AuralisSoundInstance {
        @Override public void play() {}
        @Override public void pause() {}
        @Override public void stop() {}
        @Override public boolean isPlaying() { return false; }
        @Override public boolean isPaused() { return false; }
        @Override public boolean isBound() { return false; }
        @Override public AuralisSoundInstance setVolume(float volume) { return this; }
        @Override public float getVolume() { return 1.0f; }
        @Override public AuralisSoundInstance setPitch(float pitch) { return this; }
        @Override public float getPitch() { return 1.0f; }
        @Override public AuralisSoundInstance setSpeed(float speed) { return this; }
        @Override public float getSpeed() { return 1.0f; }
        @Override public AuralisSoundInstance setStatic(boolean isStatic) { return this; }
        @Override public boolean isStatic() { return false; }
        @Override public AuralisSoundInstance setPosition(net.minecraft.world.phys.Vec3 pos) { return this; }
        @Override public net.minecraft.world.phys.Vec3 getPosition() { return net.minecraft.world.phys.Vec3.ZERO; }
        @Override public AuralisSoundInstance setMinDistance(float dist) { return this; }
        @Override public float getMinDistance() { return 1.0f; }
        @Override public AuralisSoundInstance setMaxDistance(float dist) { return this; }
        @Override public float getMaxDistance() { return 48.0f; }
        @Override public AuralisSoundInstance setLooping(boolean looping) { return this; }
        @Override public boolean isLooping() { return false; }
        @Override public AuralisSoundInstance setPriority(int priority) { return this; }
        @Override public int getPriority() { return 50; }
        @Override public AuralisSoundInstance addListener(AuralisSoundListener listener) { return this; }
        @Override public AuralisSoundInstance removeListener(AuralisSoundListener listener) { return this; }
    }
}