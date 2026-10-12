package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class PlaceBlock extends Action {
    private final Key<BlockPos> posKey;
    private final Predicate<ItemStack> filter;
    private final boolean strict;
    private final WorldReader world;
    private final Outputs outputs;
    private Stage stage;

    public PlaceBlock(Key<BlockPos> posKey, Predicate<ItemStack> filter, boolean strict, WorldReader world, Outputs outputs) {
        super("place-block");
        this.posKey = Objects.requireNonNull(posKey);
        this.filter = Objects.requireNonNull(filter);
        this.strict = strict;
        this.world = Objects.requireNonNull(world);
        this.outputs = Objects.requireNonNull(outputs);
        limit(100);
    }

    @Override
    protected String target(TickContext ctx) {
        return ctx.bb().get(ctx.owner(), posKey).map(Object::toString).orElse("missing-block");
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        stage = Stage.READY;
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        BlockPos pos = ctx.bb().get(ctx.owner(), posKey).orElse(null);
        if (pos == null) return fail(FailureCode.NO_OPTION, "placement position is missing");
        if (!world.inBounds(pos) || !world.loaded(pos)) return fail(FailureCode.UNREACHABLE, "placement outside loaded world");
        if (!world.state(pos).isReplaceable()) {
            if (stage == Stage.CONFIRMING) return outputs.placement().confirmed(pos) ? Success.INSTANCE : new Running("waiting for server placement confirmation");
            if (filter.test(new ItemStack(world.state(pos).getBlock().asItem()))) return Success.INSTANCE;
            return fail(FailureCode.REFUSED, "placement is occupied by another block");
        }
        NodeResult result = outputs.placement().place(ctx, pos, filter, strict);
        if (result instanceof Success) stage = Stage.CONFIRMING;
        return result instanceof Success ? new Running("waiting for placed block at Pre") : result;
    }

    private enum Stage {
        READY, CONFIRMING
    }
}
