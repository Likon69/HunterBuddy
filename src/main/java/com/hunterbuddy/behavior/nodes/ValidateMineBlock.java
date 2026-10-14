package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.MineGeometry;
import com.hunterbuddy.behavior.mc.MineInventorySafety;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class ValidateMineBlock extends Node {
    private final MineSiteContext mine;
    private final BlockPos point;
    private final boolean material;

    public ValidateMineBlock(MineSiteContext mine, BlockPos point, boolean material) {
        super("validate-zone-support-gravity-and-inventory-before-break");
        this.mine = mine;
        this.point = point;
        this.material = material;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        mine.zone = mine.zoneSource.get();
        if (!mine.inside(point) || !mine.inside(mine.world.feet()) || !mine.world.loaded(point)) {
            return fail(FailureCode.REFUSED, "mining target or player outside the zone margin or loaded world");
        }
        if (MineGeometry.lavaAdjacent(mine.world, point)) {
            return fail(FailureCode.UNREACHABLE, "lava occupies or borders the excavation cell");
        }
        if (MineGeometry.clear(mine.world, point)) {
            if (mine.facts.pending != null && mine.facts.pending == point.asLong() && mine.outputs.mining().confirmed(point)) {
                return Success.INSTANCE;
            }
            return fail(FailureCode.NOT_CONFIRMED, "unconfirmed target already cleared");
        }
        if (material && !mine.material(point)) return fail(FailureCode.REFUSED, "target no longer belongs to the profile");
        if (mine.facts.supports.contains(point.asLong()) || point.equals(mine.world.feet().down())) {
            return fail(FailureCode.REFUSED, "mining would remove a protected return step or the player's support");
        }
        if (MineGeometry.fallingAbove(mine.world, point)
            || !mine.world.client().world.getEntitiesByClass(FallingBlockEntity.class, new Box(point).expand(2), e -> true).isEmpty()) {
            return fail(FailureCode.UNREACHABLE, "unstable falling blocks above the current slice");
        }
        if (mine.world.client().world.getBlockEntity(point) != null || mine.world.hardness(point) < 0
            || mine.outputs.mining().protectedBlock(point)) return fail(FailureCode.REFUSED, "unbreakable, container or protected target");
        if (!MineInventorySafety.room(mine, mine.world.state(point).getBlock())) {
            mine.facts.storageNeeded = true;
            mine.facts.outcome = "storage needed to preserve the free inventory slot";
            return fail(FailureCode.BUDGET, mine.facts.outcome);
        }
        if (mine.world.countMatching(stack -> LogisticsInventory.tool(stack, mine.profile) && LogisticsInventory.healthy(stack)) == 0) {
            mine.facts.outcome = "regear required before continuing";
            return fail(FailureCode.BUDGET, mine.facts.outcome);
        }
        return Success.INSTANCE;
    }
}
