package org.linytech.trader.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.block.ShulkerBoxBlock;

public class AnvilRename extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> renameText = sgGeneral.add(
        new StringSetting.Builder()
            .name("auto-set-name")
            .description("Name to be automatically set in the anvil.")
            .defaultValue("Renamed by Meteor")
            .build()
    );

    private final Setting<Integer> clickDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("click-delay")
            .description("Delay between rename operations to avoid issues.")
            .defaultValue(5)
            .min(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<Boolean> onlyShulkers = sgGeneral.add(
        new BoolSetting.Builder()
            .name("only-shulkers")
            .description("Only rename Shulker Boxes.")
            .defaultValue(false)
            .build()
    );

    private int delay = 0;

    public AnvilRename(Category category) {
        super(category, "Anvil Auto Rename", "Automatically renames items in the anvil.");
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
        if (++delay >= clickDelay.get()) {
            delay = 0;
            renameAndMoveItems();
        }
    }

    private void renameAndMoveItems() {
        if (mc.player == null || mc.player.currentScreenHandler == null) return;
        if (!(mc.currentScreen instanceof AnvilScreen)) return;

        AnvilScreenHandler handler = (AnvilScreenHandler) mc.player.currentScreenHandler;
        ItemStack itemStackOutput = handler.getSlot(2).getStack();
        ItemStack itemStackInput1 = handler.getSlot(0).getStack();

        String outputItemName = removeBrackets(itemStackOutput.getName().getString());

        int playerLevels = mc.player.experienceLevel;

        if (!itemStackOutput.isEmpty() &&
            (playerLevels > 0 || mc.player.isCreative()) &&
            outputItemName.equals(renameText.get())) {
            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);
            return;
        }

        if (!itemStackInput1.isEmpty() && !outputItemName.equals(renameText.get())) {
            AnvilScreen screen = (AnvilScreen) mc.currentScreen;
            screen.nameField.setText(renameText.get());
            handler.updateResult();
            return;
        }

        if (itemStackInput1.isEmpty()) {
            moveNextItemToAnvil(handler);
        }
    }

    private void moveNextItemToAnvil(AnvilScreenHandler handler) {
        for (int i = 3; i < 36 + 3; i++) {
            ItemStack itemStack = handler.getSlot(i).getStack();
            if (!itemStack.isEmpty() && !removeBrackets(itemStack.getName().getString()).equals(renameText.get())) {
                if (onlyShulkers.get() && !isShulker(itemStack)) continue;
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        }
    }

    private boolean isShulker(ItemStack itemStack) {
        return itemStack.getItem() instanceof BlockItem && ((BlockItem) itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock;
    }

    private String removeBrackets(String str) {
        if (str.length() > 2 && str.startsWith("[") && str.endsWith("]")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }
}
