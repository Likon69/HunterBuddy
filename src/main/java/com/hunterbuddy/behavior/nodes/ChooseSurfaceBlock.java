package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.mc.SurfaceMineSelection;
import java.util.ArrayDeque;
import net.minecraft.util.math.BlockPos;

public final class ChooseSurfaceBlock extends Action {
    private final MineSiteContext mine;
    private final ArrayDeque<BlockPos> columns = new ArrayDeque<>();

    public ChooseSurfaceBlock(MineSiteContext mine) {
        super("choose-highest-safe-connected-surface-slice");
        this.mine = mine;
        limit(2400);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        columns.clear();
        if (!mine.surfaceInitialized) {
            columns.addAll(mine.patch);
            mine.surfaceInitialized = true;
        } else columns.addAll(mine.surfaceColumns);
        mine.surfaceColumns.clear();
    }

    @Override
    protected void onActionStop(TickContext ctx, ExitReason reason) {
        if (reason == ExitReason.PREEMPTED || reason == ExitReason.PAUSED || reason == ExitReason.GUARD_LOST) {
            mine.surfaceColumns.addAll(columns);
        }
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        for (int work = 0; work < 256 && !columns.isEmpty(); work++) {
            BlockPos point = columns.removeFirst().up();
            if (mine.material(point) && mine.patch.add(point)) columns.addLast(point);
        }
        if (!columns.isEmpty()) return new Running("checking connected blocks above the surface slice");
        if (!SurfaceMineSelection.choose(mine)) {
            boolean unknown = mine.facts.unexplored || mine.patch.stream().anyMatch(point -> !mine.world.loaded(point));
            boolean remaining = mine.patch.stream().anyMatch(point -> mine.material(point)
                && !mine.facts.supports.contains(point.asLong()));
            mine.facts.siteState = unknown || remaining ? "inaccessible" : "exhausted";
            mine.facts.outcome = remaining || unknown ? "upper layer inaccessible with a safe exit"
                : "mineable blocks exhausted; exit steps preserved";
            return Success.INSTANCE;
        }
        return Success.INSTANCE;
    }
}
