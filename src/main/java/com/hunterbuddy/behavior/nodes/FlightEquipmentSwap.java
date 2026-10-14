package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.SlotIntent;
import com.hunterbuddy.util.GliderSwap;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class FlightEquipmentSwap extends Action {
    private final ItemStack expected;
    private final WorldReader world;
    private final Outputs outputs;
    private long version;
    private boolean emitted;
    private boolean resumed;

    public FlightEquipmentSwap(ItemStack expected, WorldReader world, Outputs outputs) {
        super("flight-chest-equipment-swap");
        this.expected = expected.copy();
        this.world = world;
        this.outputs = outputs;
        limit(100);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        emitted = false;
        resumed = false;
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        resumed = true;
        version = Math.max(-1, outputs.inventory().playerVersion() - 1);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        ItemStack worn = world.client().player.getEquippedStack(EquipmentSlot.CHEST);
        if (!emitted && ItemStack.areEqual(worn, expected)) return Success.INSTANCE;
        if (resumed && !worn.isEmpty()) {
            ItemStack comparable = worn.copy();
            if (expected.isDamageable()) comparable.setDamage(expected.getDamage());
            if (ItemStack.areEqual(comparable, expected) && outputs.inventory().confirmedChest(worn)) return Success.INSTANCE;
        }
        if (emitted) {
            if (worn.isEmpty()) return new Running("waiting for server chest contents");
            ItemStack comparable = worn.copy();
            if (expected.isDamageable()) comparable.setDamage(expected.getDamage());
            if (ItemStack.areEqual(comparable, expected) && worn.getDamage() >= expected.getDamage()
                && (resumed || worn.getDamage() <= expected.getDamage() + 4) && outputs.inventory().playerVersion() > version
                && outputs.inventory().confirmedChest(worn)) {
                ctx.checkpoint(ctx.owner());
                return Success.INSTANCE;
            }
            return new Running("waiting for actual server chest equipment");
        }
        if (expected.getCount() != 1) return fail(FailureCode.MISSING_ITEM, "single wearable item required; nothing may be dropped");
        if (!world.client().player.isCreative()
            && EnchantmentHelper.hasAnyEnchantmentsWith(worn, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) {
            return fail(FailureCode.MISSING_ITEM, "chest equipment cannot be exchanged because of Curse of Binding");
        }
        int slot = world.findSlot(stack -> ItemStack.areEqual(stack, expected));
        if (slot < 0) return fail(FailureCode.MISSING_ITEM, "identified wearable left the hotbar");
        outputs.slot(ctx.owner(), new SlotIntent(slot, true));
        if (ctx.sensors().selectedSlot() != slot || ctx.sensors().serverSlot() != slot || !outputs.slotSettled(slot)) {
            return new Running("settling wearable server slot");
        }
        if (ctx.sensors().usingItem()) return new Running("waiting for held-item use to finish");
        outputs.inventory().observePlayer();
        version = outputs.inventory().playerVersion();
        GliderSwap.swapSilently(Hand.MAIN_HAND);
        emitted = true;
        return new Running("confirming wearable use");
    }
}
