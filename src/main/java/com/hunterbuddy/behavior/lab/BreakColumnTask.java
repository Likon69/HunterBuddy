package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.BreakColumnTree;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public record BreakColumnTask(BlockPos origin, int height) implements LabTask {
    private static final Key<BlockPos> ORIGIN = new Key<>("lab-column-origin", BlockPos.class, Scope.OWNER, false);

    public BreakColumnTask {
        origin = Objects.requireNonNull(origin).toImmutable();
        if (height < 1 || height > 24) throw new IllegalArgumentException("column height outside [1,24]");
    }

    @Override
    public String name() {
        return "breakcolumn";
    }

    @Override
    public String target() {
        return origin + " height=" + height;
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.MINING, Resource.INTERACT);
    }

    @Override
    public void initialize(BotContext context) {
        context.ticks().bb().set(context.owner(), ORIGIN, origin);
    }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        return BreakColumnTree.build(ORIGIN, height, world, outputs);
    }
}
