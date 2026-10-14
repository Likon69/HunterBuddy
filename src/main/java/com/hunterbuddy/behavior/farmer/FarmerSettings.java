package com.hunterbuddy.behavior.farmer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import net.minecraft.util.math.BlockPos;

public final class FarmerSettings {
    public static final int ACTIVITY_TIMEOUT_TICKS = 144_000;
    public static final int MAX_STEPS = 8;
    private final List<StepSettings> steps = new ArrayList<>(MAX_STEPS);
    private Setting<String> zone;
    private Setting<BlockPos> emptyShulkersChest;
    private Setting<BlockPos> resupplyChest;
    private Setting<BlockPos> returnChest;
    private Setting<Integer> flightRadius;
    private Setting<Integer> playerWaitMinutes;
    private boolean registered;

    public void register(List<SettingGroup> stepGroups, SettingGroup baseGroup) {
        Objects.requireNonNull(stepGroups);
        Objects.requireNonNull(baseGroup);
        if (registered) throw new IllegalStateException("FarmerBuddy settings already registered");
        if (stepGroups.size() != MAX_STEPS) throw new IllegalArgumentException("expected " + MAX_STEPS + " step groups");
        registered = true;
        for (int i = 0; i < MAX_STEPS; i++) steps.add(new StepSettings(i + 1, stepGroups.get(i)));
        zone = baseGroup.add(new StringSetting.Builder()
            .name("zone").description("Common name of the four Xaero waypoints bounding the mission work zone.")
            .defaultValue("zone").build());
        emptyShulkersChest = baseGroup.add(new BlockPosSetting.Builder()
            .name("empty-shulkers-chest").description("Base chest holding empty shulkers FarmerBuddy picks up.")
            .defaultValue(BlockPos.ORIGIN).build());
        resupplyChest = baseGroup.add(new BlockPosSetting.Builder()
            .name("resupply-chest").description("Base chest holding the prepared reserve shulker and rockets.")
            .defaultValue(BlockPos.ORIGIN).build());
        returnChest = baseGroup.add(new BlockPosSetting.Builder()
            .name("return-chest").description("Base chest where the worn-out reserve and misc shulkers are dropped off.")
            .defaultValue(BlockPos.ORIGIN).build());
        flightRadius = baseGroup.add(new IntSetting.Builder()
            .name("flight-radius").description("Maximum distance in blocks between the base and the mission work zone.")
            .defaultValue(3_000).min(100).max(20_000).sliderRange(500, 6_000).build());
        playerWaitMinutes = baseGroup.add(new IntSetting.Builder()
            .name("player-wait-minutes").description("Minutes FarmerBuddy waits at base when another player occupies its zone.")
            .defaultValue(10).min(1).max(60).build());
    }

    public List<FarmerStepConfig> steps() {
        List<FarmerStepConfig> snapshot = new ArrayList<>(steps.size());
        for (StepSettings step : steps) snapshot.add(step.snapshot());
        return List.copyOf(snapshot);
    }

    public String zone() {
        return zone.get();
    }

    public BlockPos emptyShulkersChest() {
        return emptyShulkersChest.get();
    }

    public BlockPos resupplyChest() {
        return resupplyChest.get();
    }

    public BlockPos returnChest() {
        return returnChest.get();
    }

    public int flightRadius() {
        return flightRadius.get();
    }

    public long playerWaitMillis() {
        return playerWaitTicks() * 50L;
    }

    public int playerWaitTicks() {
        return playerWaitMinutes.get() * 1_200;
    }

    public String refusal(FarmerCatalog catalog) {
        if (catalog.error() != null) return catalog.error();
        if (zone().isBlank()) return "FarmerBuddy needs a work zone name";
        List<FarmerStepConfig> enabled = steps().stream().filter(FarmerStepConfig::enabled).toList();
        if (enabled.isEmpty()) return "FarmerBuddy needs at least one enabled step";
        for (FarmerStepConfig step : enabled) {
            if (catalog.profile(step.profile()).isEmpty()) return "Step " + step.index() + " has an unknown catalog profile: " + step.profile() + " (known: " + String.join(", ", catalog.keys()) + ")";
            if (step.deliveryChest().equals(BlockPos.ORIGIN)) return "Step " + step.index() + " needs a delivery chest";
        }
        if (emptyShulkersChest().equals(BlockPos.ORIGIN) || resupplyChest().equals(BlockPos.ORIGIN) || returnChest().equals(BlockPos.ORIGIN)) {
            return "FarmerBuddy needs its three base chests set";
        }
        return null;
    }

    private static final class StepSettings {
        private final int index;
        private final Setting<Boolean> enabled;
        private final Setting<String> hint;
        private final Setting<String> profile;
        private final Setting<Integer> shulkerTarget;
        private final Setting<BlockPos> deliveryChest;

        private StepSettings(int index, SettingGroup group) {
            this.index = index;
            enabled = group.add(new BoolSetting.Builder()
                .name("enabled").description("Enable step " + index + " of the mission.").defaultValue(false).build());
            hint = group.add(new StringSetting.Builder()
                .name("hint").description("Optional Xaero waypoint name indicating where to search for this step's material inside the work zone.")
                .defaultValue("").build());
            profile = group.add(new StringSetting.Builder()
                .name("profile").description("Catalog profile key mined for this step (see catalog.json).")
                .defaultValue("").build());
            shulkerTarget = group.add(new IntSetting.Builder()
                .name("shulker-target").description("Full shulkers to deliver before moving to the next step.")
                .defaultValue(1).min(1).max(64).build());
            deliveryChest = group.add(new BlockPosSetting.Builder()
                .name("delivery-chest").description("Chest where this step's full shulkers are dropped off.")
                .defaultValue(BlockPos.ORIGIN).build());
        }

        private FarmerStepConfig snapshot() {
            return new FarmerStepConfig(index, enabled.get(), hint.get(), profile.get(), shulkerTarget.get(), deliveryChest.get());
        }
    }
}
