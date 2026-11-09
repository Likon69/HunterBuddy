package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WItem;
import net.minecraft.item.ItemStack;

public class HbItem extends WItem {
    private static final double ITEM_PIXELS = 16;
    private double side;

    public HbItem(ItemStack itemStack) {
        super(itemStack);
    }

    public void fit(double side) {
        this.side = side;
    }

    @Override
    protected void onCalculateSize() {
        if (side == 0) {
            super.onCalculateSize();
            return;
        }
        width = side;
        height = side;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (side == 0) {
            super.onRender(renderer, mouseX, mouseY, delta);
            return;
        }
        if (!itemStack.isEmpty()) {
            renderer.post(() -> renderer.item(itemStack, (int) x, (int) y, (float) (side / ITEM_PIXELS), true));
        }
    }
}
