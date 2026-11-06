package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import net.minecraft.client.gui.Click;

public final class HbHorizontalViewport extends WContainer implements HbWidget {
    private final WWidget content;
    private double offset;
    private boolean dragging;

    public HbHorizontalViewport(WWidget content) {
        this.content = content;
        tooltip = "Scroll horizontally to see the full control.";
    }

    @Override
    public void init() {
        add(content);
    }

    @Override
    protected void onCalculateSize() {
        width = content.width;
        height = content.height + theme().hbScale(8);
    }

    private double overflow() {
        return Math.max(0, content.width - width);
    }

    @Override
    protected void onCalculateWidgetPositions() {
        offset = Math.clamp(offset, 0, overflow());
        content.x = x - offset;
        content.y = y;
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!visible) return true;
        renderer.scissorStart(x, y, width, height);
        boolean hidden = super.render(renderer, mouseX, mouseY, delta);
        if (overflow() > 0) {
            double handle = width * width / content.width;
            renderer.quad(x, y + height - theme().hbScale(5), width, theme().hbScale(3), theme().colors().border());
            renderer.quad(x + offset / overflow() * (width - handle), y + height - theme().hbScale(5), handle, theme().hbScale(3), theme().accent());
        }
        renderer.scissorEnd();
        return hidden;
    }

    @Override
    protected boolean propagateEvents(WWidget widget) {
        return widget.isFocused() || mouseOver && !dragging;
    }

    @Override
    public boolean onMouseClicked(Click click, boolean doubled) {
        if (click.button() != 0 || !mouseOver || overflow() == 0 || click.y() < y + height - theme().hbScale(8)) return false;
        dragging = true;
        setFocused(true);
        scrollTo(click.x());
        return true;
    }

    @Override
    public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
        if (dragging) scrollTo(mouseX);
    }

    private void scrollTo(double mouseX) {
        double handle = width * width / content.width;
        setOffset((mouseX - x - handle / 2) / (width - handle) * overflow());
    }

    private void setOffset(double value) {
        double next = Math.clamp(value, 0, overflow());
        content.move(offset - next, 0);
        offset = next;
    }

    @Override
    public boolean onMouseReleased(Click click) {
        boolean used = dragging;
        dragging = false;
        setFocused(false);
        return used;
    }

    @Override
    public boolean mouseScrolled(double amount) {
        if (mouseOver && overflow() > 0) {
            setOffset(offset - theme().hbScale(amount * 24));
            return true;
        }
        return super.mouseScrolled(amount);
    }
}
