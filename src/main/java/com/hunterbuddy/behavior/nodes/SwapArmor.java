package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.*;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.*;
import com.hunterbuddy.behavior.resource.*;
import com.hunterbuddy.util.GliderSwap;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class SwapArmor extends Action {
    private final boolean elytra;
    private final WorldReader world;
    private final Outputs outputs;
    private final MissionJournal journal;
    private ItemStack expected;
    private boolean emitted;
    private long version;

    public SwapArmor(boolean elytra, WorldReader world, Outputs outputs, MissionJournal journal) {
        super("swap-chest-equipment");
        this.elytra = elytra;
        this.world = world;
        this.outputs = outputs;
        this.journal = journal;
        limit(100);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        ItemStack worn = world.client().player.getEquippedStack(EquipmentSlot.CHEST);
        if (!emitted && matches(worn)) return Success.INSTANCE;
        if (emitted) {
            if (ItemStack.areEqual(expected, worn) && outputs.inventory().playerVersion() > version && outputs.inventory().confirmedChest(expected)) {
                journal.milestone(ctx, "armor equipped=" + (elytra ? "elytra" : "chestplate"));
                return Success.INSTANCE;
            }
            return new Running("waiting for server chest equipment");
        }
        if (EnchantmentHelper.hasAnyEnchantmentsWith(worn, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)
            && !world.client().player.isCreative()) return fail(FailureCode.REFUSED, "chest equipment has Curse of Binding");
        int slot = world.findSlot(this::matches);
        if (slot < 0) return fail(FailureCode.MISSING_ITEM, "requested chest equipment absent from hotbar");
        outputs.slot(ctx.owner(), new SlotIntent(slot, true));
        if (ctx.sensors().selectedSlot() != slot || ctx.sensors().serverSlot() != slot || !outputs.slotSettled(slot)) return new Running("selecting chest equipment");
        expected = world.client().player.getInventory().getStack(slot).copy();
        outputs.inventory().observePlayer();
        version = outputs.inventory().playerVersion();
        GliderSwap.swapSilently(Hand.MAIN_HAND);
        emitted = true;
        return new Running("confirming worn chest equipment");
    }

    private boolean matches(ItemStack stack) {
        return !stack.willBreakNextUse() && (elytra ? GliderSwap.isGlider(stack) : GliderSwap.isChestPiece(stack));
    }
}
