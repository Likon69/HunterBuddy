package com.hunterbuddy.modules.scaffold;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class RegearTargets {
    private static final double PREDICTION_FACTOR = 3.0;
    private static final double VERTICAL_TRIGGER = 0.05;
    private static final double HORIZONTAL_TRIGGER_SQUARED = 0.0025;
    private static final double DEPTH_MARGIN = 1.5;
    private static final int MAX_DEPTH = 4;

    private final ScaffoldSettings settings;

    RegearTargets(ScaffoldSettings settings) {
        this.settings = settings;
    }

    List<BlockPos> candidates(double reach) {
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockPos base = baseBlock(mc.player, mc);
        int depth = depth(mc.player.getVelocity().y);
        List<BlockPos> result = new ArrayList<>();
        for (int offset = 0; offset < depth; offset++) {
            BlockPos candidate = base.down(offset);
            if (isUsable(candidate, mc, reach)) result.add(candidate);
        }
        return result;
    }

    private BlockPos baseBlock(ClientPlayerEntity player, MinecraftClient mc) {
        Vec3d predicted = extended(predicted(player), player, mc);
        return new BlockPos(MathHelper.floor(predicted.x), MathHelper.floor(predicted.y) - 1, MathHelper.floor(predicted.z));
    }

    private Vec3d predicted(ClientPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        double horizontalSquared = velocity.x * velocity.x + velocity.z * velocity.z;
        boolean moving = Math.abs(velocity.y) > VERTICAL_TRIGGER || horizontalSquared > HORIZONTAL_TRIGGER_SQUARED;
        if (!settings.velocityPredict.get() || !moving) return player.getEntityPos();
        return player.getEntityPos().add(velocity.multiply(PREDICTION_FACTOR));
    }

    private Vec3d extended(Vec3d position, ClientPlayerEntity player, MinecraftClient mc) {
        double distance = settings.extendDistance.get();
        if (!MovementKeys.anyPressed(mc.options) || distance <= 0.0) return position;
        return position.add(MovementKeys.direction(mc.options, player.getYaw()).multiply(distance));
    }

    private int depth(double verticalVelocity) {
        if (!settings.velocityPredict.get()) return 1;
        int wanted = (int) Math.ceil(Math.abs(verticalVelocity) * PREDICTION_FACTOR * DEPTH_MARGIN) + 1;
        return MathHelper.clamp(wanted, 1, MAX_DEPTH);
    }

    private boolean isUsable(BlockPos candidate, MinecraftClient mc, double reach) {
        boolean inReach = Vec3d.ofCenter(candidate).distanceTo(mc.player.getEyePos()) <= reach;
        return inReach && mc.world.getBlockState(candidate).isReplaceable();
    }
}
