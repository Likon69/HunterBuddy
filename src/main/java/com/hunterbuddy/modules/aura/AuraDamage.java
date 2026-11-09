package com.hunterbuddy.modules.aura;

import java.util.Locale;

final class AuraDamage {
    AuraTrack owner;
    String text = "";
    double x;
    double y;
    double z;
    long born;
    boolean critical;
    int lane;
    double width;

    void set(AuraTrack owner, float amount, long now, int lane, boolean critical) {
        this.owner = owner;
        text = Math.abs(amount - Math.round(amount)) < 0.001 ? Integer.toString(Math.round(amount))
            : String.format(Locale.ROOT, amount < 0.01f ? "%.3f" : amount < 0.1f ? "%.2f" : "%.1f", amount);
        x = owner.entity.getX();
        y = owner.entity.getBoundingBox().maxY + 0.3;
        z = owner.entity.getZ();
        born = now;
        this.critical = critical;
        this.lane = lane;
    }
}
