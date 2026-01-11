package org.mirage.gfbs_auralis.utils;
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
