package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbAlignmentGrid;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.WindowConfig;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.gui.Click;
import org.lwjgl.glfw.GLFW;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.utils.Utils.getWindowHeight;
import static meteordevelopment.meteorclient.utils.Utils.getWindowWidth;

public class HbWindow extends WWindow implements HbWidget {
    private static final double DRAG_THRESHOLD = 3;
    private static final double ICON_SIDE = 16;
    private static final double ICON_GAP = 4;
    private final HbCategoryActivity activity;

    public HbWindow(WWidget icon, String title, HbCategoryActivity activity) {
        super(icon, title);
        this.activity = activity;
    }

    private List<HbWindowSnap.Guide> guides = List.of();

    public boolean snapping() {
        return dragging && dragged;
    }

    public List<HbWindowSnap.Guide> guides() {
        return guides;
    }

    private double visibleHeight() {
        return header.height + (height - header.height) * animProgress;
    }

    private List<HbWindowSnap.Box> siblingBoxes() {
        List<HbWindowSnap.Box> boxes = new ArrayList<>();
        if (!(parent instanceof WContainer container)) return boxes;
        for (Cell<?> cell : container.cells) {
            if (cell.widget() instanceof HbWindow window && window != this && window.visible) {
                boxes.add(new HbWindowSnap.Box(window.x, window.y, window.width, window.visibleHeight()));
            }
        }
        return boxes;
    }

    private boolean moduleWindow() {
        return theme().clickGui() && (activity != null || "search".equals(id) || "favorites".equals(id));
    }

    public boolean containsInteraction(double mouseX, double mouseY) {
        if (!visible) return false;
        double visibleHeight = header.height + (height - header.height) * animProgress;
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + visibleHeight
            || expanded && containsPopup(view, mouseX, mouseY);
    }

    private boolean containsPopup(WWidget widget, double mouseX, double mouseY) {
        if (!widget.visible) return false;
        if (widget instanceof HbDropdown<?> dropdown && dropdown.containsPopup(mouseX, mouseY)) return true;
        if (widget instanceof WContainer container) {
            for (Cell<?> cell : container.cells) {
                if (containsPopup(cell.widget(), mouseX, mouseY)) return true;
            }
        }
        return false;
    }

    @Override
    public void init() {
        super.init();
        if (!moduleWindow()) return;
        minWidth = activity != null && activity.name().equals("Hunt") ? (style().hasCounter() || style().inverted() ? 262 : 258) : 228;
        Cell<?> content = cells.getLast();
        content.padTop(theme().layoutUnits(style().contentTop())).padBottom(theme().layoutUnits(style().contentBottom()));
        if (style().inverted()) {
            cells.getFirst().padHorizontal(theme().layoutUnits(1)).padTop(theme().layoutUnits(1));
            content.padHorizontal(theme().layoutUnits(1));
        }
        updateNumber();
    }

    private void updateNumber() {
        if (activity == null || id == null || !(parent instanceof WContainer container)) return;
        int number = 0;
        for (int i = 0; i < container.cells.size(); i++) {
            WWidget widget = container.cells.get(i).widget();
            if (widget instanceof HbWindow window && window.activity != null && window.id != null) number++;
            if (widget == this) {
                activity.number(number);
                return;
            }
        }
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new HbHeader(icon);
    }

    @Override
    public void calculateSize() {
        updateNumber();
        if (moduleWindow() && view != null) {
            view.maxHeight = Math.max(theme().hbScale(22), getWindowHeight() - theme().hbScale("search".equals(id) ? 148 : 128));
        }
        double minimum = minWidth;
        if (moduleWindow()) minWidth = theme().layoutUnits(minimum);
        try {
            super.calculateSize();
        } finally {
            minWidth = minimum;
        }
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            style().drawWindowBody(renderer, theme(), x, y + header.height, width, height - header.height);
        }
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!visible) return true;
        if (!moduleWindow()) return renderNative(renderer, mouseX, mouseY, delta);
        renderer.scissorStart(x, y, width, header.height + (height - header.height) * animProgress);
        boolean hidden = super.render(renderer, mouseX, mouseY, delta);
        if (!hidden) {
            double visibleHeight = header.height + (height - header.height) * animProgress;
            style().drawWindowFrame(renderer, theme(), x, y, width, visibleHeight);
        }
        renderer.scissorEnd();
        return hidden;
    }

    private boolean renderNative(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        boolean hidden = super.render(renderer, mouseX, mouseY, delta);
        if (!hidden) {
            double visibleHeight = header.height + (height - header.height) * animProgress;
            theme().fitted(header, style().headerHeight(), () -> style().drawWindowFrame(renderer, theme(), x, y, width, visibleHeight));
        }
        return hidden;
    }

    private class HbHeader extends WHeader {
        private String display;
        private String[] letters;
        private double[] offsets;

        HbHeader(WWidget icon) {
            super(icon);
        }

        private boolean moduleLook;
        private HbItem categoryIcon;
        private double rawX;
        private double rawY;
        private double travelX;
        private double travelY;
        private boolean engaged;

        @Override
        public boolean onMouseClicked(Click click, boolean doubled) {
            boolean handled = super.onMouseClicked(click, doubled);
            if (dragging) {
                rawX = HbWindow.this.x;
                rawY = HbWindow.this.y;
                travelX = 0;
                travelY = 0;
                engaged = false;
            }
            return handled;
        }

        @Override
        public boolean onMouseReleased(Click click) {
            boolean handled = super.onMouseReleased(click);
            guides = List.of();
            return handled;
        }

        @Override
        public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
            if (!moduleLook) {
                super.onMouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
                return;
            }
            if (!dragging) return;
            rawX += mouseX - lastMouseX;
            rawY += mouseY - lastMouseY;
            if (!engaged) {
                travelX += mouseX - lastMouseX;
                travelY += mouseY - lastMouseY;
                engaged = Math.hypot(travelX, travelY) > theme().hbScale(DRAG_THRESHOLD);
                if (!engaged) return;
            }
            double targetX = rawX;
            double targetY = rawY;
            if (Input.isKeyPressed(GLFW.GLFW_KEY_LEFT_ALT) || Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT_ALT)) {
                guides = List.of();
            } else {
                HbWindowSnap.Result snap = HbWindowSnap.snap(
                    new HbWindowSnap.Box(rawX, rawY, HbWindow.this.width, visibleHeight()), siblingBoxes(),
                    getWindowWidth(), getWindowHeight(), theme().hbScale(HbAlignmentGrid.MINOR_SPACING),
                    theme().hbScale(10), theme().hbScale(4));
                targetX = snap.x();
                targetY = snap.y();
                guides = snap.guides();
            }
            HbWindow.this.move(targetX - HbWindow.this.x, targetY - HbWindow.this.y);
            moved = true;
            movedX = HbWindow.this.x;
            movedY = HbWindow.this.y;
            if (id != null) {
                WindowConfig config = theme().getWindowConfig(id);
                config.x = HbWindow.this.x;
                config.y = HbWindow.this.y;
            }
            dragged = true;
        }

        @Override
        public void init() {
            moduleLook = moduleWindow();
            if (!moduleLook) {
                super.init();
                return;
            }
            display = style().hasCounter() ? String.join(" ", title.toUpperCase(java.util.Locale.ROOT).split("")) : style().windowTitle(title);
            if (style().hasCounter()) {
                letters = new String[display.length()];
                offsets = new double[display.length()];
                for (int i = 0; i < display.length(); i++) letters[i] = display.substring(i, i + 1);
            }
            if (style().lamp()) add(new HbLamp(() -> true));
            if (beforeHeaderInit != null) {
                int before = cells.size();
                beforeHeaderInit.accept(this);
                if (cells.size() > before && cells.getLast().widget() instanceof HbItem item) {
                    categoryIcon = item;
                    item.fit(theme().hbScale(ICON_SIDE));
                }
            }
            if (style().counter() && activity != null) add(new HbCounter(activity));
        }

        @Override
        protected void onCalculateSize() {
            if (!moduleLook) {
                super.onCalculateSize();
                return;
            }
            width = theme().typography().width(display, 12, style().strongTitle()) + theme().hbScale(80) + iconSpan();
            if (style().hasCounter()) {
                double offset = 0;
                for (int i = 0; i < letters.length; i++) {
                    offsets[i] = offset;
                    offset += theme().typography().width(letters[i], 12, false) + theme().hbScale(3);
                }
                width += theme().hbScale(Math.max(0, display.length() - 1) * 3);
            }
            height = theme().hbScale(style().headerHeight());
        }

        private double titleStart() {
            double start = x + theme().hbScale(style().titleInset());
            if (style().inverted() && activity != null) {
                start += theme().typography().width(activity.number(), 12, false) + theme().hbScale(10);
            }
            return start;
        }

        private double iconSpan() {
            return categoryIcon == null ? 0 : theme().hbScale(ICON_SIDE + ICON_GAP);
        }

        @Override
        protected void onCalculateWidgetPositions() {
            if (!moduleLook) {
                super.onCalculateWidgetPositions();
                return;
            }
            for (int i = 0; i < cells.size(); i++) {
                Cell<?> cell = cells.get(i);
                WWidget widget = cell.widget();
                cell.x = widget instanceof HbCounter ? x + width - theme().hbScale(10) - widget.width
                    : widget == categoryIcon ? titleStart() : x + theme().hbScale(10);
                cell.y = y + theme().hbScale(11) - widget.height / 2;
                cell.width = widget.width;
                cell.height = widget.height;
                cell.alignWidget();
            }
        }

        @Override
        public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (!moduleLook) return super.render(renderer, mouseX, mouseY, delta);
            if (!visible) return true;
            animProgress = MathHelper.clamp(animProgress + (expanded ? 1 : -1) * delta * 14, 0, 1);
            onRender(renderer, mouseX, mouseY, delta);
            for (int i = 0; i < cells.size(); i++) cells.get(i).widget().render(renderer, mouseX, mouseY, delta);
            return false;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            boolean active = activity != null && activity.any();
            double phase = "Visuals".equals(title) ? 2.5 : "Utility".equals(title) ? 4.2 : 0;
            theme().fitted(this, style().headerHeight(), () -> style().drawWindowHeader(renderer, theme(), this, active, phase));
            if (!moduleLook) return;
            if (style().inverted() && activity != null) {
                theme().typography().draw(renderer, activity.number(), x + theme().hbScale(style().titleInset()), y + theme().hbScale(5), 12, theme().colors().dim(), false);
            }
            double textX = titleStart() + iconSpan();
            if (style().hasCounter()) {
                for (int i = 0; i < letters.length; i++) {
                    theme().typography().draw(renderer, letters[i], textX + offsets[i], y + theme().hbScale(5), 12, theme().titleTextColor.get(), false);
                }
            } else {
                theme().typography().draw(renderer, display, textX, y + theme().hbScale(5), 12,
                    style().titleColor(theme()), style().strongTitle());
                style().drawTitleTail(renderer, theme(), this, textX + theme().typography().width(display, 12, style().strongTitle()), active);
            }
        }
    }
}
