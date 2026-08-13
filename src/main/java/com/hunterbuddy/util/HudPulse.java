package com.hunterbuddy.util;

import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * The small amount of motion the status HUDs share.
 *
 * <p>Two jobs that turn out to be the same one: flashing a number that has just changed, and
 * easing a progress bar toward its new length. Both are "remember what it was, walk toward what
 * it is", and writing them twice would give the pair two different rhythms.
 */
public final class HudPulse {
    /** How the motion reads. */
    public enum Style {
        /** A brief tint and a bar that slides. Easy to leave running for hours. */
        Subtle,
        /** A bounce on the number and a highlight sweeping the bar. Harder to miss. */
        Lively
    }

    private HudPulse() {
    }

    /**
     * Tracks one value and reports how recently it changed.
     *
     * <p>Keyed by name rather than by index: fields come and go from these lines as counters
     * leave zero, and an index would move the animation onto whichever row took the slot.
     */
    public static final class Tracker {
        private final Map<String, String> lastValue = new HashMap<>();
        private final Map<String, Long> changedAt = new HashMap<>();
        private final Map<String, Double> eased = new HashMap<>();

        /**
         * Records the current value and returns how fresh the last change is, from 1 down to 0.
         *
         * <p>Zero on the first sighting of a field, so an element appearing does not flash every
         * one of its rows at once.
         */
        public float freshness(String key, String value, long durationMs) {
            String previous = lastValue.put(key, value);

            if (previous == null) return 0.0f;
            if (!previous.equals(value)) changedAt.put(key, System.currentTimeMillis());

            Long at = changedAt.get(key);
            if (at == null) return 0.0f;

            long elapsed = System.currentTimeMillis() - at;
            if (elapsed >= durationMs) return 0.0f;

            return 1.0f - (float) elapsed / durationMs;
        }

        /**
         * Moves a stored number toward a target and returns where it has got to.
         *
         * <p>Frame-rate independent, so the glide takes the same time whatever the machine is
         * managing — the same reason the speed average is built on elapsed time rather than on a
         * fixed step per frame.
         */
        public double ease(String key, double target, double dt, double tau) {
            double current = eased.getOrDefault(key, target);
            double alpha = 1.0 - Math.exp(-dt / Math.max(0.01, tau));
            double next = current + alpha * (target - current);

            eased.put(key, next);
            return next;
        }
    }

    /**
     * The colour a value should be drawn in, given how recently it changed.
     *
     * <p>Fades back to the normal colour rather than switching to it, so a change reads as one
     * movement instead of two.
     */
    public static Color tint(Style style, SettingColor normal, SettingColor accent, float freshness) {
        if (freshness <= 0.0f) return normal;

        float weight = style == Style.Lively ? freshness : freshness * 0.6f;

        return new Color(
            (int) (normal.r + (accent.r - normal.r) * weight),
            (int) (normal.g + (accent.g - normal.g) * weight),
            (int) (normal.b + (accent.b - normal.b) * weight),
            normal.a);
    }

    /**
     * Vertical offset for a value that has just changed, in pixels.
     *
     * <p>Only the lively style moves anything: a line of text that jumps is exactly what makes an
     * always-on element tiring, so the subtle style stays put and says it with colour alone.
     */
    public static double bounce(Style style, float freshness, double scale) {
        if (style != Style.Lively || freshness <= 0.0f) return 0.0;

        // One hop, not a wobble: sin over a single half-period rises and settles once.
        return -Math.sin(freshness * Math.PI) * 2.0 * scale;
    }

    /**
     * Scale factor for something that has just changed: swells and settles back to 1.
     *
     * <p>For icons, where a hop would only shift the row. Growing and shrinking says the same
     * thing without moving anything else, and it ends exactly where it started.
     */
    public static double pop(float freshness) {
        if (freshness <= 0.0f) return 1.0;

        return 1.0 + 0.4 * Math.sin(freshness * Math.PI);
    }

    /**
     * Position of the highlight sweeping a progress bar, from 0 to 1, or -1 when there is none.
     *
     * <p>Runs on the clock rather than on any event, because its job is to say the bar is live
     * even while the number behind it is still.
     */
    public static double shimmer(Style style, long periodMs) {
        if (style != Style.Lively) return -1.0;

        return (System.currentTimeMillis() % periodMs) / (double) periodMs;
    }
}
