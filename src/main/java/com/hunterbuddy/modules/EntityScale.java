package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Purely visual rescaling of living entities. No outline, no glow, no ESP —
 * the model is drawn bigger or smaller and nothing else changes: hitboxes,
 * reach and everything the server sees are untouched.
 *
 * <p>The work happens in {@code EntityScaleMixin}, which multiplies the render
 * state's {@code baseScale}. Vanilla applies that field itself, so no mob type
 * can slip past.
 */
public class EntityScale extends Module {
    private final SettingGroup sgMobs   = settings.getDefaultGroup();
    private final SettingGroup sgPlayer = settings.createGroup("Players");

    private final Setting<Double> mobScale = sgMobs.add(new DoubleSetting.Builder()
        .name("mob-scale")
        .description("Visual scale applied to every mob. 1.0 is normal size.")
        .defaultValue(1.0).min(0.05).sliderRange(0.05, 5.0)
        .build()
    );

    private final Setting<Double> playerScale = sgPlayer.add(new DoubleSetting.Builder()
        .name("player-scale")
        .description("Visual scale applied to your own model. Third person only — your hands are unaffected.")
        .defaultValue(1.0).min(0.05).sliderRange(0.05, 5.0)
        .build()
    );

    private final Setting<Boolean> scaleOtherPlayers = sgPlayer.add(new BoolSetting.Builder()
        .name("scale-other-players")
        .description("Also rescale other players.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> otherPlayerScale = sgPlayer.add(new DoubleSetting.Builder()
        .name("other-player-scale")
        .description("Visual scale applied to other players.")
        .defaultValue(1.0).min(0.05).sliderRange(0.05, 5.0)
        .visible(scaleOtherPlayers::get)
        .build()
    );

    public EntityScale() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "entity-scale",
            "Visually shrinks or enlarges mobs and players.");
    }

    /** Scale for anything that is not a player. */
    public float getMobScale() {
        return mobScale.get().floatValue();
    }

    /** Scale for a player — your own model when {@code self}, otherwise another player's. */
    public float getPlayerScale(boolean self) {
        if (self) return playerScale.get().floatValue();
        return scaleOtherPlayers.get() ? otherPlayerScale.get().floatValue() : 1.0f;
    }
}
