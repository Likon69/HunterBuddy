package com.hunterbuddy.modules.aura;

public final class AuraHealth {
    private static final float EPSILON = 0.001f;
    public float health;
    public float maximum;
    public float absorption;
    private boolean valid;
    private boolean changedPlayerHealth;
    private int unanswered;
    private long lastChange;
    private float trailFrom;
    private long trailAt;

    public void clear() {
        valid = changedPlayerHealth = false;
        unanswered = 0;
        lastChange = trailAt = 0;
        health = maximum = absorption = trailFrom = 0;
    }

    public void attempted() { unanswered = Math.min(2, unanswered + 1); }

    public float observe(float next, float max, float absorb, boolean player, long now) {
        if (!Float.isFinite(next) || !Float.isFinite(max) || !Float.isFinite(absorb)
            || max <= 0 || next < 0 || next > max + EPSILON || absorb < 0) {
            clear();
            return 0;
        }
        if (!valid || Math.abs(maximum - max) > EPSILON) {
            health = trailFrom = next;
            maximum = max;
            absorption = absorb;
            valid = true;
            changedPlayerHealth = false;
            unanswered = 0;
            lastChange = now;
            return 0;
        }
        float previousTrail = trail(now);
        float lost = Math.max(0, health - next);
        boolean hpChanged = Math.abs(next - health) > EPSILON;
        boolean anyChanged = hpChanged || Math.abs(absorb - absorption) > EPSILON;
        if (hpChanged) {
            lastChange = now;
            if (next < max - EPSILON) changedPlayerHealth = true;
        }
        if (anyChanged) { unanswered = 0; lastChange = now; }
        if (next < health) {
            trailFrom = Math.max(previousTrail, health);
            trailAt = now;
        } else if (next > health) {
            trailFrom = next;
            trailAt = now;
        }
        health = next;
        maximum = max;
        absorption = absorb;
        return lost > EPSILON && trusted(player, now) ? lost : 0;
    }

    public boolean trusted(boolean player, long now) {
        return valid && (!player || changedPlayerHealth && health < maximum - EPSILON
            && now - lastChange <= 1500 && unanswered < 2);
    }

    public float fraction() { return maximum > 0 ? Math.clamp(health / maximum, 0, 1) : 0; }

    public float trail(long now) {
        double progress = Math.clamp((now - trailAt - 220) / 500.0, 0, 1);
        double eased = 1 - Math.pow(1 - progress, 3);
        return (float) Math.max(health, trailFrom + (health - trailFrom) * eased);
    }

    public float trailFraction(long now) { return maximum > 0 ? Math.clamp(trail(now) / maximum, 0, 1) : 0; }
}
