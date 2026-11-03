package com.hunterbuddy.modules.scaffold;

import com.hunterbuddy.modules.regear.util.PlacementUtils;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import java.util.List;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class RegearPlacer {
    private static final double REACH = 4.5;
    private static final double FAST_FALL = 0.45;
    private static final double MEDIUM_FALL = 0.25;

    private final ScaffoldSettings settings;
    private final RegearMode mode;
    private final RegearTargets targets;
    private final PlacedBlocks placed;
    private final PlaceDelay delay;

    RegearPlacer(ScaffoldSettings settings, RegearMode mode, PlacedBlocks placed, PlaceDelay delay) {
        this.settings = settings;
        this.mode = mode;
        this.targets = new RegearTargets(settings);
        this.placed = placed;
        this.delay = delay;
    }

    void tick() {
        FindItemResult block = ScaffoldBlocks.forRegear(mode);
        if (!block.found() || !ScaffoldHand.ready(block, settings.autoSwitch.get())) return;
        List<BlockPos> candidates = targets.candidates(REACH);
        if (!candidates.isEmpty()) placeUpTo(candidates, quota());
    }

    private void placeUpTo(List<BlockPos> candidates, int quota) {
        int done = 0;
        for (BlockPos position : candidates) {
            if (done >= quota) return;
            BlockHitResult hit = hitFor(position);
            if (hit == null) continue;
            place(position, hit);
            done++;
        }
    }

    private int quota() {
        double fall = Math.abs(MinecraftClient.getInstance().player.getVelocity().y);
        if (fall > FAST_FALL) return 3;
        return fall > MEDIUM_FALL ? 2 : 1;
    }

    private BlockHitResult hitFor(BlockPos position) {
        BlockHitResult hit = SupportHit.find(position, MinecraftClient.getInstance().player.getEyePos(), REACH);
        return hit != null ? hit : PlacementUtils.resolvePlaceHit(position, REACH);
    }

    private void place(BlockPos position, BlockHitResult hit) {
        Vec3d eye = MinecraftClient.getInstance().player.getEyePos();
        float[] angles = RotationUtils.getRotationsTo(eye, hit.getPos());
        RotationUtils.getInstance().setRotationFullInstant(angles[0], angles[1]);
        PlacementUtils.placeHit(hit, Hand.MAIN_HAND, true);
        placed.add(position);
        delay.reset();
    }
}
