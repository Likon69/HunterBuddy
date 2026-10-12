package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.Conditions;
import com.hunterbuddy.behavior.mc.MiningBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.SlotIntent;
import com.hunterbuddy.modules.mine.EnemyBlocks;
import java.util.Objects;
import net.minecraft.util.math.BlockPos;

public final class BreakBlock extends Action {
    private final Key<BlockPos> posKey;
    private final boolean feetBelow;
    private final WorldReader world;
    private final Outputs outputs;
    private BlockPos pos;
    private Stage stage;
    private int emptyTicks;
    private long queuedAt;
    private ExitReason lastExit;

    public BreakBlock(Key<BlockPos> posKey, boolean feetBelow, WorldReader world, Outputs outputs) {
        super("break-block");
        this.posKey = Objects.requireNonNull(posKey);
        this.feetBelow = feetBelow;
        this.world = Objects.requireNonNull(world);
        this.outputs = Objects.requireNonNull(outputs);
        limit(300);
    }

    @Override
    protected String target(TickContext ctx) {
        return ctx.bb().get(ctx.owner(), posKey).map(Object::toString).orElse("missing-block");
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        BlockPos next = ctx.bb().get(ctx.owner(), posKey).orElse(null);
        boolean resume = Objects.equals(pos, next) && (lastExit == ExitReason.PREEMPTED || lastExit == ExitReason.PAUSED
            || lastExit == ExitReason.GUARD_LOST);
        pos = next;
        if (!resume) {
            stage = Stage.READY;
            queuedAt = -40;
        }
        emptyTicks = 0;
        lastExit = null;
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (pos == null) return fail(FailureCode.NO_OPTION, "mining position is missing");
        if (!world.inBounds(pos) || !world.loaded(pos)) return fail(FailureCode.UNREACHABLE, "mining position outside loaded world");
        MiningBridge mining = outputs.mining();
        if (world.cleared(pos)) {
            mining.releaseClick();
            emptyTicks++;
            if (stage == Stage.VANILLA && emptyTicks < 2) return new Running("confirming vanilla air for 2 Pre ticks");
            if (stage != Stage.READY && !mining.confirmed(pos)) return new Running("waiting for server block confirmation");
            if (stage != Stage.READY && !ctx.progress(new ProgressEvidence(EvidenceKind.BLOCK_CONFIRMED, 1, "block=" + pos))) {
                return new Running("block confirmation rejected=setback");
            }
            return Success.INSTANCE;
        }
        emptyTicks = 0;
        String refusal = mining.refusal();
        if (refusal != null) return fail(FailureCode.REFUSED, refusal);
        if (world.hardness(pos) < 0 || mining.protectedBlock(pos)) return fail(FailureCode.REFUSED, "unbreakable or protected block=" + pos);
        if (feetBelow && ctx.sensors().y() >= pos.getY()) return fail(FailureCode.REFUSED, "feet must remain below the mined block");
        TargetPoint center = new TargetPoint(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        TargetPoint eyes = world.eyes();
        if (center.distance(eyes.x(), eyes.y(), eyes.z()) > 4.5) return fail(FailureCode.UNREACHABLE, "mining position is out of reach");
        if (!mining.canQueue(pos)) return new Running("HMine is finishing its engaged block");
        LookIntent desired = Conditions.towards(eyes, center);
        LookIntent look = new LookIntent(ctx.sensors().yaw() + Conditions.wrap(desired.yaw() - ctx.sensors().yaw()), desired.pitch());
        outputs.look(ctx.owner(), look);
        int tool = mining.bestTool(pos);
        if (tool >= 0) {
            outputs.slot(ctx.owner(), new SlotIntent(tool, true));
            if (ctx.sensors().selectedSlot() != tool || ctx.sensors().serverSlot() != tool || !outputs.slotSettled(tool)) {
                mining.releaseClick();
                return new Running("selecting mining tool");
            }
        }
        if (!Conditions.facing(ctx.sensors(), look)) {
            mining.releaseClick();
            return new Running("aligning visible mining look");
        }
        if (stage == Stage.READY) mining.prepare(ctx, pos);
        if (mining.vanilla(pos)) {
            if (mining.holdVanilla(ctx, pos)) stage = Stage.VANILLA;
        } else {
            mining.releaseClick();
            if (stage == Stage.READY || outputs.nativeTick() - queuedAt >= 40) {
                mining.queue(ctx, pos, EnemyBlocks.facing(pos));
                stage = Stage.PACKET;
                queuedAt = outputs.nativeTick();
            }
        }
        return new Running("mining block=" + pos);
    }

    @Override
    protected void onActionStop(TickContext ctx, ExitReason reason) {
        outputs.mining().releaseClick();
        lastExit = reason;
    }

    @Override
    protected void onReset() {
        pos = null;
        stage = Stage.READY;
        lastExit = null;
        queuedAt = -40;
        emptyTicks = 0;
    }

    private enum Stage {
        READY, PACKET, VANILLA
    }
}
