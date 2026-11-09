package com.hunterbuddy.chatdock;

import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.modules.messenger.ui.Pointer;
import com.hunterbuddy.modules.messenger.ui.Rect;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.lwjgl.glfw.GLFW;

public final class ChatDock {
    public static final ChatDock INSTANCE = new ChatDock();
    private final List<DockEntry> entries = new ArrayList<>();
    private final GlassPainter glass = new GlassPainter();
    private final DockDrag drag = new DockDrag();
    private final DockEntry[] shown = new DockEntry[32];
    private final double[] widths = new double[32];
    private final double[] offsets = new double[32];
    private DockPreferences preferences;
    private Rect bounds;
    private int count;
    private int pressed = -1;
    private int font;
    private int measuredFont = -1;
    private double scale;
    private double textScale;
    private double measuredScreen;
    private double width;
    private double height;
    private double lineHeight;
    private Rect initial;
    private double initialScreenWidth;
    private double initialScreenHeight;
    private double initialChatRight;
    private double initialChatTop;
    private double initialGap;

    private ChatDock() {
    }

    public void configure(DockPreferences preferences) {
        this.preferences = preferences;
    }

    public void register(DockEntry entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(entry.id())) {
                entries.set(i, entry);
                measuredFont = -1;
                return;
            }
        }
        if (entries.size() >= shown.length) throw new IllegalStateException("Dock capacity reached");
        entries.add(entry);
        measuredFont = -1;
    }

    public void fontChanged() {
        font++;
    }

    public void resetPosition() {
        cancel();
        if (preferences != null) preferences.dockPosition(-1, -1);
    }

    public void cancel() {
        drag.cancel();
        pressed = -1;
    }

    public boolean click(double x, double y, int button) {
        if (bounds == null || count == 0 || !bounds.contains(x, y)) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = hit(x);
            drag.begin(x, y, bounds.x(), bounds.y());
        }
        return true;
    }

    public boolean contains(double x, double y) {
        return bounds != null && count > 0 && bounds.contains(x, y);
    }

    private int hit(double x) {
        double left = bounds.x() + 20 * scale;
        for (int i = 0; i < count; i++) {
            if (x >= left && x < left + widths[i]) return i;
            left += widths[i] + 4 * scale;
        }
        return -1;
    }

    public void render(DrawContext context) {
        if (preferences == null) return;
        int next = 0;
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            DockEntry entry = entries.get(i);
            if (!entry.visible().getAsBoolean()) continue;
            if (shown[next] != entry) changed = true;
            shown[next++] = entry;
        }
        changed |= next != count;
        for (int i = next; i < count; i++) shown[i] = null;
        if (changed) cancel();
        count = next;
        if (count == 0) { bounds = null; cancel(); return; }
        var window = MeteorClient.mc.getWindow();
        double sw = window.getFramebufferWidth();
        double sh = window.getFramebufferHeight();
        double s = preferences.dockScale();
        double t = preferences.dockTextScale();
        HudRenderer renderer = HudRenderer.INSTANCE;
        Utils.unscaledProjection();
        renderer.begin(context);
        try {
            if (changed || measuredFont != font || scale != s || textScale != t || measuredScreen != sw) {
                scale = s;
                textScale = t;
                measuredFont = font;
                measuredScreen = sw;
                lineHeight = renderer.textHeight(false, 0.82 * t);
                height = Math.max(34 * s, lineHeight + 14 * s);
                width = 28 * s;
                for (int i = 0; i < count; i++) {
                    double label = renderer.textWidth(shown[i].label(), false, 0.82 * t);
                    widths[i] = Math.max(48 * s, label + 22 * s);
                    offsets[i] = (widths[i] - label) / 2;
                    width += widths[i] + 4 * s;
                }
            }
            double gui = window.getScaleFactor();
            double cs = MeteorClient.mc.options.getChatScale().getValue();
            double chatRight = (ChatHud.getWidth(MeteorClient.mc.options.getChatWidth().getValue()) + 8 * cs) * gui;
            double chatTop = (window.getScaledHeight() - 40) * gui - MeteorClient.mc.inGameHud.getChatHud().getVisibleLineCount()
                * (int) (9 * (MeteorClient.mc.options.getChatLineSpacing().getValue() + 1)) * cs * gui;
            double gap = Math.max(32, 9 * gui);
            if (initial == null || initialScreenWidth != sw || initialScreenHeight != sh || initial.w() != width || initial.h() != height
                || initialChatRight != chatRight || initialChatTop != chatTop || initialGap != gap) {
                initial = DockPlacement.initial(sw, sh, width, height, chatRight, chatTop, gap);
                initialScreenWidth = sw;
                initialScreenHeight = sh;
                initialChatRight = chatRight;
                initialChatTop = chatTop;
                initialGap = gap;
            }
            double x = DockPlacement.coordinate(preferences.dockX(), sw, width, initial.x());
            double y = DockPlacement.coordinate(preferences.dockY(), sh, height, initial.y());
            double mx = Pointer.x();
            double my = Pointer.y();
            if (drag.active) {
                boolean released = drag.update(mx, my, GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS,
                    sw, sh, width, height, 4 * s);
                if (drag.moved) { x = drag.x; y = drag.y; }
                if (released) {
                    if (drag.moved) preferences.dockPosition(DockPlacement.fraction(x, sw, width), DockPlacement.fraction(y, sh, height));
                    else if (pressed >= 0 && pressed < count && bounds.contains(mx, my) && hit(mx) == pressed) shown[pressed].action().run();
                    pressed = -1;
                }
            }
            if (bounds == null || bounds.x() != x || bounds.y() != y || bounds.w() != width || bounds.h() != height)
                bounds = new Rect(x, y, width, height);
            glass.card(renderer, x, y, width, height, s, 1, true, preferences.dockBackground());
            for (int i = 0; i < 3; i++) renderer.quad(x + 9 * s, y + height / 2 + (i - 1) * 5 * s, 3 * s, 2 * s, glass.white(0.3));
            double left = x + 20 * s;
            for (int i = 0; i < count; i++) {
                boolean selected = shown[i].selected().getAsBoolean();
                boolean hover = mx >= left && mx < left + widths[i] && my >= y && my < y + height;
                if (selected || hover) glass.pill(renderer, left, y + 4 * s, widths[i], height - 8 * s, s,
                    glass.tint(preferences.dockAccent(), selected ? 0.2 : 0.1));
                renderer.text(shown[i].label(), left + offsets[i], y + (height - lineHeight) / 2,
                    selected || hover ? preferences.dockAccent() : glass.white(0.85), false, 0.82 * t);
                left += widths[i] + 4 * s;
            }
        } finally {
            renderer.end();
            Utils.scaledProjection();
        }
    }
}
