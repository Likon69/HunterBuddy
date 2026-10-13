package com.hunterbuddy.behavior.probes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class FindOpenSkyPoint extends Action {
    private final Key<BlockPos> originKey;
    private final Key<BlockScan> resultKey;
    private final WorldReader world;
    private BlockScan cache;
    private long cachedAt;

    public FindOpenSkyPoint(Key<BlockPos> originKey, Key<BlockScan> resultKey, WorldReader world) {
        super("find-open-sky-point"); this.originKey = originKey; this.resultKey = resultKey; this.world = world;
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        BlockPos origin = ctx.bb().get(ctx.owner(), originKey).orElse(null);
        if (origin == null) return fail(FailureCode.NO_OPTION, "open sky origin missing");
        if (cache != null && cache.world().equals(world.identity()) && cache.origin().equals(origin) && ctx.tick() - cachedAt < 20) {
            ctx.bb().set(ctx.owner(), resultKey, cache); return Success.INSTANCE;
        }
        boolean clear = true;
        for (int height = 1; height <= 6; height++) {
            BlockPos pos = origin.up(height);
            if (!world.inBounds(pos) || !world.loaded(pos) || !world.state(pos).isAir()) { clear = false; break; }
        }
        cache = new BlockScan(world.identity(), origin, clear ? List.of(origin) : List.of());
        cachedAt = ctx.tick(); ctx.bb().set(ctx.owner(), resultKey, cache); return Success.INSTANCE;
    }
}
