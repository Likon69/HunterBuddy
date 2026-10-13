package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.mc.TemporaryEquipment;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import net.minecraft.util.math.BlockPos;

public final class BreakEquipment extends Decorator {
    private final TemporaryEquipment equipment;
    private final WorldReader world;
    private final Outputs outputs;
    private boolean prepared;

    public BreakEquipment(TemporaryEquipment equipment, Key<BlockPos> key, WorldReader world, Outputs outputs) {
        super("break-tracked-equipment", new BreakBlock(key, false, world, outputs));
        this.equipment = equipment;
        this.world = world;
        this.outputs = outputs;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!equipment.tracked()) return fail(FailureCode.REFUSED, "break requires tracked temporary position");
        if (!world.loaded(equipment.pos())) return fail(FailureCode.UNREACHABLE, "temporary equipment chunk unavailable");
        if (!world.cleared(equipment.pos()) && (equipment.enderChest()
            ? !world.state(equipment.pos()).isOf(net.minecraft.block.Blocks.ENDER_CHEST)
            : !equipment.signature().matchesBlock(world.state(equipment.pos())))) return fail(FailureCode.REFUSED, "tracked equipment block changed");
        if (!prepared) {
            if (world.cleared(equipment.pos())) return fail(FailureCode.REFUSED, "tracked equipment disappeared before break");
            equipment.prepareBreak(ctx, outputs.inventory());
            prepared = true;
        }
        if (equipment.enderChest() && !world.cleared(equipment.pos())) {
            int slot = world.findSlot(InventoryLayout::silkPickaxe);
            if (slot < 0) return fail(FailureCode.MISSING_ITEM, "Silk Touch pickaxe missing before break");
            if (!outputs.mining().vanilla(equipment.pos())) {
                return fail(FailureCode.REFUSED, "add minecraft:ender_chest to HMine leave-to-vanilla so the Silk Touch pickaxe breaks it");
            }
            outputs.mining().holdTool(equipment.pos(), slot);
        }
        NodeResult result = child().execute(ctx);
        if (result instanceof Success) equipment.broken(ctx);
        return result;
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) { outputs.mining().releaseTool(); }
    @Override
    protected void onSuspend(TickContext ctx) { outputs.mining().releaseTool(); }
}
