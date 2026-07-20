package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class Pitch40Classic extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFirework = this.settings.createGroup("Auto Firework");

    public final Setting<Double> lowerBound = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("lower-bound")
        .description("Hauteur Y où on repart en pitch up.")
        .defaultValue(220.0)
        .min(220.0)
        .sliderMax(500000.0)
        .build()
    );

    public final Setting<Double> upperBound = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("upper-bound")
        .description("Hauteur Y où on repart en pitch down.")
        .defaultValue(120.0)
        .min(-128.0)
        .sliderMax(360.0)
        .build()
    );

    public final Setting<Double> pitchRate = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-rate")
        .description("Degrés par tick pour ajuster le pitch.")
        .defaultValue(2.0)
        .min(0.1)
        .sliderMax(10.0)
        .build()
    );

    public final Setting<PitchMode> pitchMode = this.sgGeneral.add(new EnumSetting.Builder<PitchMode>()
        .name("pitch-mode")
        .description("Mode d'oscillation: CLASSIC (±40°, Meteor 0.5.8, prédictible) ou EFFICIENT (37.72°/-54.77°, Meteor 1.21.11, plus naturel avec drift up).")
        .defaultValue(PitchMode.CLASSIC)
        .build()
    );

    // ---- Auto Firework (porté de JEFF Pitch40Util) ----

    public final Setting<Boolean> autoFirework = this.sgFirework.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Pop un feu d'artifice automatiquement (logique JEFF Pitch40Util).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> velocityThreshold = this.sgFirework.add(new DoubleSetting.Builder()
        .name("velocity-threshold")
        .description("Velocity must be below this value when going up for firework to activate.")
        .defaultValue(-0.05)
        .sliderRange(-0.5, 1.0)
        .visible(autoFirework::get)
        .build()
    );

    public final Setting<Integer> fireworkCooldownTicks = this.sgFirework.add(new IntSetting.Builder()
        .name("cooldown-ticks")
        .description("Cooldown after using a firework in ticks.")
        .defaultValue(10)
        .sliderRange(0, 100)
        .visible(autoFirework::get)
        .build()
    );

    private boolean pitchingDown = true;
    private boolean goingUp = true;
    private int fireworkCooldown = 0;

    public Pitch40Classic() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "pitch40-classic", "Oscillation elytra fixe +40/-40, standalone (n'utilise PAS ElytraFly de Meteor). Firework logique JEFF Pitch40Util.");
    }

    /**
     * Returns the upper pitch clamp for the current mode.
     * -40.0 in CLASSIC mode, -54.77 in EFFICIENT (drift) mode.
     * Exposed so other modules (ElytraAutoFly) can detect the "going up" apex
     * without hardcoding the value.
     */
    public float getUpClamp() {
        return this.pitchMode.get() == PitchMode.EFFICIENT ? -54.77f : -40.0f;
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null) {
            this.pitchingDown = this.mc.player.getY() >= this.upperBound.get();
        }
        this.goingUp = true;
        this.fireworkCooldown = 0;
    }

    @Override
    public void onDeactivate() {
        if (this.mc.player != null) {
            this.mc.player.setPitch(0.0f);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!this.isActive()) return;
        if (this.mc.player == null || !this.mc.player.isGliding()) return;

        double y = this.mc.player.getY();

        // Cooldown
        if (this.fireworkCooldown > 0) {
            this.fireworkCooldown--;
        }

        // Bound switching
        if (this.pitchingDown && y <= this.lowerBound.get()) {
            this.pitchingDown = false;
        } else if (!this.pitchingDown && y >= this.upperBound.get()) {
            this.pitchingDown = true;
        }

        // Pitch control (mode choisi: CLASSIC ou EFFICIENT)
        float current = this.mc.player.getPitch();
        float rate = this.pitchRate.get().floatValue();
        float downClamp = this.pitchMode.get() == PitchMode.EFFICIENT ? 37.72f : 40.0f;
        float upClamp = this.pitchMode.get() == PitchMode.EFFICIENT ? -54.77f : -40.0f;
        float updated = this.pitchingDown
            ? Math.min(current + rate, downClamp)
            : Math.max(current - rate, upClamp);
        this.mc.player.setPitch(updated);

        // Auto-firework (logique exacte de JEFF Pitch40Util, adaptée au mode)
        if (current == upClamp) {
            // upClamp pitch = facing up (-40° CLASSIC, -54.77° EFFICIENT)
            this.goingUp = true;
            if (this.autoFirework.get()
                && this.mc.player.getVelocity().y < this.velocityThreshold.get()
                && y < this.upperBound.get()) {
                if (this.fireworkCooldown == 0) {
                    int launchStatus = Utils.firework(this.mc, false);
                    if (launchStatus >= 0) {
                        this.fireworkCooldown = this.fireworkCooldownTicks.get();
                    }
                }
            }
        }
    }

    public enum PitchMode {
        CLASSIC("Classic (±40°, Meteor 0.5.8, prédictible)"),
        EFFICIENT("Efficient (37.72°/-54.77°, Meteor 1.21.11, drift up)");

        private final String displayName;

        PitchMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}