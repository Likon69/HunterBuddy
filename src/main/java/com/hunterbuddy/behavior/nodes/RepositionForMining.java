package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.trees.GroundTravel;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class RepositionForMining extends Node {
    private final MineSiteContext mine;
    private final BlockPos point;
    private final BlockPos stand;
    private final Node mining;
    private final Node travel;

    public RepositionForMining(MineSiteContext mine, BlockPos point, BlockPos stand, Node mining) {
        super("restore-mining-reach-after-water-or-combat");
        this.mine = mine;
        this.point = point;
        this.stand = stand;
        this.mining = mining;
        travel = GroundTravel.build(stand, 0, mine.baritone, mine.outputs);
    }

    @Override
    public List<Node> children() {
        return List.of(travel, mining);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        boolean confirmed = mine.world.cleared(point) && mine.outputs.mining().confirmed(point);
        if (!confirmed && (mine.world.feet().getSquaredDistance(stand) > 4 || point.equals(mine.world.feet().down()))) {
            mining.halt(ctx, ExitReason.PREEMPTED);
            NodeResult result = travel.execute(ctx);
            if (!(result instanceof Success)) return result;
        }
        mine.outputs.mining().holdTool(point, 0);
        return mining.execute(ctx);
    }
}
