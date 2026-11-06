package com.hunterbuddy.pet;

public final class PetSurfaces {
    public static final PetSurfaces INSTANCE = new PetSurfaces();
    private static final int CAPACITY = 1024;
    public final float[] left = new float[CAPACITY];
    public final float[] right = new float[CAPACITY];
    public final float[] top = new float[CAPACITY];
    public final int[] kind = new int[CAPACITY];
    public int count;
    public int width;
    public int height;
    public boolean enabled;
    public boolean bottom = true;
    public boolean hotbar = true;
    public boolean bars = true;
    public boolean meteor = true;
    public boolean climbing = true;
    public float petSize = 80;

    private PetSurfaces() {
    }

    public void begin(int width, int height) {
        this.width = width;
        this.height = height;
        count = 0;
        if (enabled && bottom) add(0, width, height, 0);
    }

    public void add(float x1, float x2, float y, int type) {
        if (!enabled || !Float.isFinite(y) || y < 0 || y > height) return;
        x1 = Math.max(0, x1);
        x2 = Math.min(width, x2);
        if (x2 <= x1) return;
        for (int i = 0; i < count; i++) {
            if (kind[i] == type && top[i] == y && x1 <= right[i] + 1 && x2 >= left[i] - 1) {
                left[i] = Math.min(left[i], x1);
                right[i] = Math.max(right[i], x2);
                return;
            }
        }
        if (count == CAPACITY) return;
        left[count] = x1;
        right[count] = x2;
        top[count] = y;
        kind[count++] = type;
    }

    public void excludeFloor(float x1, float x2) {
        int originalCount = count;
        for (int i = 0; i < originalCount; i++) {
            if (kind[i] != 0 || x1 >= right[i] || x2 <= left[i]) continue;
            float end = right[i];
            right[i] = Math.max(left[i], x1);
            if (x2 < end) add(Math.max(x2, left[i]), end, height, 0);
        }
    }

    public boolean wall(float x, float y) {
        return climbing && (x == 0 || x == width) && y >= 0 && y <= height;
    }

    public int support(float x, float y) {
        for (int i = 0; i < count; i++) {
            if (right[i] > left[i] && Math.abs(top[i] - y) < 0.1f && x >= left[i] && x <= right[i]) return i;
        }
        return -1;
    }

    public int below(float x, float from, float to) {
        int best = -1;
        float nearest = Float.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            if (right[i] > left[i] && x >= left[i] && x <= right[i] && top[i] >= from && top[i] <= to && top[i] < nearest) {
                nearest = top[i];
                best = i;
            }
        }
        return best;
    }

    public boolean safe(float x, float y, float size) {
        return Math.abs(x - width * 0.5f) > size * 0.65f + 24
            || y < height * 0.5f - 32 || y - size > height * 0.5f + 32;
    }
}
