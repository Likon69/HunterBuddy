package com.hunterbuddy.journal;

import com.hunterbuddy.hud.toast.ModuleToastBlur;
import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.chatdock.ChatDock;
import com.hunterbuddy.HunterBuddyAddon;
import java.util.function.UnaryOperator;
import com.hunterbuddy.modules.messenger.ui.Pointer;
import java.util.function.ToDoubleFunction;
import meteordevelopment.meteorclient.MeteorClient;
import com.hunterbuddy.hud.text.HudText;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import com.hunterbuddy.hud.text.OverlayPass;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public final class JournalOverlay {
    private final JournalSettings config;
    private final JournalLayout layout = new JournalLayout();
    private final JournalRows rows = new JournalRows();
    private final JournalSelection selection = new JournalSelection();
    private final JournalPainter painter = new JournalPainter();
    private final ToDoubleFunction<String> measure;
    private final UnaryOperator<String> prefix;
    private final JournalBuffer buffer = new JournalBuffer();
    private final ModuleToastBlur blur = ModuleToastBlur.cached(125_000_000L);
    private boolean chatSeen;
    private long refreshAt;
    private final JournalWriter writer = new JournalWriter(message -> status = message,
        (message, error) -> HunterBuddyAddon.LOG.error(message, error), message -> HunterBuddyAddon.LOG.info(message));
    private boolean open;
    private boolean hidden = true;
    private boolean expanded;
    private boolean focused;
    private boolean selecting;
    private boolean dragging;
    private double dragX;
    private double dragY;
    private int font;
    private long lastScroll;
    private volatile String status = "";
    private String count = "0 messages";

    public JournalOverlay(JournalSettings config) {
        this.config = config;
        measure = text -> HudText.textWidth(HudRenderer.INSTANCE, text, false, config.textScale.get());
        prefix = text -> GlassPainter.fit(HudRenderer.INSTANCE, text, Math.max(1, layout.prefixWidth - 8 * config.scale.get()),
            0.65 * config.textScale.get());
    }

    public void reset() {
        hidden = config.hideCoordinates.get();
        chatSeen = false;
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        if (open) close();
        else {
            open = focused = true;
            rows.end();
            refreshAt = 0;
        }
    }

    public void chatClosed() {
        close();
        chatSeen = false;
        ChatDock.INSTANCE.cancel();
    }

    public boolean inputFocused() {
        return open && focused;
    }

    public void close() {
        open = focused = selecting = dragging = false;
        selection.clear();
        rows.clear();
        buffer.clear();
        refreshAt = 0;
        blur.invalidateCache();
    }

    public void shutdown() {
        writer.shutdown();
        blur.close();
    }

    public void fontChanged() {
        font++;
    }

    public void render(DrawContext context, boolean chat) {
        if (chat && !chatSeen) {
            chatSeen = true;
            if (config.autoOpen.get()) { open = true; focused = false; rows.end(); refreshAt = 0; }
        }
        hidden = config.hideCoordinates.get();
        long now = System.nanoTime();
        if (!open) return;
        var screen = MeteorClient.mc.getWindow();
        HudRenderer renderer = HudRenderer.INSTANCE;
        OverlayPass.begin(context);
        try {
            layout.update(renderer, config, screen.getFramebufferWidth(), screen.getFramebufferHeight(),
                Math.max(24, screen.getScaleFactor() * 18), expanded, font);
            if (now >= refreshAt) {
                if (config.store.read(buffer)) count = buffer.size() + " messages";
                refreshAt = now + 250_000_000L;
            }
            rows.update(buffer, hidden, Math.max(1, layout.content.w() - layout.prefixWidth - 5 * config.scale.get()),
                config.textScale.get(), font, layout.visible, measure, prefix);
            double mx = Pointer.x();
            double my = Pointer.y();
            if (dragging || selecting) hold(mx, my);
            blur.begin(2);
            blur.draw(layout.window.x(), layout.window.y(), layout.window.w(), layout.window.h(), GlassPainter.radius(config.scale.get()), 1);
            painter.window(renderer, layout, config, rows, selection, hidden, expanded, count, status, mx, my);
        } finally {
            OverlayPass.end(context);
        }
    }

    public boolean click(double x, double y, int button) {
        JournalLayout.Action action = open && layout.window != null ? layout.hit(x, y) : null;
        if (action != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) act(action);
            return true;
        }
        if (!open || layout.window == null || !layout.window.contains(x, y)) {
            focused = false;
            return false;
        }
        focused = true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (layout.content.contains(x, y)) {
            int index = rowAt(y);
            if (index >= 0) {
                selection.start(rows.get(index).entry().id(), shift());
                selecting = true;
                rows.pause();
            }
        } else if (y < layout.window.y() + layout.headerHeight && !expanded) {
            dragging = true;
            dragX = x - layout.window.x();
            dragY = y - layout.window.y();
        }
        return true;
    }

    public boolean scroll(double x, double y, double amount) {
        if (!open || layout.window == null || !layout.window.contains(x, y)) return false;
        rows.scroll(-(int) Math.signum(amount) * Math.max(1, (int) Math.ceil(Math.abs(amount) * 3)));
        return true;
    }

    public boolean key(int key, int modifiers) {
        if (!open || !focused) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) return true;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_C) {
            String copied = selection.copy(config.store.snapshot().entries(), hidden);
            if (copied.isEmpty()) return false;
            MeteorClient.mc.keyboard.setClipboard(copied);
            status = "Selected messages copied";
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_A) {
            if (buffer.size() > 0) {
                selection.start(buffer.get(0).id(), false);
                selection.extend(buffer.get(buffer.size() - 1).id());
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) { rows.end(); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { rows.home(); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            rows.scroll((key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * Math.max(1, layout.visible - 1));
            return true;
        }
        return false;
    }

    private void act(JournalLayout.Action action) {
        focused = true;
        switch (action) {
            case LOG, CLOSE -> close();
            case CLEAR -> { config.store.clear(); selection.clear(); rows.end(); refreshAt = 0; status = "Journal cleared"; }
            case SAVE -> save();
            case SIZE -> expanded = !expanded;
            case COORDS -> { hidden = !hidden; config.hideCoordinates.set(hidden); status = hidden ? "Coordinates are hidden" : "Coordinates are visible"; }
            case TEXT_DOWN -> config.textScale.set(Math.max(0.5, config.textScale.get() - 0.1));
            case TEXT_UP -> config.textScale.set(Math.min(1.6, config.textScale.get() + 0.1));
            case SCALE_DOWN -> config.scale.set(Math.max(0.6, config.scale.get() - 0.1));
            case SCALE_UP -> config.scale.set(Math.min(1.8, config.scale.get() + 0.1));
        }
    }

    private void save() {
        try {
            JournalEntry[] entries = config.store.snapshot().entries();
            boolean censor = hidden;
            var directory = MeteorClient.FOLDER.toPath().toAbsolutePath().normalize();
            writer.save("journal", () -> JournalExport.save(directory, entries, censor));
        } catch (RuntimeException error) {
            writer.failed("journal", error);
        }
    }

    private void hold(double x, double y) {
        if (GLFW.glfwGetMouseButton(MeteorClient.mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            selecting = dragging = false;
            return;
        }
        if (dragging) {
            layout.move(x - dragX, y - dragY);
            return;
        }
        long now = System.nanoTime();
        if (now - lastScroll > 70_000_000L && (y < layout.content.y() || y > layout.content.y() + layout.content.h())) {
            rows.scroll(y < layout.content.y() ? -1 : 1);
            lastScroll = now;
        }
        int index = rowAt(y);
        if (index >= 0) selection.extend(rows.get(index).entry().id());
    }

    private int rowAt(double y) {
        if (rows.size() == 0 || layout.visible == 0) return -1;
        int offset = Math.max(0, Math.min(layout.visible - 1, (int) ((y - layout.content.y()) / layout.rowHeight)));
        return Math.min(rows.size() - 1, offset);
    }

    private static boolean shift() {
        long handle = MeteorClient.mc.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}
