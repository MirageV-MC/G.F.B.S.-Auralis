package org.mirage.gfbs_auralis.utils;

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

import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.mirage.gfbs_auralis.DecodedPcm;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class OggVorbisDecoder {
    private OggVorbisDecoder() {}

    public static DecodedPcm decodeFully(InputStream in) throws Exception {
        byte[] bytes = readAll(in);
        ByteBuffer ogg = MemoryUtil.memAlloc(bytes.length);
        ogg.put(bytes).flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            long handle = STBVorbis.stb_vorbis_open_memory(ogg, error, null);
            if (handle == MemoryUtil.NULL) {
                throw new IllegalStateException("stb_vorbis_open_memory failed, error=" + error.get(0));
            }

            try (STBVorbisInfo info = STBVorbisInfo.malloc(stack)) {
                STBVorbis.stb_vorbis_get_info(handle, info);
                int channels = info.channels();
                int sampleRate = info.sample_rate();

                int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
                ByteBuffer pcm = MemoryUtil.memAlloc(totalSamples * channels * 2);

                var shortView = pcm.asShortBuffer();
                int samplesDecoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, shortView);
                shortView.limit(samplesDecoded * channels);

                int alFormat = (channels == 1) ? AL11.AL_FORMAT_MONO16 : AL11.AL_FORMAT_STEREO16;
                return new DecodedPcm(alFormat, sampleRate, pcm);
            } finally {
                STBVorbis.stb_vorbis_close(handle);
            }
        } finally {
            MemoryUtil.memFree(ogg);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) >= 0) {
            baos.write(buf, 0, r);
        }
        return baos.toByteArray();
    }
}
