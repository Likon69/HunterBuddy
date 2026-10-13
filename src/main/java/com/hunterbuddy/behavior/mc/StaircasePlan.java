package com.hunterbuddy.behavior.mc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class StaircasePlan {
    private StaircasePlan() {}

    public static List<BlockPos> choose(MineSiteContext mine) {
        BlockPos start = BlockPos.fromLong(mine.facts.stairs.getLast());
        int level = Math.min(start.getY(), Math.min(0, mine.site.y + 1));
        int count = Math.abs(start.getY() - level);
        if (count == 0) return List.of();
        int rise = Integer.compare(level, start.getY());
        if (mine.facts.stairSpan < 8) {
            int bottom = mine.facts.supports.stream().map(BlockPos::fromLong).mapToInt(BlockPos::getY).min().orElse(start.getY());
            mine.facts.stairSpan = Math.max(8, (start.getY() - bottom + 7) / 4);
        }
        int initial = mine.facts.stairDirection < 0 ? toward(start, mine.site.seed()).getHorizontalQuarterTurns()
            : mine.facts.stairDirection;
        for (int attempt = 0; attempt < 4; attempt++) {
            List<BlockPos> steps = new ArrayList<>();
            BlockPos feet = start;
            boolean valid = true;
            for (int index = 0; index < count; index++) {
                int turn = (mine.facts.stairs.size() - 1 + index) / mine.facts.stairSpan;
                Direction direction = Direction.fromHorizontalQuarterTurns((initial + attempt + turn) & 3);
                feet = feet.offset(direction).up(rise);
                if (!mine.inside(feet) || !mine.inside(feet.up(2)) || !MineGeometry.floor(mine.world, feet.down())) {
                    valid = false;
                    break;
                }
                for (int head = 0; head < 3; head++) {
                    BlockPos point = feet.up(head);
                    if (!mine.world.loaded(point) || mine.facts.supports.contains(point.asLong())
                        || mine.world.hardness(point) < 0 || mine.world.client().world.getBlockEntity(point) != null) valid = false;
                    if (mine.world.loaded(point) && mine.world.state(point).getFluidState().isIn(FluidTags.LAVA)) valid = false;
                }
                steps.add(feet);
                if (!valid) break;
            }
            if (valid) {
                mine.facts.stairDirection = (initial + attempt) & 3;
                return steps;
            }
        }
        throw new IllegalStateException("no supported staircase fits the zone without destroying existing return steps");
    }

    public static Direction toward(BlockPos from, BlockPos target) {
        int dx = target.getX() - from.getX();
        int dz = target.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx < 0 ? Direction.WEST : Direction.EAST;
        return dz < 0 ? Direction.NORTH : Direction.SOUTH;
    }
}
