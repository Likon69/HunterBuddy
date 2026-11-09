package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.screens.HbModulesScreen;
import com.hunterbuddy.modules.mixin.accessors.GuiRendererScissorAccessor;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WItem;
import net.minecraft.item.ItemStack;

public class HbItem extends WItem {
    private static final double ITEM_PIXELS = 16;
    private double side;
    private final HbItemClip clip = new HbItemClip();

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
        if (itemStack.isEmpty()) return;
        double extent = side == 0 ? theme.scale(32) : side;
        clip.reset(x, y, extent, extent);
        if (MeteorClient.mc.currentScreen instanceof HbModulesScreen screen) {
            WWindow owner = null;
            for (WWidget widget = parent; widget != null; widget = widget.parent) {
                if (widget instanceof WWindow window) { owner = window; break; }
            }
            boolean above = false;
            var windows = screen.windows();
            for (int i = 0; i < windows.size(); i++) {
                WWindow window = windows.get(i);
                if (window == owner) { above = true; continue; }
                if (!above || !window.visible) continue;
                double height = window instanceof HbWindow hb ? hb.visibleHeight() : window.height;
                clip.exclude(Math.floor(window.x), Math.floor(window.y), Math.ceil(window.width), Math.ceil(height));
            }
        }
        var context = ((GuiRendererScissorAccessor) renderer).hunterbuddy$getDrawContext();
        for (int i = 0; i < clip.size(); i++) {
            context.enableScissor((int) Math.ceil(clip.edge(i, 0)), (int) Math.ceil(clip.edge(i, 1)),
                (int) Math.floor(clip.edge(i, 2)), (int) Math.floor(clip.edge(i, 3)));
            renderer.item(itemStack, (int) x, (int) y, (float) (extent / ITEM_PIXELS), true);
            context.disableScissor();
        }
    }
}
