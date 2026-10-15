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
import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;

public final class FindTakeoffPoints extends Action {
    private final Key<BlockPos> originKey;
    private final Key<BlockScan> resultKey;
    private final WorldReader world;
    private final Predicate<BlockPos> accepted;
    private final List<BlockPos> points = new ArrayList<>();
    private BlockPos origin;
    private BlockPos candidate;
    private BlockScan cache;
    private long cachedAt;
    private int radius;
    private int index;
    private int head;

    public FindTakeoffPoints(Key<BlockPos> originKey, Key<BlockScan> resultKey, WorldReader world) {
        this(originKey, resultKey, world, point -> true);
    }

    public FindTakeoffPoints(Key<BlockPos> originKey, Key<BlockScan> resultKey, WorldReader world,
                             Predicate<BlockPos> accepted) {
        super("find-takeoff-points"); this.originKey = originKey; this.resultKey = resultKey; this.world = world;
        this.accepted = accepted;
        limit(2_400);
    }

    @Override
    protected String target(TickContext ctx) {
        return ctx.bb().get(ctx.owner(), originKey).map(Object::toString).orElse("missing-origin");
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        origin = ctx.bb().get(ctx.owner(), originKey).orElse(null);
        radius = 16; index = 0; head = 0; candidate = null; points.clear();
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (origin == null) return fail(FailureCode.NO_OPTION, "takeoff scan origin missing");
        if (cache != null && cache.world().equals(world.identity()) && cache.origin().equals(origin) && ctx.tick() - cachedAt < 20
            && cache.blocks().stream().allMatch(accepted)) {
            ctx.bb().set(ctx.owner(), resultKey, cache); return Success.INSTANCE;
        }
        for (int work = 0; work < 64; work++) {
            if (candidate != null) {
                BlockPos pos = candidate.up(++head);
                if (!world.inBounds(pos) || !world.loaded(pos) || !world.state(pos).isAir()) candidate = null;
                else if (head == 6) { if (accepted.test(candidate)) points.add(candidate); candidate = null; }
                continue;
            }
            int width = radius / 2 + 1;
            if (index >= width * width) {
                if (!points.isEmpty() || radius == 96) return finish(ctx);
                radius += 16; index = 0;
            }
            int dx = index % width * 4 - radius;
            int dz = index++ / width * 4 - radius;
            if (radius > 16 && Math.max(Math.abs(dx), Math.abs(dz)) <= radius - 16) continue;
            BlockPos surface = world.surface(origin.getX() + dx, origin.getZ() + dz);
            if (world.identity().dimensionKey().equals("minecraft:the_nether") && origin.getY() < 128) {
                if (surface != null && surface.getY() >= 128) surface = null;
                BlockPos nearby = origin.add(dx, 0, dz);
                if (world.inBounds(nearby) && world.loaded(nearby) && !world.cleared(nearby.down())
                    && world.state(nearby.down()).getFluidState().isEmpty() && world.state(nearby).isAir()) surface = nearby;
            }
            candidate = surface; head = 0;
        }
        return new Running("takeoff scan radius=" + radius + " candidates=" + points.size());
    }

    private NodeResult finish(TickContext ctx) {
        cache = new BlockScan(world.identity(), origin, points); cachedAt = ctx.tick();
        ctx.bb().set(ctx.owner(), resultKey, cache); return Success.INSTANCE;
    }
}
