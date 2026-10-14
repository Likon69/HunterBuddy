package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.TemporaryEquipment;

public final class ConfirmEquipmentPlacement extends Node {
    private final LogisticsContext logistics;
    private final String role;

    public ConfirmEquipmentPlacement(LogisticsContext logistics, String role) {
        super("placement-evidence-" + role);
        this.logistics = logistics;
        this.role = role;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        TemporaryEquipment item = logistics.equipment(role);
        if (!item.placementRequested() || item.pickedUpConfirmed()) {
            return fail(FailureCode.CONDITION_FALSE, "no pending placement");
        }
        boolean block = item.enderChest()
            ? logistics.world.state(item.pos()).isOf(net.minecraft.block.Blocks.ENDER_CHEST)
            : item.signature().matchesBlock(logistics.world.state(item.pos()));
        if (!block) {
            if (item.tracked()) throw new IllegalStateException("tracked " + role + " disappeared before opening");
            return fail(FailureCode.CONDITION_FALSE, "placement has not reached the server");
        }
        if (logistics.world.countMatching(item.filter()) != item.beforePickup()) {
            throw new IllegalStateException("placement of " + role + " lacks matching inventory evidence");
        }
        item.placed(ctx, logistics.outputs.inventory());
        logistics.outputs.mining().trackTemporaryBlock(item.pos());
        if (item.enderChest()) logistics.outputs.mining().trackTemporaryChest(item.pos());
        return Success.INSTANCE;
    }
}
