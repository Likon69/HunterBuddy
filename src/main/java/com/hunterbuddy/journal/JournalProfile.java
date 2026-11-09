package com.hunterbuddy.journal;

import java.util.Arrays;
import java.util.Locale;

public final class JournalProfile {
    public enum Stage { LAYOUT, BUFFER, ROWS, BLUR_SUBMIT, DRAW_SUBMIT, TEXT_FLUSH }

    private final long[][] totals = new long[2][Stage.values().length];
    private final long[][] times = new long[2][4096];
    private final long[] frames = new long[2];
    private final int[] samples = new int[2];
    private final int[] cursor = new int[2];
    private int mode;
    private int warm;
    private long previous;

    public void frame(long now, boolean open) {
        int next = open ? 1 : 0;
        if (previous == 0 || next != mode) warm = 20;
        long delta = now - previous;
        mode = next;
        previous = now;
        frames[mode]++;
        if (warm > 0) warm--;
        else if (delta > 0 && delta < 5_000_000_000L) {
            times[mode][cursor[mode]++ % times[mode].length] = delta;
            samples[mode] = Math.min(times[mode].length, samples[mode] + 1);
        }
    }

    public void add(Stage stage, long elapsed) {
        totals[mode][stage.ordinal()] += elapsed;
    }

    public void pause() {
        previous = 0;
    }

    public void clear() {
        for (long[] total : totals) Arrays.fill(total, 0);
        Arrays.fill(frames, 0);
        Arrays.fill(samples, 0);
        Arrays.fill(cursor, 0);
        pause();
    }

    public String report() {
        StringBuilder text = new StringBuilder("HunterBuddy Log performance\nCPU timings are submission timings, not GPU timings.\nFrame samples exclude 20 transition frames and inactive gaps.\n");
        for (int i = 0; i < 2; i++) {
            text.append(i == 0 ? "\nDock only\n" : "\nJournal open\n");
            text.append("Rendered frames: ").append(frames[i]).append('\n');
            long[] sorted = Arrays.copyOf(times[i], samples[i]);
            Arrays.sort(sorted);
            double mean = Arrays.stream(sorted).average().orElse(0) / 1_000_000;
            text.append(String.format(Locale.ROOT, "Frame mean: %.4f ms; p95: %.4f ms; mean FPS: %.2f%n", mean,
                sorted.length == 0 ? 0 : sorted[(int) ((sorted.length - 1) * 0.95)] / 1_000_000.0, mean == 0 ? 0 : 1000 / mean));
            for (Stage stage : Stage.values()) text.append(String.format(Locale.ROOT, "%s mean: %.6f ms%n", stage,
                frames[i] == 0 ? 0 : totals[i][stage.ordinal()] / (frames[i] * 1_000_000.0)));
        }
        return text.toString();
    }
}
