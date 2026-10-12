package com.hunterbuddy.modules;

import com.hunterbuddy.util.QuietClicks;
import com.hunterbuddy.HunterBuddyAddon;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class InventoryCleaner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScreens = settings.createGroup("Screens");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Whitelist throws everything not in the list below. Blacklist throws only what is in the list.")
        .defaultValue(Mode.Whitelist)
        .build()
    );

    private final Setting<List<Item>> whitelist = sgGeneral.add(new ItemListSetting.Builder()
        .name("whitelist")
        .description("The item list the mode above checks against.")
        .defaultValue(
            Items.EXPERIENCE_BOTTLE, Items.SHULKER_BOX, Items.WHITE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX,
            Items.MAGENTA_SHULKER_BOX, Items.LIGHT_BLUE_SHULKER_BOX, Items.YELLOW_SHULKER_BOX, Items.LIME_SHULKER_BOX,
            Items.PINK_SHULKER_BOX, Items.GRAY_SHULKER_BOX, Items.LIGHT_GRAY_SHULKER_BOX, Items.CYAN_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX, Items.BLUE_SHULKER_BOX, Items.BROWN_SHULKER_BOX, Items.GREEN_SHULKER_BOX,
            Items.RED_SHULKER_BOX, Items.BLACK_SHULKER_BOX, Items.CRAFTING_TABLE, Items.CRYING_OBSIDIAN,
            Items.DIAMOND_CHESTPLATE, Items.DIAMOND_PICKAXE, Items.ELYTRA, Items.ENCHANTED_GOLDEN_APPLE,
            Items.ENDER_CHEST, Items.FIREWORK_ROCKET, Items.FIREWORK_STAR, Items.FLINT_AND_STEEL, Items.GLOWSTONE,
            Items.GOLDEN_CARROT, Items.JUNGLE_SIGN, Items.NETHERITE_AXE, Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_SWORD, Items.OBSIDIAN,
            Items.RESPAWN_ANCHOR, Items.TOTEM_OF_UNDYING
        )
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait between two throws.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> hotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar")
        .description("Also clean the hotbar. The main inventory is always cleaned.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> inventoryScreen = sgScreens.add(new BoolSetting.Builder()
        .name("inventory")
        .description("Clean while no screen or the player inventory is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> craftingTableScreen = sgScreens.add(new BoolSetting.Builder()
        .name("crafting-table")
        .description("Clean while a crafting table is open.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> containersScreen = sgScreens.add(new BoolSetting.Builder()
        .name("containers")
        .description("Clean while a chest, shulker box or other container screen is open.")
        .defaultValue(false)
        .build()
    );

    private int ticksLeft;

    public InventoryCleaner() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "inventory-cleaner", "Throws items out of your inventory that match, or don't match, a list.");
    }

    @Override
    public void onActivate() {
        ticksLeft = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (!screenAllowed()) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;

        if (ticksLeft > 0) {
            ticksLeft--;
            return;
        }

        int slot = findSlotToThrow();
        if (slot == -1) return;
        if (!QuietClicks.readyOrSettle()) return;

        InvUtils.drop().slot(slot);
        ticksLeft = delay.get();
    }

    private boolean screenAllowed() {
        if (mc.currentScreen == null) return inventoryScreen.get();
        if (mc.currentScreen instanceof InventoryScreen) return inventoryScreen.get();
        if (mc.currentScreen instanceof CraftingScreen) return craftingTableScreen.get();
        if (mc.currentScreen instanceof HandledScreen) return containersScreen.get();
        return false;
    }

    private int findSlotToThrow() {
        int start = hotbar.get() ? 0 : 9;
        for (int i = start; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (shouldThrow(stack)) return i;
        }
        return -1;
    }

    private boolean shouldThrow(ItemStack stack) {
        boolean listed = whitelist.get().contains(stack.getItem());
        return mode.get() == Mode.Whitelist ? !listed : listed;
    }

    public enum Mode {
        Whitelist,
        Blacklist
    }
}
