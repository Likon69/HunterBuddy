package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;

public class Pitch40Classic extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgFirework = this.settings.createGroup("Auto Firework");

    public final Setting<Double> lowerBound = this.sgGeneral.add(new DoubleSetting.Builder()
        .name("lower-bound")
        .description("Hauteur Y où on repart en pitch up.")
        .defaultValue(80.0)
        .min(-128.0)
        .sliderMax(360.0)
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

    public final Setting<Boolean> autoFirework = this.sgFirework.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Pop un feu d'artifice automatique quand la vitesse verticale est trop basse.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> minVerticalSpeed = this.sgFirework.add(new DoubleSetting.Builder()
        .name("min-vertical-speed")
        .description("Vitesse verticale minimum avant de pop un feu (blocks/sec).")
        .defaultValue(1.0)
        .min(0.0)
        .sliderMax(5.0)
        .build()
    );

    public final Setting<Integer> fireworkCooldown = this.sgFirework.add(new IntSetting.Builder()
        .name("firework-cooldown-ticks")
        .description("Cooldown en ticks entre les feux d'artifice.")
        .defaultValue(100)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private boolean pitchingDown = true;
    private double lastY = 0.0;
    private int fireworkCooldownCounter = 0;

    public Pitch40Classic() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "pitch40-classic", "Oscillation elytra fixe +40/-40, standalone (n'utilise PAS ElytraFly de Meteor). Auto-firework optionnel.");
    }

    @Override
    public void onActivate() {
        if (this.mc.player != null) {
            this.pitchingDown = this.mc.player.getY() >= this.upperBound.get();
            this.lastY = this.mc.player.getY();
        }
        this.fireworkCooldownCounter = 0;
    }

    @Override
    public void onDeactivate() {
        if (this.mc.player != null) {
            this.mc.player.setPitch(0.0f);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || !this.mc.player.isGliding()) return;

        double y = this.mc.player.getY();
        double verticalSpeed = y - this.lastY;
        this.lastY = y;

        if (this.fireworkCooldownCounter > 0) {
            this.fireworkCooldownCounter--;
        }

        // Bound switching
        if (this.pitchingDown && y <= this.lowerBound.get()) {
            this.pitchingDown = false;
        } else if (!this.pitchingDown && y >= this.upperBound.get()) {
            this.pitchingDown = true;
        }

        // Pitch control (fixed ±40°, no randomisation)
        float current = this.mc.player.getPitch();
        float rate = this.pitchRate.get().floatValue();
        float updated = this.pitchingDown
            ? Math.min(current + rate, 40.0f)
            : Math.max(current - rate, -40.0f);
        this.mc.player.setPitch(updated);

        // Auto-firework: if vertical speed is too low while pitching up, pop a firework
        if (this.autoFirework.get() && !this.pitchingDown && verticalSpeed < this.minVerticalSpeed.get()
            && this.fireworkCooldownCounter == 0) {
            this.popFirework();
        }
    }

    private void popFirework() {
        if (this.mc.player == null) return;
        FindItemResult result = InvUtils.findInHotbar(net.minecraft.item.Items.FIREWORK_ROCKET);
        if (!result.found()) return;

        if (result.isOffhand()) {
            this.mc.interactionManager.interactItem(this.mc.player, Hand.OFF_HAND);
            this.mc.player.swingHand(Hand.OFF_HAND);
        } else {
            InvUtils.swap(result.slot(), false);
            this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
            this.mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
        this.fireworkCooldownCounter = this.fireworkCooldown.get();
    }
}