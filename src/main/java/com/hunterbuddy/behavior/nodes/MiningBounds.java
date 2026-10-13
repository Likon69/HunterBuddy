package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MiningBridge;
import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;

public final class MiningBounds extends Decorator {
    private final MiningBridge mining;
    private final Predicate<BlockPos> rule;

    public MiningBounds(MiningBridge mining, Predicate<BlockPos> rule, Node child) {
        super("mining-bounded-by-real-zone", child);
        this.mining = mining;
        this.rule = rule;
    }

    @Override
    protected void onStart(TickContext ctx) {
        Predicate<BlockPos> previous = mining.breakRule();
        activation().defer(() -> mining.breakRule(previous));
        mining.breakRule(rule);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        mining.breakRule(rule);
        return child().execute(ctx);
    }
}
