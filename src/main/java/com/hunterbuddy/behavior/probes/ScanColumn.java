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
import net.minecraft.util.math.BlockPos;

public final class ScanColumn extends Action {
    private final Key<BlockPos> originKey;
    private final Key<BlockScan> resultKey;
    private final int height;
    private final WorldReader world;
    private final List<BlockPos> found = new ArrayList<>();
    private BlockPos origin;
    private BlockScan cached;
    private long cachedAt;
    private int index;

    public ScanColumn(Key<BlockPos> originKey, int height, Key<BlockScan> resultKey, WorldReader world) {
        super("scan-column");
        if (height < 1 || height > 384) throw new IllegalArgumentException("column height outside [1,384]");
        this.originKey = Objects.requireNonNull(originKey);
        this.resultKey = Objects.requireNonNull(resultKey);
        this.height = height;
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
        if (origin == null) return fail(FailureCode.NO_OPTION, "column origin is missing");
        if (cached != null && cached.world().equals(world.identity()) && cached.origin().equals(origin) && ctx.tick() - cachedAt < 20) {
            ctx.bb().set(ctx.owner(), resultKey, cached);
            return Success.INSTANCE;
        }
        for (int scanned = 0; scanned < 64 && index < height; scanned++, index++) {
            BlockPos pos = origin.up(index);
            if (!world.inBounds(pos) || !world.loaded(pos)) return fail(FailureCode.UNREACHABLE, "column outside loaded world");
            if (!world.cleared(pos)) found.add(pos);
        }
        if (index < height) return new Running("column scan " + index + "/" + height);
        cached = new BlockScan(world.identity(), origin, found);
        cachedAt = ctx.tick();
        ctx.bb().set(ctx.owner(), resultKey, cached);
        return Success.INSTANCE;
    }
}
