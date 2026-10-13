package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;

public final class ConfirmMiningPickup extends Decorator {
    private final MineSiteContext mine;

    public ConfirmMiningPickup(MineSiteContext mine, Node child) {
        super("finish-pickup-on-exact-inventory-receipt", child);
        this.mine = mine;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return mine.receipt.collected() ? Success.INSTANCE : child().execute(ctx);
    }
}
