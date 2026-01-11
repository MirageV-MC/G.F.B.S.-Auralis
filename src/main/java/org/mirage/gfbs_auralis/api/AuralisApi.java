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
    
    /**
     * Checks if the Auralis engine is initialized.
     * @return true if the engine is initialized, false otherwise
     */
    public static boolean isInitialized() {
        return ENGINE != null;
    }

    public static AuralisSoundInstance create(SoundEvent soundEvent) {
        return engine().create(soundEvent);
    }
}
