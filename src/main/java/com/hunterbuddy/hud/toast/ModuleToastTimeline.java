package com.hunterbuddy.hud.toast;

import java.util.Arrays;

public final class ModuleToastTimeline {
    public record Notice(String name, String bind, boolean enabled) {
    }

    public static final class Slot {
        public Notice notice;
        public long started;
        public double row;

        public double opacity(long now, double seconds) {
            double age = Math.max(0, (now - started) / 1_000_000_000.0);
            return smooth(age / 0.24) * smooth((seconds - age) / 0.6);
        }

        public double remaining(long now, double seconds) {
            return Math.max(0, Math.min(1, 1 - (now - started) / (seconds * 1_000_000_000.0)));
        }
    }

    private final Notice[] pending = new Notice[32];
    private final Slot[] visible = new Slot[6];
    private int head;
    private int waiting;
    private int count;
    private long last;
    private long next;

    public ModuleToastTimeline() {
        for (int i = 0; i < visible.length; i++) visible[i] = new Slot();
    }

    public void offer(Notice notice) {
        if (waiting == pending.length) {
            pending[head] = null;
            head = (head + 1) % pending.length;
            waiting--;
        }
        pending[(head + waiting) % pending.length] = notice;
        waiting++;
    }

    public void update(long now, int maximum, double seconds) {
        maximum = Math.max(1, Math.min(visible.length, maximum));
        double dt = last == 0 ? 0.016 : Math.max(0, Math.min(0.1, (now - last) / 1_000_000_000.0));
        last = now;
        for (int i = count - 1; i >= 0; i--) {
            if (i >= maximum || now - visible[i].started >= seconds * 1_000_000_000.0) remove(i);
        }
        while (waiting > 0 && count < maximum && now >= next) {
            Notice notice = pending[head];
            pending[head] = null;
            head = (head + 1) % pending.length;
            waiting--;
            Slot slot = visible[count];
            slot.notice = notice;
            slot.started = now;
            slot.row = count;
            count++;
            next = now + 160_000_000L;
        }
        double blend = 1 - Math.exp(-dt / 0.09);
        for (int i = 0; i < count; i++) visible[i].row += (i - visible[i].row) * blend;
    }

    public int size() {
        return count;
    }

    public Slot get(int index) {
        return visible[index];
    }

    public void clear() {
        Arrays.fill(pending, null);
        for (Slot slot : visible) slot.notice = null;
        head = waiting = count = 0;
        last = next = 0;
    }

    private void remove(int index) {
        Slot removed = visible[index];
        System.arraycopy(visible, index + 1, visible, index, count - index - 1);
        visible[--count] = removed;
        removed.notice = null;
    }

    private static double smooth(double value) {
        value = Math.max(0, Math.min(1, value));
        return value * value * (3 - 2 * value);
    }
}
