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

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class GFBsAuralisConfig {
    public static class ServerConfig {
        public final ForgeConfigSpec.IntValue maxConcurrentSounds;
        public final ForgeConfigSpec.DoubleValue defaultVolume;
        public final ForgeConfigSpec.BooleanValue enableRemoteSounds;

        ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Server configuration for GFBS-Auralis")
                    .push("general");

            maxConcurrentSounds = builder
                    .comment("Maximum number of concurrent sounds per player")
                    .defineInRange("maxConcurrentSounds", 16, 1, 64);

            defaultVolume = builder
                    .comment("Default volume for sounds (0.0 to 1.0)")
                    .defineInRange("defaultVolume", 1.0, 0.0, 1.0);

            enableRemoteSounds = builder
                    .comment("Enable playing sounds from remote locations")
                    .define("enableRemoteSounds", true);

            builder.pop();
        }
    }

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }
}