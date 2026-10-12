package com.hunterbuddy.behavior.probes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public final class FindBlocks extends Action {
    private final Key<BlockPos> originKey;
    private final Key<BlockScan> resultKey;
    private final int radius;
    private final int height;
    private final Predicate<BlockState> filter;
    private final WorldReader world;
    private final List<BlockPos> found = new ArrayList<>();
    private BlockPos origin;
    private BlockScan cached;
    private long cachedAt;
    private int index;

    public FindBlocks(Key<BlockPos> originKey, int radius, int height, Predicate<BlockState> filter, Key<BlockScan> resultKey, WorldReader world) {
        super("find-blocks");
        if (radius < 0 || radius > 16 || height < 1 || height > 64) throw new IllegalArgumentException("scan bounds exceeded");
        this.originKey = Objects.requireNonNull(originKey);
        this.resultKey = Objects.requireNonNull(resultKey);
        this.radius = radius;
        this.height = height;
        this.filter = Objects.requireNonNull(filter);
        this.world = Objects.requireNonNull(world);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        origin = ctx.bb().get(ctx.owner(), originKey).orElse(null);
        index = 0;
        found.clear();
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (origin == null) return fail(FailureCode.NO_OPTION, "block scan origin is missing");
        if (cached != null && cached.world().equals(world.identity()) && cached.origin().equals(origin) && ctx.tick() - cachedAt < 20) {
            ctx.bb().set(ctx.owner(), resultKey, cached);
            return Success.INSTANCE;
        }
        int width = radius * 2 + 1;
        int area = width * width;
        int total = area * height;
        for (int scanned = 0; scanned < 64 && index < total; scanned++, index++) {
            BlockPos pos = origin.add(index % width - radius, index / area, index / width % width - radius);
            if (!world.inBounds(pos) || !world.loaded(pos)) continue;
            if (filter.test(world.state(pos))) found.add(pos);
        }
        if (index < total) return new Running("block scan " + index + "/" + total);
        cached = new BlockScan(world.identity(), origin, found);
        cachedAt = ctx.tick();
        ctx.bb().set(ctx.owner(), resultKey, cached);
        return Success.INSTANCE;
    }
}
