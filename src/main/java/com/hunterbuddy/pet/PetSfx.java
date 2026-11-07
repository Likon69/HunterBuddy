package com.hunterbuddy.pet;

import java.util.Arrays;

public final class PetSfx {
    public interface Backend {
        boolean active(int slot);
        boolean start(PetSfxAction action, int slot, float volume);
        void stop(int slot);

        default String refusal() {
            return "backend refused";
        }
    }

    private final Backend backend;
    private final PetSfxLog log;
    private final long[] next = new long[PetSfxAction.ALL.length];
    private final PetSfxAction[] playing = new PetSfxAction[3];
    private long now;
    private boolean enabled;
    private boolean quiet;
    private boolean shown = true;
    private float volume;

    public PetSfx(Backend backend) {
        this(backend, new PetSfxLog(null));
    }

    public PetSfx(Backend backend, PetSfxLog log) {
        this.backend = backend;
        this.log = log;
    }

    public void update(long now, boolean enabled, boolean quiet, float volume) {
        this.now = now;
        this.enabled = enabled;
        this.quiet = quiet;
        this.volume = volume;
        for (int i = 0; i < playing.length; i++) {
            if (playing[i] == null) continue;
            if (!enabled || volume <= 0 || quiet && playing[i] != PetSfxAction.SCARED) {
                backend.stop(i);
                playing[i] = null;
            } else if (!backend.active(i)) playing[i] = null;
        }
    }

    public void shown(boolean value) {
        shown = value;
    }

    public void play(PetSfxAction action) {
        if (!enabled) {
            if (!shown) return;
            log.skipped(action, now, "disabled (setting off or pack missing)");
            return;
        }
        if (volume <= 0) {
            log.skipped(action, now, "volume is zero");
            return;
        }
        if (quiet && action != PetSfxAction.SCARED) {
            log.skipped(action, now, "silenced by combat, regear or landing");
            return;
        }
        if (now < next[action.ordinal()]) return;
        int busy = 0;
        for (PetSfxAction value : playing) if (value != null) busy++;
        if (action.priority == 0 && busy >= playing.length - 1) return;
        for (int i = 0; i < playing.length; i++) {
            if (playing[i] != null) continue;
            if (backend.start(action, i, volume)) {
                playing[i] = action;
                next[action.ordinal()] = now + action.interval;
            } else if (backend.refusal() != null) log.skipped(action, now, backend.refusal());
            return;
        }
        int weakest = 0;
        for (int i = 1; i < playing.length; i++) if (playing[i].priority < playing[weakest].priority) weakest = i;
        if (playing[weakest].priority < action.priority) {
            backend.stop(weakest);
            playing[weakest] = null;
            play(action);
        } else log.skipped(action, now, "no free voice");
    }

    public void reset() {
        for (int i = 0; i < playing.length; i++) {
            backend.stop(i);
            playing[i] = null;
        }
        Arrays.fill(next, 0);
        enabled = false;
    }
}
