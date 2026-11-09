package com.hunterbuddy.modules.scaffold;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class NormalTarget {
    private static final long HOLD_MS = 150;
    private final ScaffoldSettings settings;
    private PlacementTarget target;
    private long expires;
    private boolean pending;

    NormalTarget(ScaffoldSettings settings) {
        this.settings = settings;
    }

    void reset() {
        target = null;
        expires = 0;
        pending = false;
    }

    void sent(long now) {
        pending = true;
        expires = now + HOLD_MS;
    }

    PlacementTarget find(long now) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        Vec3d eye = player.getEyePos();
        double reach = SupportHit.reach(settings);
        if (target != null && now < expires && SupportHit.usable(target.position())
            && (!target.hit().isInsideBlock() || settings.airPlace.get()) && SupportHit.visible(target.hit(), eye, reach)) {
            return pending ? null : target;
        }
        reset();
        if (settings.airPlace.get()) {
            BlockPos position = underFeet(player.getEntityPos());
            if (!SupportHit.usable(position)) return null;
            BlockHitResult hit = SupportHit.air(position, eye, reach);
            if (hit == null) return null;
            target = new PlacementTarget(position, hit);
            expires = now + HOLD_MS;
            return target;
        }
        ScaffoldPath path = ScaffoldPath.create(player.getEntityPos(), player.getVelocity(),
            MovementKeys.direction(mc.options, player.getYaw()), settings.velocityPredict.get());
        target = search(path, eye, reach, false);
        if (target == null) target = search(path, eye, reach, true);
        expires = now + HOLD_MS;
        return target;
    }

    private PlacementTarget search(ScaffoldPath path, Vec3d eye, double reach, boolean extend) {
        BlockPos previous = null;
        for (int step = 0; step <= settings.lookahead.get() * 2; step++) {
            BlockPos position = path.at(step * 0.5);
            if (position.equals(previous)) continue;
            previous = position;
            if (extend) {
                PlacementTarget bridge = bridge(position, eye, reach);
                if (bridge != null) return bridge;
            } else if (SupportHit.usable(position)) {
                BlockHitResult hit = SupportHit.find(position, eye, reach);
                if (hit != null) return new PlacementTarget(position, hit);
            }
        }
        return null;
    }

    private PlacementTarget bridge(BlockPos position, Vec3d eye, double reach) {
        if (!SupportHit.usable(position)) return null;
        PlacementTarget best = null;
        double nearest = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            if (side == Direction.UP) continue;
            BlockPos candidate = position.offset(side);
            if (!SupportHit.usable(candidate)) continue;
            BlockHitResult hit = SupportHit.find(candidate, eye, reach);
            if (hit == null) continue;
            double distance = hit.getPos().squaredDistanceTo(eye);
            if (distance < nearest) {
                nearest = distance;
                best = new PlacementTarget(candidate, hit);
            }
        }
        return best;
    }

    static BlockPos underFeet(Vec3d feet) {
        return BlockPos.ofFloored(feet).down();
    }
}
