package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.ShulkerContents;
import java.util.Map;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ShulkerPeek extends Module {
    public enum IconPosition {
        BottomRight,
        BottomLeft,
        TopRight,
        TopLeft,
        Center
    }

    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Integer> iconSize = sgMain.add(new IntSetting.Builder()
        .name("icon-size")
        .description("Size in pixels of the item drawn over the box.")
        .defaultValue(12)
        .min(4)
        .max(16)
        .sliderMin(4)
        .sliderMax(16)
        .build()
    );

    private final Setting<IconPosition> badgeSpot = sgMain.add(new EnumSetting.Builder<IconPosition>()
        .name("icon-position")
        .description("Where in the slot the item is drawn.")
        .defaultValue(IconPosition.Center)
        .build()
    );

    private final Setting<String> multipleIndicator = sgMain.add(new StringSetting.Builder()
        .name("multiple-indicator")
        .description("Mark drawn when the box holds more than one kind of item; empty hides it.")
        .defaultValue("+")
        .build()
    );

    private final Setting<Integer> multipleSize = sgMain.add(new IntSetting.Builder()
        .name("multiple-size")
        .description("Size of that mark; 8 is the normal text size.")
        .defaultValue(8)
        .min(4)
        .max(16)
        .sliderMin(4)
        .sliderMax(16)
        .build()
    );

    private final Setting<Boolean> debugMode = sgMain.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Write the number of item kinds and the main item above each box.")
        .defaultValue(false)
        .build()
    );

    public ShulkerPeek() {
        super(HunterBuddyAddon.LOGISTICS_CATEGORY, "shulker-peek", "Draws the main item of each shulker box over its icon, in inventories and on the hotbar.", "shulker-overview");
    }

    public void drawOverlay(DrawContext ctx, int x, int y, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;

        Map<Item, Integer> totals = ShulkerContents.totals(stack);
        if (totals.isEmpty()) return;

        Item main = null;
        int mainTotal = 0;
        for (Map.Entry<Item, Integer> entry : totals.entrySet()) {
            if (main == null || entry.getValue() > mainTotal) {
                main = entry.getKey();
                mainTotal = entry.getValue();
            }
        }
        boolean mixed = totals.size() > 1;

        if (debugMode.get()) {
            String text = "Items: " + totals.size() + ", Most: " + main.getName().getString() + " x" + mainTotal;
            ctx.drawText(mc.textRenderer, text, x, y - 10, 0xFFFFFFFF, true);
        }

        int s = iconSize.get();
        int cornerX;
        int cornerY;
        switch (badgeSpot.get()) {
            case BottomRight -> {
                cornerX = x + 16 - s;
                cornerY = y + 16 - s;
            }
            case BottomLeft -> {
                cornerX = x;
                cornerY = y + 16 - s;
            }
            case TopRight -> {
                cornerX = x + 16 - s;
                cornerY = y;
            }
            case TopLeft -> {
                cornerX = x;
                cornerY = y;
            }
            default -> {
                cornerX = x + (16 - s) / 2;
                cornerY = y + (16 - s) / 2;
            }
        }

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) cornerX, (float) cornerY);
        if (s != 16) ctx.getMatrices().scale(s / 16f, s / 16f);
        ctx.drawItemWithoutEntity(new ItemStack(main), 0, 0);
        ctx.getMatrices().popMatrix();

        String indicator = multipleIndicator.get();
        if (mixed && !indicator.isEmpty()) {
            float k = multipleSize.get() / 8f;
            int w = mc.textRenderer.getWidth(indicator);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x + 16 - 1 - w * k, (float) (y + 1));
            ctx.getMatrices().scale(k, k);
            ctx.drawText(mc.textRenderer, indicator, 0, 0, 0xFFFFFF00, true);
            ctx.getMatrices().popMatrix();
        }
    }
}
