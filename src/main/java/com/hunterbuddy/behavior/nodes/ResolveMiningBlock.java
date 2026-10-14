package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import net.minecraft.util.math.BlockPos;

public final class ResolveMiningBlock extends Decorator {
    private final MineSiteContext mine;
    private final BlockPos point;

    public ResolveMiningBlock(MineSiteContext mine, BlockPos point, Node child) {
        super("close-mining-block-after-confirmation-or-search", child);
        this.mine = mine;
        this.point = point;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (mine.facts.pending == null && mine.world.loaded(point) && mine.world.cleared(point)) return Success.INSTANCE;
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Failure failure) || mine.facts.pending == null
            || mine.facts.pending != point.asLong()) return result;
        if (failure.code() != FailureCode.TIMEOUT && failure.code() != FailureCode.NOT_CONFIRMED
            && failure.code() != FailureCode.MISSING_ITEM && failure.code() != FailureCode.UNREACHABLE
            && failure.code() != FailureCode.REFUSED && failure.code() != FailureCode.GUARD_LOST) return result;
        if (!mine.world.loaded(point)) {
            if (mine.facts.broken) {
                mine.loseBlock(ctx, "mining drop lost after its origin could not be reached at " + point.toShortString());
                return Success.INSTANCE;
            }
            mine.rejected.add(point);
            mine.closeBlock(ctx, "unconfirmed mining target rejected after its chunk became unreachable at " + point.toShortString());
            return fail(FailureCode.UNREACHABLE, "unconfirmed mining cell could not be loaded");
        }
        boolean present = !mine.world.cleared(point);
        if (!present && !mine.facts.broken && mine.outputs.mining().confirmed(point)) {
            mine.confirmBlock(ctx, point);
            child().resetMemory();
            return new Running("late server break receipt confirmed; search drops before closing");
        }
        boolean broken = mine.facts.broken;
        if (!present && broken) {
            mine.loseBlock(ctx, "mining drop lost after search at " + point.toShortString());
            mine.patch.remove(point);
            return Success.INSTANCE;
        }
        mine.closeBlock(ctx, present ? "mining target rejected: block remains at " + point.toShortString()
            : "unconfirmed empty mining cell skipped at " + point.toShortString());
        if (present) {
            mine.rejected.add(point);
            return fail(FailureCode.UNREACHABLE, "mining target remains after the break attempt");
        }
        mine.patch.remove(point);
        return Success.INSTANCE;
    }
}
