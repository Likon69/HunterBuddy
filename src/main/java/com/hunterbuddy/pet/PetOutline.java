package com.hunterbuddy.pet;

import java.util.Arrays;
import net.minecraft.client.texture.NativeImage;

final class PetOutline {
    private PetOutline() {
    }

    static float[] read(NativeImage image) {
        long[] points = new long[image.getWidth() * 4];
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            int top = image.getHeight();
            int bottom = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getColorArgb(x, y) >>> 24) < 16) continue;
                top = Math.min(top, y);
                bottom = y;
            }
            if (bottom < top) continue;
            points[count++] = point(x, top);
            points[count++] = point(x + 1, top);
            points[count++] = point(x, bottom + 1);
            points[count++] = point(x + 1, bottom + 1);
        }
        if (count < 4) throw new IllegalArgumentException();
        Arrays.sort(points, 0, count);
        long[] hull = new long[count * 2];
        int used = 0;
        for (int i = 0; i < count; i++) {
            while (used >= 2 && cross(hull[used - 2], hull[used - 1], points[i]) <= 0) used--;
            hull[used++] = points[i];
        }
        int lower = used;
        for (int i = count - 2; i >= 0; i--) {
            while (used > lower && cross(hull[used - 2], hull[used - 1], points[i]) <= 0) used--;
            hull[used++] = points[i];
        }
        float[] outline = new float[(used - 1) * 2];
        for (int i = 0; i < used - 1; i++) {
            outline[i * 2] = hull[i] >>> 32;
            outline[i * 2 + 1] = hull[i] & 0xffffffffL;
        }
        return outline;
    }

    private static long point(int x, int y) {
        return ((long) x << 32) | y;
    }

    private static long cross(long a, long b, long c) {
        long ax = a >>> 32;
        long ay = a & 0xffffffffL;
        return ((b >>> 32) - ax) * ((c & 0xffffffffL) - ay) - ((b & 0xffffffffL) - ay) * ((c >>> 32) - ax);
    }
}
