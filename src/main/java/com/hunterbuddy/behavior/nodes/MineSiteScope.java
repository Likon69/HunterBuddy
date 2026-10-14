package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import net.minecraft.util.math.BlockPos;

public final class MineSiteScope extends Decorator {
    private final MineSiteContext mine;

    public MineSiteScope(MineSiteContext mine, Node child) {
        super("mining-in-original-world", child);
        this.mine = mine;
    }

    @Override
    protected void onStart(TickContext ctx) {
        activation().defer(mine.outputs.mining()::releaseTool);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (mine.facts != null && !mine.facts.world.equals(mine.world.storageKey())) {
            mine.failure = "mining belongs to another world or dimension";
            return fail(FailureCode.REFUSED, mine.failure);
        }
        try {
            mine.zone = mine.recordedZone == null ? mine.zoneSource.get() : mine.recordedZone;
        } catch (IllegalStateException ex) {
            mine.zone = null;
            mine.failure = "invalid mining zone: " + ex.getMessage();
            return fail(FailureCode.REFUSED, mine.failure);
        }
        NodeResult result = child().execute(ctx);
        if (result instanceof Failure failure) mine.failure = failure.reason();
        return result;
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        mine.outputs.inventory().observePlayer();
        if (mine.facts == null || mine.facts.pending == null) return;
        mine.facts.receiptVersion = mine.outputs.inventory().playerVersion() - 1;
        BlockPos point = BlockPos.fromLong(mine.facts.pending);
        if (!mine.facts.broken && mine.world.loaded(point) && mine.world.cleared(point) && !mine.outputs.mining().confirmed(point)) {
            mine.patch.remove(point);
            mine.closeBlock(ctx, "resumed empty mining cell skipped without a server receipt at " + point.toShortString());
        }
    }

}
