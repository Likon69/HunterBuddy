package com.hunterbuddy.behavior.mc;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class GallerySelection {
    private GallerySelection() {}

    public static boolean choose(MineSiteContext mine) {
        if (mine.facts.gallery.isEmpty()) return at(mine, BlockPos.fromLong(mine.facts.stairs.getLast()));
        for (int index = mine.facts.gallery.size() - 1; index >= 0; index--) {
            if (at(mine, BlockPos.fromLong(mine.facts.gallery.get(index)))) return true;
        }
        return at(mine, BlockPos.fromLong(mine.facts.stairs.getLast()));
    }

    private static boolean at(MineSiteContext mine, BlockPos start) {
        Direction axis = StaircasePlan.toward(BlockPos.fromLong(mine.facts.entrance), mine.site.seed());
        int index = mine.facts.gallery.size();
        int phase = index % 28;
        int turn = phase < 12 ? 0 : phase < 14 ? 1 : phase < 26 ? 2 : 1;
        int heading = (axis.getHorizontalQuarterTurns() + turn) & 3;
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos next = start.offset(Direction.fromHorizontalQuarterTurns((heading + attempt) & 3));
            if (!mine.inside(next) || mine.rejected.contains(next) || mine.facts.gallery.contains(next.asLong())
                || mine.facts.stairs.contains(next.asLong())
                || !MineGeometry.floor(mine.world, next.down()) || !connected(mine, next)) continue;
            if (mine.facts.supports.contains(next.asLong()) || mine.facts.supports.contains(next.up().asLong())) continue;
            if (!mine.world.loaded(next.up(2)) || MineGeometry.fallingAbove(mine.world, next.up())) continue;
            mine.stand = start;
            mine.nextFeet = next;
            return true;
        }
        return false;
    }

    private static boolean connected(MineSiteContext mine, BlockPos next) {
        for (BlockPos point : new BlockPos[] {next.down(), next, next.up()}) {
            if (!mine.material(point)) continue;
            if (mine.patch.contains(point)) return true;
            for (Direction direction : Direction.values()) {
                if (mine.patch.contains(point.offset(direction)) && mine.material(point.offset(direction))) {
                    mine.patch.add(point);
                    return true;
                }
            }
        }
        return false;
    }
}
