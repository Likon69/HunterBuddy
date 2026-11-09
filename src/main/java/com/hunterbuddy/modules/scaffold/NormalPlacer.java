package com.hunterbuddy.modules.scaffold;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

final class NormalPlacer {
    private final ScaffoldSettings settings;
    private final NormalTarget target;
    private final ScaffoldPlacement placement;
    private final PlaceDelay delay;

    NormalPlacer(ScaffoldSettings settings, ScaffoldPlacement placement, PlaceDelay delay) {
        this.settings = settings;
        this.target = new NormalTarget(settings);
        this.placement = placement;
        this.delay = delay;
    }

    void reset() {
        target.reset();
    }

    void tick() {
        if (!delay.reached(settings.placeDelay.get())) return;
        long now = System.currentTimeMillis();
        PlacementTarget candidate = target.find(now);
        if (candidate == null) return;
        FindItemResult block = ScaffoldBlocks.forTarget(candidate.position(), settings, candidate.hit().isInsideBlock());
        placement.place(candidate, block, () -> {
            target.sent(System.currentTimeMillis());
            tower();
        });
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
