package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.PlaceBlock;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public record PlaceTask(BlockPos pos, Predicate<ItemStack> filter) implements LabTask {
    private static final Key<BlockPos> POS = new Key<>("lab-place-pos", BlockPos.class, Scope.OWNER, false);

    public PlaceTask {
        pos = Objects.requireNonNull(pos).toImmutable();
        Objects.requireNonNull(filter);
    }

    @Override
    public String name() {
        return "place";
    }

    @Override
    public String target() {
        return pos.toString();
    }

    @Override
    public Set<Resource> resources() {
        return Set.of(Resource.SLOT, Resource.LOOK, Resource.INTERACT);
    }

    @Override
    public void initialize(BotContext context) {
        context.ticks().bb().set(context.owner(), POS, pos);
    }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        return new Timeout(100, new WithLease(resources(), LeasePolicy.WAIT, new PlaceBlock(POS, filter, true, world, outputs)));
    }
}
