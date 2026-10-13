package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.TemporaryEquipment;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;

public final class PlaceEquipment extends Action {
    private final TemporaryEquipment equipment;
    private final WorldReader world;
    private final Outputs outputs;
    private boolean sent;
    private boolean prepared;

    public PlaceEquipment(TemporaryEquipment equipment, WorldReader world, Outputs outputs) {
        super("place-equipment");
        this.equipment = equipment;
        this.world = world;
        this.outputs = outputs;
        limit(160);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!world.loaded(equipment.pos())) return fail(FailureCode.UNREACHABLE, "equipment chunk unavailable");
        if (equipment.enderChest() && world.findSlot(InventoryLayout::silkPickaxe) < 0)
            return fail(FailureCode.MISSING_ITEM, "Silk Touch pickaxe required before placing ender chest");
        if (sent) {
            if (!outputs.placement().confirmed(equipment.pos())) return new Running("waiting for server equipment placement");
            equipment.placed(ctx, outputs.inventory());
            outputs.mining().trackTemporaryBlock(equipment.pos());
            if (equipment.enderChest()) outputs.mining().trackTemporaryChest(equipment.pos());
            return Success.INSTANCE;
        }
        if (!world.state(equipment.pos()).isReplaceable()) return fail(FailureCode.REFUSED, "temporary position occupied");
        if (!prepared) {
            equipment.preparePlacement(ctx);
            prepared = true;
        }
        NodeResult result = outputs.placement().placeTemporary(ctx, equipment.pos(), equipment.filter());
        if (result instanceof Success) { sent = true; return new Running("confirming temporary placement"); }
        return result;
    }
}
