package org.linytech.trader.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class ShulkerColor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private int delay = 0;
    private final int craftingSlotLimeDye = 1;  // Slot in the crafting grid for lime dye
    private final int craftingSlotShulker = 2;  // Slot for the shulker box
    private final int craftingResultSlot = 0;   // Crafting result slot

    public ShulkerColor(Category category) {
        super(category, "shulker-color", "Automatically colors shulker boxes with lime dye in the crafting grid.");
    }

    @Override
    public void onActivate() {
        delay = 0;
    }

    @Override
    public void onDeactivate() {
        delay = 0;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.currentScreen == null) {
            return;
        }

        if (!(mc.currentScreen instanceof HandledScreen)) {
            return;
        }

        delay++;

        if (delay >= 5) {  // Adjust delay to prevent issues
            delay = 0;

            // Ensure lime dye is in slot 1 and shulker in slot 0
            if (!isLimeDyeInSlot(craftingSlotLimeDye)) {
                moveLimeDyeToSlot(craftingSlotLimeDye);
            }

            if (!isShulkerInSlot(craftingSlotShulker)) {
                moveShulkerToSlot(craftingSlotShulker);
            }

            // If the shulker box is colored, move it to the inventory and reset
            if (isShulkerColored()) {
                takeColoredShulker();
            }
        }
    }

    private boolean isLimeDyeInSlot(int slot) {
        ItemStack stack = mc.player.currentScreenHandler.getSlot(slot).getStack();
        return stack.getItem() == Items.LIME_DYE;
    }

    private boolean isShulkerInSlot(int slot) {
        ItemStack stack = mc.player.currentScreenHandler.getSlot(slot).getStack();
        return stack.getItem() == Items.SHULKER_BOX;
    }

    private void moveLimeDyeToSlot(int slot) {
        int inventorySlot = findItemInInventory(Items.LIME_DYE);
        if (inventorySlot != -1) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, inventorySlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private void moveShulkerToSlot(int slot) {
        int inventorySlot = findItemInInventory(Items.SHULKER_BOX);
        if (inventorySlot != -1) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, inventorySlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private boolean isShulkerColored() {
        ItemStack stack = mc.player.currentScreenHandler.getSlot(craftingResultSlot).getStack();
        return stack.getItem() == Items.LIME_SHULKER_BOX;
    }

    private void takeColoredShulker() {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, craftingResultSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    // Updated findItemInInventory to use Item instead of Items
    private int findItemInInventory(Item item) {
        for (int i = 9; i < 36 ; i++) {  // Inventory slots 9-35 (ignoring hotbar)
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }
}
