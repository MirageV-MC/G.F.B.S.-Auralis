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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL11;
import org.mirage.gfbs_auralis.utils.OggVorbisDecoder;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class SoundBufferCache {
    private record Entry(int bufferId, AtomicInteger refs) {}

    private final Minecraft mc;
    private final AuralisAL al;
    private final Map<ResourceLocation, Entry> cache = new ConcurrentHashMap<>();
    private final Map<Integer, ResourceLocation> bufferToPath = new ConcurrentHashMap<>();

    SoundBufferCache(Minecraft mc, AuralisAL al) {
        this.mc = Objects.requireNonNull(mc, "mc");
        this.al = Objects.requireNonNull(al, "al");
    }

    int acquireBuffer(ResourceLocation soundPath) {
        Objects.requireNonNull(soundPath, "soundPath");

        Entry existing = cache.get(soundPath);
        if (existing != null) {
            existing.refs.incrementAndGet();
            return existing.bufferId();
        }

        int bufferId = al.callBlocking(() -> {
            DecodedPcm pcm = decode(soundPath);
            int id = AL11.alGenBuffers();
            AL11.alBufferData(id, pcm.alFormat(), pcm.pcmData(), pcm.sampleRate());
            pcm.free();
            return id;
        });

        Entry entry = new Entry(bufferId, new AtomicInteger(1));
        Entry prev = cache.putIfAbsent(soundPath, entry);
        if (prev != null) {
            al.submit(() -> AL11.alDeleteBuffers(bufferId));
            prev.refs.incrementAndGet();
            return prev.bufferId();
        }

        bufferToPath.put(bufferId, soundPath);

        return bufferId;
    }

    void releaseBuffer(int bufferId) {
        ResourceLocation soundPath = bufferToPath.get(bufferId);
        if (soundPath == null) {
            return;
        }

        Entry entry = cache.get(soundPath);
        if (entry == null || entry.bufferId() != bufferId) {
            bufferToPath.remove(bufferId);
            return;
        }

        int left = entry.refs.decrementAndGet();
        if (left <= 0) {
            if (cache.remove(soundPath, entry)) {
                bufferToPath.remove(bufferId);
                al.submit(() -> AL11.alDeleteBuffers(bufferId));
            }
        }
    }

    void clearAll() {
        for (Entry e : cache.values()) {
            int id = e.bufferId();
            al.submit(() -> AL11.alDeleteBuffers(id));
        }
        cache.clear();
        bufferToPath.clear();
    }

    private DecodedPcm decode(ResourceLocation soundPath) {
        try {
            Resource r = mc.getResourceManager().getResource(soundPath).orElseThrow(
                    () -> new IllegalArgumentException("Missing sound resource: " + soundPath)
            );
            try (InputStream in = r.open()) {
                return OggVorbisDecoder.decodeFully(in);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode OGG: " + soundPath, e);
        }
    }
}
