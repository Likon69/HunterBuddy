package com.hunterbuddy.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;

public class TimerPace {
    private final Setting<Double> multiplier;
    private final Setting<Boolean> enabled;
    private final Setting<Boolean> travelOnly;
    private final Setting<Double> travelKmh;
    private final Setting<Double> slowest;
    private final Setting<Double> fastest;
    private final Setting<Integer> scanRadius;
    private final Setting<Integer> tolerance;
    private final Setting<Double> easing;
    private final Setting<Integer> period;

    private double currentSpeed = 1.0;
    private int ticks;
    private Vec3d lastPos;
    private double measuredSpeed;

    public TimerPace(SettingGroup group, Setting<Double> multiplier) {
        this.multiplier = multiplier;

        enabled = group.add(new BoolSetting.Builder()
            .name("auto-adjust")
            .description("Lower the Timer on its own while chunks around you are still loading, then bring it back up.")
            .defaultValue(true)
            .build());

        travelOnly = group.add(new BoolSetting.Builder()
            .name("only-when-traveling")
            .description("Only pace the Timer while you move faster than travel-speed-threshold; slower than that it runs at 1.")
            .defaultValue(true)
            .visible(enabled::get)
            .build());

        travelKmh = group.add(new DoubleSetting.Builder()
            .name("travel-speed-threshold")
            .description("Horizontal speed in km/h above which you count as travelling.")
            .defaultValue(30.0)
            .min(1.0)
            .sliderRange(1.0, 100.0)
            .visible(() -> enabled.get() && travelOnly.get())
            .build());

        slowest = group.add(new DoubleSetting.Builder()
            .name("min-speed")
            .description("Slowest the Timer may go while many chunks are missing.")
            .defaultValue(0.2)
            .min(0.1)
            .sliderRange(0.1, 1.0)
            .visible(enabled::get)
            .build());

        fastest = group.add(new DoubleSetting.Builder()
            .name("max-speed")
            .description("Fastest the Timer may go once the chunks around you are loaded; 1 is normal speed.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderRange(0.1, 2.0)
            .visible(enabled::get)
            .build());

        scanRadius = group.add(new IntSetting.Builder()
            .name("check-radius")
            .description("How many chunks around you are checked in each direction.")
            .defaultValue(4)
            .min(1)
            .sliderRange(1, 8)
            .visible(enabled::get)
            .build());

        tolerance = group.add(new IntSetting.Builder()
            .name("unloaded-threshold")
            .description("Missing chunks tolerated before the Timer slows down.")
            .defaultValue(6)
            .min(1)
            .sliderRange(1, 20)
            .visible(enabled::get)
            .build());

        easing = group.add(new DoubleSetting.Builder()
            .name("adjust-speed")
            .description("Share of the gap to the wanted speed closed at each check; higher reacts faster.")
            .defaultValue(0.15)
            .min(0.01)
            .sliderRange(0.01, 1.0)
            .visible(enabled::get)
            .build());

        period = group.add(new IntSetting.Builder()
            .name("check-interval")
            .description("Ticks between two checks.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 40)
            .visible(enabled::get)
            .build());
    }

    public void reset() {
        currentSpeed = multiplier.get();
        ticks = 0;
        lastPos = null;
        measuredSpeed = 0.0;
    }

    public void tick() {
        MinecraftClient mc = MeteorClient.mc;
        if (!enabled.get() || mc.player == null || mc.world == null) {
            return;
        }

        Vec3d pos = mc.player.getEntityPos();
        if (lastPos != null) {
            double dx = pos.x - lastPos.x;
            double dz = pos.z - lastPos.z;
            measuredSpeed = Math.sqrt(dx * dx + dz * dz) * 20.0 * 3.6;
        }
        lastPos = pos;

        ticks++;
        if (ticks < period.get()) {
            return;
        }
        ticks = 0;

        if (travelOnly.get() && measuredSpeed < travelKmh.get()) {
            currentSpeed = 1.0;
            multiplier.set(1.0);
            return;
        }

        int reach = scanRadius.get();
        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;
        int missing = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (mc.world.getChunkManager().getChunk(centerX + dx, centerZ + dz, ChunkStatus.FULL, false) == null) {
                    missing++;
                }
            }
        }

        double min = slowest.get();
        double max = fastest.get();
        int tolerated = tolerance.get();
        double wanted;
        if (missing > tolerated) {
            double severity = Math.min(1.0, missing / (2.0 * tolerated));
            wanted = min + (max - min) * (1.0 - severity);
        } else {
            wanted = max;
        }

        double gap = wanted - currentSpeed;
        if (Math.abs(gap) > 0.01) {
            currentSpeed += gap * easing.get();
            multiplier.set(Math.max(min, Math.min(max, currentSpeed)));
        }
    }
}
