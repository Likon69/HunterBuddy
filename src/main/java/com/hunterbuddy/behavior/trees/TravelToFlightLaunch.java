package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.mc.FlightPlan;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class TravelToFlightLaunch extends Node {
    private final FlightPlan plan;
    private final Key<BlockPos> chosen;
    private Node travel;

    public TravelToFlightLaunch(FlightPlan plan, Key<BlockPos> chosen) {
        super("baritone-to-flight-launch");
        this.plan = plan;
        this.chosen = chosen;
    }

    @Override
    protected void onStart(TickContext ctx) {
        BlockPos pos = ctx.bb().get(ctx.owner(), chosen).orElseThrow();
        travel = GroundTravel.build(pos, 0, plan.baritone, plan.outputs);
        TreeValidator.validate(travel);
        TreeValidator.bind(travel, path() + "/ground-travel", ctx.owner(), hook());
    }

    @Override
    public List<Node> children() {
        return travel == null ? List.of() : List.of(travel);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return travel.execute(ctx);
    }
}
