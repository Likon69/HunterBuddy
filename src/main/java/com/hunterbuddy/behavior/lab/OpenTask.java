package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.OpenChestTree;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public record OpenTask(BlockPos pos, BaritoneBridge baritone) implements LabTask {
    public OpenTask {
        pos = Objects.requireNonNull(pos).toImmutable();
        Objects.requireNonNull(baritone);
    }

    @Override
    public String name() { return "open"; }

    @Override
    public String target() { return pos.toString(); }

    @Override
    public Set<Resource> resources() { return Set.of(Resource.BARITONE, Resource.MINING, Resource.INVENTORY, Resource.INTERACT); }

    @Override
    public void initialize(BotContext context) { Objects.requireNonNull(context); }

    @Override
    public Node build(BotContext context, WorldReader world, Outputs outputs) {
        return OpenChestTree.build(pos, world, baritone, outputs);
    }
}
