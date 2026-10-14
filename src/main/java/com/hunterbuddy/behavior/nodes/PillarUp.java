package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class PillarUp extends Action {
    private final ToDoubleFunction<TickContext> targetY;
    private final WorldReader world;
    private final Outputs outputs;
    private final Predicate<ItemStack> block;
    private BlockPos base;
    private double provenY;
    private Stage stage;

    public PillarUp(double targetY, WorldReader world, Outputs outputs) {
        this(ctx -> targetY, world, outputs);
        if (!Double.isFinite(targetY)) throw new IllegalArgumentException("invalid pillar height");
    }

    public PillarUp(ToDoubleFunction<TickContext> targetY, WorldReader world, Outputs outputs) {
        this(targetY, world, outputs, outputs.placement()::pillarItem);
    }

    public PillarUp(double targetY, WorldReader world, Outputs outputs, Predicate<ItemStack> block) {
        this(ctx -> targetY, world, outputs, block);
        if (!Double.isFinite(targetY)) throw new IllegalArgumentException("invalid pillar height");
    }

    private PillarUp(ToDoubleFunction<TickContext> targetY, WorldReader world, Outputs outputs, Predicate<ItemStack> block) {
        super("pillar-up");
        this.targetY = Objects.requireNonNull(targetY);
        this.world = Objects.requireNonNull(world);
        this.outputs = Objects.requireNonNull(outputs);
        this.block = Objects.requireNonNull(block);
        limit(1_800);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        base = null;
        provenY = ctx.sensors().y();
        stage = Stage.READY;
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        double target = targetY.applyAsDouble(ctx);
        if (!Double.isFinite(target)) return fail(FailureCode.NO_OPTION, "pillar target is missing");
        var sensors = ctx.sensors();
        if (!world.available() || !world.inBounds(BlockPos.ofFloored(sensors.x(), target, sensors.z()))) {
            return fail(FailureCode.UNREACHABLE, "pillar target outside world");
        }
        if (!world.loaded(world.feet())) return fail(FailureCode.UNREACHABLE, "pillar chunk is not loaded");
        if (sensors.inWater() || sensors.submerged()) {
            outputs.look(ctx.owner(), new LookIntent(sensors.yaw(), -90));
            outputs.movement(ctx.owner(), new MovementIntent(true, false, false, false, true, false, sensors.food() > 6));
            if (sensors.y() >= target && ctx.monitor().samplesValid(ctx.tick())) return Success.INSTANCE;
            return new Running("swimming up instead of placing in water");
        }
        if (stage == Stage.CONFIRMING) {
            if (!outputs.placement().confirmed(base)) return new Running("waiting for server pillar confirmation");
            if (!sensors.onGround()) return new Running("waiting to land on confirmed pillar block");
            if (sensors.y() - provenY >= 1 && ctx.progress(new ProgressEvidence(EvidenceKind.GOAL_BEST, sensors.y() - provenY, "pillar landed higher"))) {
                provenY = sensors.y();
            }
            stage = Stage.READY;
        }
        if (stage == Stage.READY && sensors.y() >= target && sensors.onGround() && ctx.monitor().samplesValid(ctx.tick())) return Success.INSTANCE;
        if (world.findSlot(block) < 0) return fail(FailureCode.MISSING_ITEM, "pillar reserve=25; no usable throwaway block");
        if (stage == Stage.READY && sensors.onGround()) {
            base = world.feet();
            if (!world.state(base).isReplaceable()) return fail(FailureCode.REFUSED, "pillar starts from a full block surface");
            stage = Stage.JUMPING;
        }
        if (base == null) return new Running("waiting to start pillar jump from ground");
        if (!world.cleared(base.up()) || !world.cleared(base.up(2))) return fail(FailureCode.UNREACHABLE, "pillar headroom is blocked");
        outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, true, false, false));
        if (sensors.y() < base.getY() + 1 || !world.state(base).isReplaceable()) return new Running("pillar jump above placement");
        NodeResult placed = outputs.placement().place(ctx, base, block, false);
        if (placed instanceof Success) stage = Stage.CONFIRMING;
        return placed instanceof Success ? new Running("pillar block emitted; waiting for landing") : placed;
    }

    private enum Stage {
        READY, JUMPING, CONFIRMING
    }
}
