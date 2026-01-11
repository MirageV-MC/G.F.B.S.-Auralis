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

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class DecodedPcm {
    private final int alFormat;
    private final int sampleRate;
    private final ByteBuffer pcmData;

    public DecodedPcm(int alFormat, int sampleRate, ByteBuffer pcmData) {
        this.alFormat = alFormat;
        this.sampleRate = sampleRate;
        this.pcmData = pcmData;
    }

    int alFormat() { return alFormat; }
    int sampleRate() { return sampleRate; }
    ByteBuffer pcmData() { return pcmData; }

    void free() {
        MemoryUtil.memFree(pcmData);
    }
}
