package com.hunterbuddy.hud.text;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HudFontCacheTest {
    private static final double[] HUD = {0.88, 0.62, 0.65, 0.8, 0.64, 1, 0.88, 0.65};
    private static final double[] MENU = {0.82};
    private static final double[] LOG = {0.82 * 0.85, 0.95 * 0.85, 0.65 * 0.85, 0.72 * 0.85, 0.85, 0.62 * 0.85};

    @Test
    void allSettingsShareOnlyThreeAtlasHeights() {
        Set<Integer> sizes = new HashSet<>();
        for (double scale = 0.1; scale < 4; scale += 0.01) sizes.add((int) Math.round(HudTextScale.fixed(scale) * 18));
        assertEquals(Set.of(12, 15, 18), sizes);
    }

    @Test
    void representativeThreePassReplayStopsAtlasChurnAfterWarmup() {
        Cache before = new Cache();
        Cache after = new Cache();
        for (int frame = 0; frame < 20; frame++) {
            before.pass(HUD, false); before.pass(MENU, false); before.pass(LOG, false);
            after.pass(HUD, true);
        }
        int oldLoads = before.loads;
        int newLoads = after.loads;
        for (int frame = 0; frame < 120; frame++) {
            before.pass(HUD, false); before.pass(MENU, false); before.pass(LOG, false);
            after.pass(HUD, true);
        }
        System.out.printf(java.util.Locale.ROOT, "Atlas replay: before %.2f loads/frame, after %.2f loads/frame (120 frames after warmup)%n",
            (before.loads - oldLoads) / 120.0, (after.loads - newLoads) / 120.0);
        assertTrue(before.loads > oldLoads);
        assertEquals(newLoads, after.loads);
    }

    private static final class Cache {
        final Set<Integer> active = new HashSet<>();
        final LinkedHashMap<Integer, Boolean> cached = new LinkedHashMap<>(8, 0.75f, true);
        int loads;

        void pass(double[] requested, boolean fixed) {
            Set<Integer> visited = new HashSet<>();
            for (double scale : requested) {
                int height = (int) Math.round(18 * (fixed ? HudTextScale.fixed(scale) : scale));
                if (!active.contains(height)) {
                    if (cached.remove(height) == null) loads++;
                    active.add(height);
                }
                visited.add(height);
            }
            for (int height : Set.copyOf(active)) {
                if (visited.contains(height)) continue;
                active.remove(height);
                cached.put(height, true);
                if (cached.size() > 4) cached.remove(cached.keySet().iterator().next());
            }
        }
    }
}
