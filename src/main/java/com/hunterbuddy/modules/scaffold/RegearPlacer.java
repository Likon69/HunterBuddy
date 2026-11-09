package com.hunterbuddy.modules.scaffold;

import java.util.List;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class RegearPlacer {
    private static final double FAST_FALL = 0.45;
    private static final double MEDIUM_FALL = 0.25;
    private final ScaffoldSettings settings;
    private final RegearMode mode;
    private final RegearTargets targets;
    private final ScaffoldPlacement placement;
    private final PlaceDelay delay;

    RegearPlacer(ScaffoldSettings settings, RegearMode mode, ScaffoldPlacement placement, PlaceDelay delay) {
        this.settings = settings;
        this.mode = mode;
        this.targets = new RegearTargets(settings);
        this.placement = placement;
        this.delay = delay;
    }

    void tick() {
        if (!delay.reached(settings.placeDelay.get())) return;
        List<BlockPos> candidates = targets.candidates(SupportHit.reach(settings));
        int done = 0;
        for (BlockPos position : candidates) {
            if (done >= quota() || !delay.reached(settings.placeDelay.get())) return;
            Vec3d eye = MinecraftClient.getInstance().player.getEyePos();
            double reach = SupportHit.reach(settings);
            BlockHitResult hit = SupportHit.find(position, eye, reach);
            if (hit == null && settings.airPlace.get()) hit = SupportHit.air(position, eye, reach);
            if (hit == null) continue;
            FindItemResult block = ScaffoldBlocks.forRegear(mode, hit.isInsideBlock());
            if (placement.place(new PlacementTarget(position, hit), block, null)) done++;
        }
    }

    private int quota() {
        double fall = Math.abs(MinecraftClient.getInstance().player.getVelocity().y);
        if (fall > FAST_FALL) return 3;
        return fall > MEDIUM_FALL ? 2 : 1;
    }
}
