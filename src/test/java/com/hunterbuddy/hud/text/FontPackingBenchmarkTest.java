package com.hunterbuddy.hud.text;

import java.nio.ByteBuffer;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTruetype;
import static org.junit.jupiter.api.Assertions.*;

final class FontPackingBenchmarkTest {
    @Test
    void measuresTheMeteorGlyphPackingStageWithoutAGpu() throws Exception {
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/assets/meteor-client/fonts/JetBrains Mono.ttf")) {
            assertNotNull(stream); bytes = stream.readAllBytes();
        }
        ByteBuffer font = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        ByteBuffer bitmap = BufferUtils.createByteBuffer(2048 * 2048);
        int[] starts = {32, 160, 256, 880, 1024, 8734};
        int[] counts = {95, 96, 128, 144, 256, 1};
        STBTTPackedchar.Buffer[] characters = new STBTTPackedchar.Buffer[6];
        STBTTPackRange.Buffer ranges = STBTTPackRange.create(6);
        for (int i = 0; i < 6; i++) {
            characters[i] = STBTTPackedchar.create(counts[i]);
            ranges.get(i).set(15, starts[i], null, counts[i], characters[i], (byte) 2, (byte) 2);
        }
        STBTTPackContext context = STBTTPackContext.create();
        long total = 0;
        for (int iteration = 0; iteration < 12; iteration++) {
            assertTrue(STBTruetype.stbtt_PackBegin(context, bitmap, 2048, 2048, 0, 1));
            long start = System.nanoTime();
            boolean packed = STBTruetype.stbtt_PackFontRanges(context, font, 0, ranges);
            long elapsed = System.nanoTime() - start;
            STBTruetype.stbtt_PackEnd(context);
            assertTrue(packed);
            if (iteration >= 4) total += elapsed;
        }
        System.out.printf(Locale.ROOT, "STB PackFontRanges CPU mean: %.3f ms/atlas (8 samples after 4 warmups, Meteor six ranges, 2048 bitmap, no GPU)%n", total / 8_000_000.0);
    }
}
