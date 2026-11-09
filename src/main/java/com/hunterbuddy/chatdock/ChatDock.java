package com.hunterbuddy.chatdock;

import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
import com.hunterbuddy.modules.messenger.ui.Pointer;
import com.hunterbuddy.modules.messenger.ui.Rect;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.Settings;
import com.hunterbuddy.hud.text.HudText;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import com.hunterbuddy.hud.text.OverlayPass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ChatDock {
    public static final ChatDock INSTANCE = new ChatDock();
    private static final Identifier BEE = Identifier.of("hunterbuddy", "textures/gui/bee.png");
    private final DockRegistry registry = new DockRegistry();
    private final GlassPainter glass = new GlassPainter();
    private final ModuleToastBlur blur = ModuleToastBlur.cached(125_000_000L);
    private final DockDrag drag = new DockDrag();
    private final DockMenuLayout menu = new DockMenuLayout();
    private DockEntry[] shown;
    private DockPreferences preferences;
    private Rect initial;
    private int count;
    private int font;
    private int measuredFont = -1;
    private double scale;
    private double textScale;
    private double guiScale;
    private double size;
    private double beeSize;
    private double x;
    private double y;
    private double menuWidth;
    private double rowHeight;
    private double lineHeight;
    private double screenWidth;
    private double screenHeight;
    private double chatRight;
    private double chatTop;
    private boolean enabled;
    private boolean expanded;
    private long openedAt;

    private ChatDock() {
    }

    public void configure(DockPreferences preferences, Settings settings) {
        this.preferences = preferences;
        registry.configure(settings);
        shown = new DockEntry[registry.entries().length];
    }

    public void widgets(GuiTheme theme, WVerticalList controls) { registry.widgets(theme, controls); }

    private boolean menuVisible() {
        return enabled && preferences != null && (!(MeteorClient.mc.currentScreen instanceof ChatScreen) || preferences.dockInChat());
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) registry.activate();
        else {
            registry.deactivate();
            if (MeteorClient.mc.currentScreen instanceof HunterBuddyScreen) MeteorClient.mc.setScreen(null);
            closed();
        }
    }

    public void toggleScreen() {
        if (!enabled || MeteorClient.mc.player == null) return;
        if (MeteorClient.mc.currentScreen instanceof HunterBuddyScreen) MeteorClient.mc.setScreen(null);
        else if (MeteorClient.mc.currentScreen == null) MeteorClient.mc.setScreen(new HunterBuddyScreen());
    }

    public void fontChanged() { font++; registry.fontChanged(); }

    public void resetPosition() {
        cancel();
        if (preferences != null) preferences.dockPosition(-1, -1);
    }

    public void cancel() {
        drag.cancel();
        expanded = false;
    }

    public void closed() {
        cancel();
        registry.closed();
        blur.invalidateCache();
    }

    public void shutdown() { registry.shutdown(); blur.close(); }

    public boolean inputFocused() { return enabled && registry.inputFocused(); }

    public boolean key(int key, int modifiers) { return enabled && registry.key(key, modifiers); }

    public boolean scroll(double px, double py, double amount) {
        return enabled && (contains(px, py) || registry.scroll(px, py, amount));
    }

    public boolean contains(double px, double py) {
        return menuVisible() && initial != null && (buttonContains(px, py) || expanded && menu.contains(px, py));
    }

    private boolean buttonContains(double px, double py) {
        return px >= x && px < x + size && py >= y && py < y + size;
    }

    public boolean click(double px, double py, int button) {
        if (!enabled) return false;
        if (!menuVisible() || initial == null) return registry.click(px, py, button);
        if (buttonContains(px, py)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) drag.begin(px, py, x, y);
            return true;
        }
        if (expanded && menu.contains(px, py)) {
            int hit = menu.hit(px, py, count);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit >= 0 && shown[hit].visible()) {
                shown[hit].toggle();
                expanded = false;
            }
            return true;
        }
        expanded = false;
        return registry.click(px, py, button);
    }

    public void render(DrawContext context) {
        if (!enabled || preferences == null) return;
        registry.render(context, MeteorClient.mc.currentScreen instanceof ChatScreen);
        if (!menuVisible()) { cancel(); return; }
        DockEntry[] all = registry.entries();
        int next = 0;
        boolean changed = false;
        for (int i = 0; i < all.length; i++) {
            if (!all[i].visible()) continue;
            if (shown[next] != all[i]) changed = true;
            shown[next++] = all[i];
        }
        changed |= next != count;
        for (int i = next; i < count; i++) shown[i] = null;
        count = next;
        if (changed) cancel();
        var window = MeteorClient.mc.getWindow();
        double sw = window.getFramebufferWidth();
        double sh = window.getFramebufferHeight();
        double s = preferences.dockScale();
        double t = s;
        double gui = window.getScaleFactor();
        double cs = MeteorClient.mc.options.getChatScale().getValue();
        double right = (ChatHud.getWidth(MeteorClient.mc.options.getChatWidth().getValue()) + 8 * cs) * gui;
        double top = (window.getScaledHeight() - 40) * gui - MeteorClient.mc.inGameHud.getChatHud().getVisibleLineCount()
            * (int) (9 * (MeteorClient.mc.options.getChatLineSpacing().getValue() + 1)) * cs * gui;
        HudRenderer renderer = HudRenderer.INSTANCE;
        OverlayPass.begin(context);
        try {
            if (changed || measuredFont != font || scale != s || textScale != t || guiScale != gui
                || screenWidth != sw || screenHeight != sh || chatRight != right || chatTop != top) {
                scale = s;
                textScale = t;
                guiScale = gui;
                measuredFont = font;
                screenWidth = sw;
                screenHeight = sh;
                chatRight = right;
                chatTop = top;
                beeSize = (2 * MeteorClient.mc.textRenderer.fontHeight + 2) * gui * s;
                size = beeSize + 12 * s;
                lineHeight = HudText.textHeight(renderer, false, 0.82 * t);
                rowHeight = Math.max(32 * s, lineHeight + 14 * s);
                menuWidth = 116 * s;
                for (int i = 0; i < count; i++) menuWidth = Math.max(menuWidth, HudText.textWidth(renderer, shown[i].label(), false, 0.82 * t) + 52 * s);
                initial = DockPlacement.initial(sw, sh, size, size, right, top, Math.max(32, 9 * gui));
            }
            x = DockPlacement.coordinate(preferences.dockX(), sw, size, initial.x());
            y = DockPlacement.coordinate(preferences.dockY(), sh, size, initial.y());
            double mx = Pointer.x();
            double my = Pointer.y();
            if (drag.active) {
                boolean released = drag.update(mx, my, GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS,
                    sw, sh, size, size, 4 * s);
                if (drag.moved) { x = drag.x; y = drag.y; expanded = false; }
                if (released) {
                    if (drag.moved) preferences.dockPosition(DockPlacement.fraction(x, sw, size), DockPlacement.fraction(y, sh, size));
                    else if (buttonContains(mx, my)) {
                        expanded = !expanded && count > 0;
                        openedAt = System.nanoTime();
                    }
                }
            }
            blur.begin(2);
            blur.draw(x, y, size, size, GlassPainter.radius(s), 1);
            glass.card(renderer, x, y, size, size, s, 1, true, preferences.dockBackground());
            if (expanded || buttonContains(mx, my)) glass.pill(renderer, x + 3 * s, y + 3 * s, size - 6 * s, size - 6 * s, s,
                glass.tint(preferences.dockAccent(), expanded ? 0.2 : 0.12));
            Renderer2D.COLOR.render();
            Renderer2D.COLOR.begin();
            renderer.texture(BEE, x + 6 * s, y + 6 * s, beeSize, beeSize, glass.white(1));
            if (expanded) drawMenu(renderer, sw, sh, mx, my);
        } finally {
            OverlayPass.end(context);
        }
    }

    private void drawMenu(HudRenderer renderer, double sw, double sh, double mx, double my) {
        double elapsed = Math.min(1, Math.max(0, (System.nanoTime() - openedAt) / 130_000_000.0));
        double progress = 1 - Math.pow(1 - elapsed, 3);
        menu.update(x, y, size, sw, sh, menuWidth, rowHeight, count, scale, progress);
        blur.draw(menu.x, menu.y, menu.width, menu.height, GlassPainter.radius(scale), progress);
        glass.card(renderer, menu.x, menu.y, menu.width, menu.height, scale, progress, true, preferences.dockBackground());
        int hover = menu.hit(mx, my, count);
        for (int i = 0; i < count; i++) {
            DockEntry entry = shown[i];
            double rowY = menu.y + menu.padding + i * rowHeight;
            boolean selected = entry.selected();
            if (selected || hover == i) glass.pill(renderer, menu.x + menu.padding, rowY, menu.width - 2 * menu.padding, rowHeight,
                scale, glass.tint(preferences.dockAccent(), progress * (selected ? 0.2 : 0.12)));
            entry.icon(renderer, menu.x + 14 * scale, rowY + (rowHeight - 14 * scale) / 2, 14 * scale,
                glass.tint(preferences.dockAccent(), progress * (selected || hover == i ? 1 : 0.7)));
            HudText.text(renderer, entry.label(), menu.x + 38 * scale, rowY + (rowHeight - lineHeight) / 2,
                glass.white(progress * (hover == i || selected ? 1 : 0.82)), false, 0.82 * textScale);
            if (selected) glass.pill(renderer, menu.x + menu.width - 13 * scale, rowY + rowHeight / 2 - 2 * scale,
                4 * scale, 4 * scale, scale, glass.tint(preferences.dockAccent(), progress));
        }
    }
}
