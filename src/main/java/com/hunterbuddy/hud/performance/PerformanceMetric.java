package com.hunterbuddy.hud.performance;

import java.util.Locale;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class PerformanceMetric {
    public final String label;
    public final String sample;
    public final Color color = new Color();
    public String text = "-";
    public int level;
    public float flash;
    private final String suffix;
    private long reading = Long.MIN_VALUE;

    public PerformanceMetric(String label, String sample, String suffix) {
        this.label = label;
        this.sample = sample;
        this.suffix = suffix;
    }

    public void integer(int value) {
        if (reading == value) return;
        reading = value;
        text = value < 0 ? "-" : Integer.toString(value) + suffix;
    }

    public void decimal(float value) {
        int bits = Float.floatToIntBits(value);
        if (reading == bits) return;
        reading = bits;
        text = value < 0 ? "-" : String.format(Locale.ROOT, "%.1f", value);
    }
}
