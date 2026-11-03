package com.hunterbuddy.modules.scaffold;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class NormalPlacer {
    private static final int ROTATION_PRIORITY = 50;

    private final ScaffoldSettings settings;
    private final NormalTarget target;
    private final PlacedBlocks placed;
    private final PlaceDelay delay;

    NormalPlacer(ScaffoldSettings settings, PlacedBlocks placed, PlaceDelay delay) {
        this.settings = settings;
        this.target = new NormalTarget(settings);
        this.placed = placed;
        this.delay = delay;
    }

    void reset() {
        target.reset();
    }

    void tick() {
        delay.advance();
        if (!delay.reached(settings.placeDelay.get())) return;
        BlockPos position = target.find();
        if (position == null || NormalTarget.outsideBuildLimits(position)) return;
        if (attemptPlacement(position)) {
            target.rememberIfSolid(position);
            tower();
        }
    }

    private boolean attemptPlacement(BlockPos position) {
        FindItemResult block = ScaffoldBlocks.forTarget(position, settings);
        if (!block.found() || !ScaffoldHand.ready(block, settings.autoSwitch.get())) return false;
        if (BlockUtils.place(position, block, settings.rotate.get(), ROTATION_PRIORITY, true, true)) {
            placed.add(position);
            delay.reset();
        }
        return true;
    }

    private void tower() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean climbing = mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed();
        if (!settings.tower.get() || !climbing || MovementKeys.anyPressed(mc.options)) return;
        ClientPlayerEntity player = mc.player;
        Vec3d velocity = player.getVelocity();
        player.setVelocity(velocity.x, settings.towerSpeed.get(), velocity.z);
    }
}
