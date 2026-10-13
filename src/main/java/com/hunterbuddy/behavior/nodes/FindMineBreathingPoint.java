package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineGeometry;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.util.math.BlockPos;

public final class FindMineBreathingPoint extends Node {
    private final MineSiteContext mine;

    public FindMineBreathingPoint(MineSiteContext mine) {
        super("find-air-on-the-confirmed-mine-access");
        this.mine = mine;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (mine.facts == null) return fail(FailureCode.NO_OPTION, "mine access not initialized");
        mine.breathingPoint = Stream.concat(mine.facts.stairs.stream(), mine.facts.gallery.stream())
            .map(BlockPos::fromLong).filter(point -> MineGeometry.standing(mine.world, point)
                && mine.world.state(point.up()).isAir())
            .min(Comparator.comparingDouble(point -> point.getSquaredDistance(mine.world.feet()))).orElse(null);
        if (mine.breathingPoint == null) return fail(FailureCode.NO_OPTION, "no air point on the carved access");
        return Success.INSTANCE;
    }
}
