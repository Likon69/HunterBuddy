package com.hunterbuddy.pet;

import java.util.Arrays;

public final class PetSfx {
    public interface Backend {
        boolean active(int slot);
        boolean start(PetSfxAction action, int slot, float volume);
        void stop(int slot);
    }

    private final Backend backend;
    private final long[] next = new long[PetSfxAction.ALL.length];
    private final PetSfxAction[] playing = new PetSfxAction[3];
    private long now;
    private boolean enabled;
    private boolean quiet;
    private float volume;

    public PetSfx(Backend backend) {
        this.backend = backend;
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

    public void play(PetSfxAction action) {
        if (!enabled || volume <= 0 || quiet && action != PetSfxAction.SCARED || now < next[action.ordinal()]) return;
        for (int i = 0; i < playing.length; i++) {
            if (playing[i] != null) continue;
            if (backend.start(action, i, volume)) {
                playing[i] = action;
                next[action.ordinal()] = now + action.interval;
            }
            return;
        }
        if (action == PetSfxAction.SCARED) {
            backend.stop(0);
            playing[0] = null;
            play(action);
        }
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
