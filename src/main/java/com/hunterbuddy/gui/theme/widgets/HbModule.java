package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbAnim;
import com.hunterbuddy.gui.theme.HbDraw;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class HbModule extends WPressable implements HbWidget {
    private static final double PRESS_ALPHA = 0.22;
    private final Module module;
    private final String title;
    private final HbModuleEntry entry;
    private final WWidget row = new WWidget() {};
    private final HbPressLatch latch = new HbPressLatch();
    private final Color press = new Color();
    private boolean wasActive;
    private double fill;
    private double fillFrom;
    private long transitionStart;
    private long pulseStart;

    public HbModule(Module module, String title, HbModuleEntry entry) {
        this.module = module;
        this.title = title;
        this.entry = entry;
        tooltip = module.description;
        wasActive = module.isActive();
        fill = wasActive ? 1 : 0;
        fillFrom = fill;
    }

    private boolean separated() {
        return style().inverted() && theme().separatorBeforeModules.get().contains(module);
    }

    @Override
    public double pad() {
        return theme().hbScale(style().moduleInset(module.isActive()));
    }

    @Override
    protected void onCalculateSize() {
        width = theme().typography().width(title, 12, false) + theme().hbScale(style().moduleInset(true) + 36);
        height = theme().hbScale(separated() ? 27 : 20);
    }

    @Override
    public boolean onMouseClicked(Click click, boolean doubled) {
        if (separated() && click.y() < y + theme().hbScale(7)) return false;
        return super.onMouseClicked(click, doubled);
    }

    @Override
    protected void onPressed(int button) {
        latch.release();
        if (button == GLFW_MOUSE_BUTTON_LEFT) module.toggle();
        else if (button == GLFW_MOUSE_BUTTON_RIGHT) entry.toggle();
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        boolean active = module.isActive();
        long now = HbAnim.now();
        if (active != wasActive) {
            if (active) pulseStart = now;
            fillFrom = fill;
            transitionStart = now;
            wasActive = active;
        }
        double progress = transitionStart == 0 ? 1 : Math.min(1, (now - transitionStart) / 140_000_000.0);
        double steps = Math.floor(progress * 14) / 14;
        fill = fillFrom + ((active ? 1 : 0) - fillFrom) * steps;
        row.theme = theme;
        row.x = x;
        row.y = y + (separated() ? theme().hbScale(7) : 0);
        row.width = width;
        row.height = theme().hbScale(20);
        if (separated()) {
            for (double left = x + theme().hbScale(10); left < x + width - theme().hbScale(10); left += theme().hbScale(3)) {
                renderer.quad(left, y + theme().hbScale(3), theme().hbScale(1), theme().hbScale(1), theme().colors().border());
            }
        }
        boolean hover = mouseOver && mouseY >= row.y;
        boolean risk = theme().riskModules.get().contains(module);
        double pulse = pulseStart == 0 ? 1 : Math.min(1, (now - pulseStart) / 250_000_000.0);
        style().drawModuleRow(renderer, theme(), row, active, hover, risk, pulse, fill);
        if (latch.held(pressed)) renderer.quad(row, HbDraw.withAlpha(press, theme().accent(), PRESS_ALPHA));
        double textX = x + theme().hbScale(style().moduleInset(active));
        double textY = row.y + theme().hbScale(4);
        double textWidth = theme().typography().width(title, 12, false);
        if (style().inverted() && fill > 0 && fill < 1) {
            renderer.scissorStart(x + width * fill, row.y, width * (1 - fill), row.height);
            drawTitle(renderer, textX, textY, textWidth, false, hover);
            renderer.scissorEnd();
            renderer.scissorStart(x, row.y, width * fill, row.height);
            drawTitle(renderer, textX, textY, textWidth, true, hover);
            renderer.scissorEnd();
        } else {
            drawTitle(renderer, textX, textY, textWidth, style().inverted() ? fill == 1 : active, hover);
        }
        if (risk) style().drawRisk(renderer, theme(), row, active);
        if (module.favorite) drawFavoriteMarker(renderer, active);
        style().drawModuleArrow(renderer, theme(), row, entry.isExpanded(), active);
    }

    private void drawTitle(GuiRenderer renderer, double textX, double textY, double textWidth, boolean active, boolean hover) {
        var color = style().moduleTextColor(theme(), active, hover);
        theme().typography().draw(renderer, title, textX, textY, 12, color, false);
        if (style().inverted() && hover) {
            renderer.quad(textX, textY + theme().hbScale(11), textWidth, theme().hbScale(1),
                active ? color : theme().accent());
        }
    }

    private void drawFavoriteMarker(GuiRenderer renderer, boolean active) {
        double right = x + width;
        if (style().hasCounter()) {
            renderer.triangle(right - theme().hbScale(8), row.y, right, row.y, right, row.y + theme().hbScale(8), theme().accent());
        } else if (style().inverted()) {
            renderer.quad(right - theme().hbScale(23), row.y + theme().hbScale(7), theme().hbScale(6), theme().hbScale(6),
                style().moduleTextColor(theme(), active, false));
        } else {
            HbDraw.diamond(renderer, right - theme().hbScale(20), row.y + row.height / 2, theme().hbScale(4 * Math.sqrt(2) * 0.7), theme().accent());
        }
    }
}
