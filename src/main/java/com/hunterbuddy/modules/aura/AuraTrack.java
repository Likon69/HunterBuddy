package com.hunterbuddy.modules.aura;

import java.util.Locale;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

final class AuraTrack {
    final AuraHealth health = new AuraHealth();
    LivingEntity entity;
    String name = "";
    String displayName = "";
    String life = "";
    String gold = "";
    double nameWidth;
    double lifeWidth;
    double goldWidth;
    long seen;
    long attempt;
    long hit;
    long critical;
    long refreshed;
    double measuredSize;

    void reset(LivingEntity entity, long now) {
        health.clear();
        this.entity = entity;
        name = entity.getName().getString();
        displayName = "";
        life = gold = "";
        seen = now;
        attempt = hit = critical = refreshed = 0;
        measuredSize = 0;
        health.observe(entity.getHealth(), entity.getMaxHealth(), entity.getAbsorptionAmount(), player(), now);
        labels();
    }

    boolean player() { return entity instanceof PlayerEntity; }

    void labels() {
        life = number(health.health);
        gold = health.absorption > 0 ? "+" + number(health.absorption) : "";
        measuredSize = 0;
    }

    private static String number(float value) {
        return Math.abs(value - Math.round(value)) < 0.001 ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }
}
