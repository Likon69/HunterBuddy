package com.hunterbuddy.gui.theme.screens;

import com.hunterbuddy.gui.theme.HbAlignmentGrid;
import com.hunterbuddy.gui.theme.HbGuiTheme;
import com.hunterbuddy.gui.theme.widgets.HbModuleEntry;
import com.hunterbuddy.gui.theme.widgets.HbWindow;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.meteor.ModuleBindChangedEvent;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class HbModulesScreen extends ModulesScreen {
    private final List<HbModuleEntry> expanded = new ArrayList<>();
    private HbModuleEntry clipboardTarget;
    private WCategoryController categoryController;
    private Cell<WWindow> favorites;
    private boolean favoritesDirty;

    public HbModulesScreen(HbGuiTheme theme) {
        super(theme);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <W extends WWidget> Cell<W> add(W widget) {
        if (widget instanceof WCategoryController) return (Cell<W>) super.add(new HbCategoryController());
        return super.add(widget);
    }

    @Override
    protected Cell<WWindow> createFavorites(WContainer container) {
        categoryController = (WCategoryController) container;
        favorites = super.createFavorites(container);
        return favorites;
    }

    @Override
    protected void init() {
        super.init();
        if (favorites != null && !categoryController.cells.contains(favorites)) favorites = null;
        prune();
    }

    public void refreshFavorites() {
        favoritesDirty = true;
    }

    public void cancelBindings() {
        for (HbModuleEntry entry : expanded) entry.settings().cancelBinding();
    }

    private void updateFavorites() {
        if (!favoritesDirty) return;
        favoritesDirty = false;
        if (favorites == null) {
            favorites = createFavorites(categoryController);
            if (favorites != null) categoryController.windows.add(favorites.widget());
        } else {
            WWindow window = favorites.widget();
            window.clear();
            if (!createFavoritesW(window)) {
                categoryController.remove(favorites);
                categoryController.windows.remove(window);
                favorites = null;
            }
        }
    }

    public void toggle(HbModuleEntry entry) {
        if (categoryController instanceof HbCategoryController controller) controller.raise(entry.window());
        prune();
        if (entry.isExpanded()) {
            entry.collapse();
            expanded.remove(entry);
            if (clipboardTarget == entry) clipboardTarget = null;
            return;
        }

        expanded.removeIf(open -> {
            if (open.window() != entry.window() && open.module() != entry.module()) return false;
            open.collapse();
            return true;
        });

        entry.expand();
        expanded.add(entry);
        clipboardTarget = entry;
    }

    private void prune() {
        expanded.removeIf(entry -> {
            if (entry.isAttached()) return false;
            entry.collapse();
            return true;
        });
        if (!expanded.contains(clipboardTarget)) clipboardTarget = null;
    }

    @Override
    public void tick() {
        super.tick();
        updateFavorites();
        prune();
        for (HbModuleEntry entry : List.copyOf(expanded)) entry.settings().tick();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!locked) {
            prune();
            for (HbModuleEntry entry : expanded) {
                if (entry.mouseOver && entry.getView().mouseOver) clipboardTarget = entry;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !Modules.get().isBinding();
    }

    @Override
    public void renderCustom(DrawContext context, int mouseX, int mouseY, float delta) {
        ((HbGuiTheme) theme).inClickGui(() -> super.renderCustom(context, mouseX, mouseY, delta));
    }

    @Override
    public void removed() {
        cancelBindings();
        super.removed();
    }

    @EventHandler
    private void onModuleBindChanged(ModuleBindChangedEvent event) {
        for (HbModuleEntry entry : expanded) entry.settings().resetBind();
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        for (HbModuleEntry entry : expanded) entry.settings().syncActive();
    }

    @Override
    public boolean toClipboard() {
        prune();
        return clipboardTarget == null ? super.toClipboard() : clipboardTarget.settings().toClipboard();
    }

    @Override
    public boolean fromClipboard() {
        prune();
        return clipboardTarget == null ? super.fromClipboard() : clipboardTarget.settings().fromClipboard();
    }

    private class HbCategoryController extends WCategoryController {
        private final HbAlignmentGrid alignment = new HbAlignmentGrid();
        private WWindow captured;
        private double pointerX;
        private double pointerY;

        void raise(WWindow window) {
            if (windows.remove(window)) windows.add(window);
        }

        private WWindow hit(double mouseX, double mouseY) {
            for (int i = windows.size() - 1; i >= 0; i--) {
                WWindow window = windows.get(i);
                if (window instanceof HbWindow hb && hb.containsInteraction(mouseX, mouseY)) return window;
            }
            return null;
        }

        @Override
        protected void renderWidget(WWidget widget, GuiRenderer renderer, double mouseX, double mouseY, double delta) {}

        @Override
        public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (super.render(renderer, mouseX, mouseY, delta)) return true;
            WWindow hovered = hit(mouseX, mouseY);
            HbWindow snapping = null;
            for (WWindow window : windows) {
                if (window instanceof HbWindow hb && hb.snapping()) snapping = hb;
            }
            alignment.advance(snapping != null, delta);
            HbGuiTheme hbTheme = (HbGuiTheme) theme;
            alignment.drawGrid(renderer::quad, hbTheme, Utils.getWindowWidth(), Utils.getWindowHeight());
            for (WWindow window : windows) {
                window.render(renderer, window == hovered ? mouseX : Double.NaN, window == hovered ? mouseY : Double.NaN, delta);
            }
            if (snapping != null) alignment.drawGuides(renderer::quad, hbTheme, snapping.guides(), Utils.getWindowWidth(), Utils.getWindowHeight());
            return false;
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            captured = hit(click.x(), click.y());
            if (captured != null) raise(captured);
            mouseMoved(click.x(), click.y(), click.x(), click.y());
            for (WWindow window : windows) {
                if (window != captured && window.isFocused()) window.mouseClicked(click, doubled);
            }
            if (captured == null) return false;
            captured.mouseClicked(click, doubled);
            return true;
        }

        @Override
        public boolean mouseReleased(Click click) {
            if (captured == null) return false;
            WWindow window = captured;
            captured = null;
            window.mouseReleased(click);
            return true;
        }

        @Override
        public void mouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
            pointerX = mouseX;
            pointerY = mouseY;
            WWindow hovered = captured != null ? captured : hit(mouseX, mouseY);
            for (WWindow window : windows) {
                boolean active = window == hovered;
                window.mouseMoved(active ? mouseX : Double.NaN, active ? mouseY : Double.NaN, lastMouseX, lastMouseY);
            }
        }

        @Override
        public boolean mouseScrolled(double amount) {
            WWindow window = hit(pointerX, pointerY);
            if (window == null) return false;
            raise(window);
            window.mouseScrolled(amount);
            return true;
        }
    }
}
