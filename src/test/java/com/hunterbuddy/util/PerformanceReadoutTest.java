package com.hunterbuddy.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.junit.jupiter.api.Test;

class PerformanceReadoutTest {
    @Test
    void pingCopiesTheLatestSamplesInChronologicalOrder() throws Exception {
        PingSampler sampler = create(PingSampler.class);
        Method record = PingSampler.class.getDeclaredMethod("record", int.class);
        record.setAccessible(true);
        for (int i = 1; i <= 35; i++) record.invoke(sampler, i);
        int[] target = new int[PingSampler.CAPACITY];
        assertEquals(PingSampler.CAPACITY, sampler.copyHistory(target));
        assertArrayEquals(sampler.history(), target);
        assertEquals(6, target[0]);
        assertEquals(35, target[target.length - 1]);
        int[] small = new int[3];
        assertEquals(3, sampler.copyHistory(small));
        assertArrayEquals(new int[] {33, 34, 35}, small);
        assertEquals(0, sampler.copyHistory(new int[0]));
    }

    @Test
    void tpsCopiesAcrossTheRingBoundaryWithoutExposingTheRing() throws Exception {
        TpsSampler sampler = create(TpsSampler.class);
        Field samples = TpsSampler.class.getDeclaredField("samples");
        samples.setAccessible(true);
        float[] ring = (float[]) samples.get(sampler);
        ring[TpsSampler.CAPACITY - 2] = 20;
        ring[TpsSampler.CAPACITY - 1] = 18;
        ring[0] = 15;
        ring[1] = 9;
        set(sampler, "head", 2);
        set(sampler, "filled", 4);
        float[] target = new float[4];
        assertEquals(4, sampler.copyHistory(target));
        assertArrayEquals(new float[] {20, 18, 15, 9}, target);
        assertArrayEquals(sampler.history(), target);
        float[] small = new float[2];
        sampler.copyHistory(small);
        assertArrayEquals(new float[] {15, 9}, small);
        target[0] = 0;
        assertEquals(20, sampler.history()[0]);
        assertEquals(0, sampler.copyHistory(new float[0]));
    }

    @Test
    void reusableTintMatchesTheExistingPulse() {
        SettingColor normal = new SettingColor(110, 240, 130, 177);
        SettingColor accent = new SettingColor(255, 80, 80);
        Color target = new Color();
        float[] freshness = {0, 0.25f, 0.5f, 1};
        int[][] subtle = {{110, 240, 130}, {131, 216, 122}, {153, 192, 115}, {197, 144, 100}};
        int[][] lively = {{110, 240, 130}, {146, 200, 117}, {182, 160, 105}, {255, 80, 80}};
        for (HudPulse.Style style : HudPulse.Style.values()) {
            int[][] expected = style == HudPulse.Style.Subtle ? subtle : lively;
            assertSame(normal, HudPulse.tint(style, normal, accent, 0));
            for (int i = 0; i < freshness.length; i++) {
                Color legacy = HudPulse.tint(style, normal, accent, freshness[i]);
                assertSame(target, HudPulse.tint(style, normal, accent, freshness[i], target));
                assertEquals(expected[i][0], target.r);
                assertEquals(expected[i][1], target.g);
                assertEquals(expected[i][2], target.b);
                assertEquals(177, target.a);
                assertEquals(target.r, legacy.r);
                assertEquals(target.g, legacy.g);
                assertEquals(target.b, legacy.b);
                assertEquals(target.a, legacy.a);
            }
        }
    }

    private static <T> T create(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
