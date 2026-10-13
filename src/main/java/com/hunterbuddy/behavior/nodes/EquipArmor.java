package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.SlotIntent;
import com.hunterbuddy.util.GliderSwap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class EquipArmor extends Action {
    private final EquipmentSlot slot;
    private final WorldReader world;
    private final Outputs outputs;
    private ItemStack expected;
    private long version;
    public EquipArmor(EquipmentSlot slot, WorldReader world, Outputs outputs) {
        super("equip-armor");
        this.slot = slot;
        this.world = world;
        this.outputs = outputs;
        limit(100);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (expected != null) return outputs.inventory().playerVersion() > version &&
            outputs.inventory().confirmedPlayerSlot(LogisticsInventory.armorIndex(slot), expected)
            && ItemStack.areEqual(world.client().player.getEquippedStack(slot), expected) ? Success.INSTANCE : new
                Running("waiting for confirmed worn armor");
        ItemStack worn = world.client().player.getEquippedStack(slot);
        if (!worn.isEmpty() && worn.getDamage() == 0 && !GliderSwap.isGlider(worn)) return Success.INSTANCE;
        int hotbar = world.findSlot(stack -> LogisticsInventory.armorSlot(stack) == slot && stack.getDamage() == 0 &&
            !GliderSwap.isGlider(stack));
        if (hotbar < 0) return fail(FailureCode.MISSING_ITEM, "fresh armor missing for " + slot.getName());
        outputs.slot(ctx.owner(), new SlotIntent(hotbar, true));
        if (ctx.sensors().selectedSlot() != hotbar || ctx.sensors().serverSlot() != hotbar || !outputs.slotSettled(hotbar)) return new
            Running("selecting armor");
        expected = world.client().player.getInventory().getStack(hotbar).copy();
        if (expected.getCount() != 1) return fail(FailureCode.REFUSED, "equip a single armor item: stacked armor can drop the worn item");
        outputs.inventory().observePlayer();
        version = outputs.inventory().playerVersion();
        GliderSwap.swapSilently(Hand.MAIN_HAND);
        return new Running("confirming armor use");
    }
}
