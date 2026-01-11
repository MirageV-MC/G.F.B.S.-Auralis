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

import net.minecraft.world.phys.Vec3;

public interface AuralisSoundInstance {
    static void bind(AuralisSoundInstance instance) {
        AuralisApi.engine().bind(instance);
    }

    static void unbind(AuralisSoundInstance instance) {
        AuralisApi.engine().unbind(instance);
    }

    void play();
    void pause();
    void stop();
    boolean isPlaying();
    boolean isPaused();

    boolean isBound();

    AuralisSoundInstance setVolume(float volume);
    float getVolume();

    AuralisSoundInstance setPitch(float pitch);
    float getPitch();

    AuralisSoundInstance setSpeed(float speed);
    float getSpeed();

    AuralisSoundInstance setStatic(boolean isStatic);
    boolean isStatic();

    AuralisSoundInstance setPosition(Vec3 pos);
    Vec3 getPosition();

    AuralisSoundInstance setMinDistance(float dist);
    float getMinDistance();

    AuralisSoundInstance setMaxDistance(float dist);
    float getMaxDistance();

    AuralisSoundInstance setLooping(boolean looping);
    boolean isLooping();

    AuralisSoundInstance setPriority(int priority);

    int getPriority();

    AuralisSoundInstance addListener(AuralisSoundListener listener);

    AuralisSoundInstance removeListener(AuralisSoundListener listener);
}
