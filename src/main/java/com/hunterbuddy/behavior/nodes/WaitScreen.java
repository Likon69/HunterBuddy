package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.InventoryBridge;
import java.util.Objects;

public final class WaitScreen extends Action {
    private final InventoryBridge inventory;

    public WaitScreen(InventoryBridge inventory) {
        super("wait-screen");
        this.inventory = Objects.requireNonNull(inventory);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        return inventory.ready() ? Success.INSTANCE : new Running("waiting for synchronized owned screen");
    }
}
