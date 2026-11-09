package com.hunterbuddy.journal;

import com.hunterbuddy.hud.toast.ModuleToastBlur;
import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.chatdock.ChatDock;
import java.util.function.UnaryOperator;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import com.hunterbuddy.modules.ClientLog;
import com.hunterbuddy.modules.messenger.ui.Pointer;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.ToDoubleFunction;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public final class JournalOverlay {
    private static JournalOverlay active;
    private final ClientLog config;
    private final JournalLayout layout = new JournalLayout();
    private final JournalRows rows = new JournalRows();
    private final JournalSelection selection = new JournalSelection();
    private final JournalPainter painter = new JournalPainter();
    private final ToDoubleFunction<String> measure;
    private final UnaryOperator<String> prefix;
    private final JournalBuffer buffer = new JournalBuffer();
    private final ModuleToastBlur blur = ModuleToastBlur.cached(125_000_000L);
    private final JournalProfile profile = new JournalProfile();
    private boolean profiling;
    private boolean chatSeen;
    private long refreshAt;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "HunterBuddy Journal Export");
        thread.setDaemon(true);
        return thread;
    });
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
    private volatile boolean saving;
    private String count = "0 messages";

    public JournalOverlay(ClientLog config) {
        this.config = config;
        measure = text -> HudRenderer.INSTANCE.textWidth(text, false, config.textScale.get());
        prefix = text -> GlassPainter.fit(HudRenderer.INSTANCE, text, Math.max(1, layout.prefixWidth - 8 * config.scale.get()),
            0.65 * config.textScale.get());
    }

    public static JournalOverlay active() {
        return active;
    }

    public static void activate(JournalOverlay overlay) {
        overlay.hidden = true;
        overlay.chatSeen = false;
        active = overlay;
    }

    public static void deactivate(JournalOverlay overlay) {
        overlay.chatClosed();
        if (active == overlay) active = null;
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
            status = "";
        }
    }

    public void chatClosed() {
        close();
        chatSeen = false;
        profile.pause();
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

    public void render(DrawContext context) {
        if (!chatSeen) {
            chatSeen = true;
            if (config.autoOpen.get()) { open = true; focused = false; rows.end(); refreshAt = 0; }
        }
        boolean measuring = config.profilePerformance.get();
        if (measuring && !profiling) profile.clear();
        if (!measuring && profiling) profile.pause();
        profiling = measuring;
        long now = System.nanoTime();
        if (measuring) profile.frame(now, open);
        if (!open) return;
        var screen = MeteorClient.mc.getWindow();
        Utils.unscaledProjection();
        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(context);
        long stamp = measuring ? System.nanoTime() : 0;
        try {
            layout.update(renderer, config, screen.getFramebufferWidth(), screen.getFramebufferHeight(),
                Math.max(24, screen.getScaleFactor() * 18), expanded, font);
            if (measuring) { profile.add(JournalProfile.Stage.LAYOUT, System.nanoTime() - stamp); stamp = System.nanoTime(); }
            if (now >= refreshAt) {
                if (config.store.read(buffer)) count = buffer.size() + " messages";
                refreshAt = now + 250_000_000L;
            }
            if (measuring) { profile.add(JournalProfile.Stage.BUFFER, System.nanoTime() - stamp); stamp = System.nanoTime(); }
            rows.update(buffer, hidden, Math.max(1, layout.content.w() - layout.prefixWidth - 5 * config.scale.get()),
                config.textScale.get(), font, layout.visible, measure, prefix);
            double mx = Pointer.x();
            double my = Pointer.y();
            if (dragging || selecting) hold(mx, my);
            if (measuring) { profile.add(JournalProfile.Stage.ROWS, System.nanoTime() - stamp); stamp = System.nanoTime(); }
            blur.begin(2);
            blur.draw(layout.window.x(), layout.window.y(), layout.window.w(), layout.window.h(), GlassPainter.radius(config.scale.get()), 1);
            if (measuring) { profile.add(JournalProfile.Stage.BLUR_SUBMIT, System.nanoTime() - stamp); stamp = System.nanoTime(); }
            painter.window(renderer, layout, config, rows, selection, hidden, expanded, count, status, mx, my);
            if (measuring) profile.add(JournalProfile.Stage.DRAW_SUBMIT, System.nanoTime() - stamp);
        } finally {
            stamp = measuring ? System.nanoTime() : 0;
            renderer.end();
            Utils.scaledProjection();
            if (measuring) profile.add(JournalProfile.Stage.TEXT_FLUSH, System.nanoTime() - stamp);
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
            case LOG -> toggle();
            case CLEAR -> { config.store.clear(); selection.clear(); rows.end(); refreshAt = 0; status = "Journal cleared"; }
            case SAVE -> save();
            case SIZE -> expanded = !expanded;
            case COORDS -> { hidden = !hidden; status = hidden ? "Coordinates are hidden" : "Coordinates are visible"; }
            case TEXT_DOWN -> config.textScale.set(Math.max(0.5, config.textScale.get() - 0.1));
            case TEXT_UP -> config.textScale.set(Math.min(1.6, config.textScale.get() + 0.1));
            case SCALE_DOWN -> config.scale.set(Math.max(0.6, config.scale.get() - 0.1));
            case SCALE_UP -> config.scale.set(Math.min(1.8, config.scale.get() + 0.1));
        }
    }

    public void savePerformance() {
        String report = profile.report();
        var directory = MeteorClient.mc.runDirectory.toPath().resolve("hunterbuddy/logs");
        writer.execute(() -> {
            try {
                Files.createDirectories(directory);
                var target = directory.resolve("log-performance-" + System.currentTimeMillis() + ".txt");
                Files.writeString(target, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                status = "Performance report saved";
            } catch (IOException exception) {
                status = "Could not save performance report";
            }
        });
    }

    private void save() {
        if (saving) return;
        JournalEntry[] entries = config.store.snapshot().entries();
        boolean censor = hidden;
        var directory = MeteorClient.mc.runDirectory.toPath();
        saving = true;
        status = "Saving journal...";
        writer.execute(() -> {
            try {
                status = "Saved " + JournalExport.save(directory, entries, censor).getFileName();
            } catch (IOException exception) {
                status = "Could not save the journal";
            } finally {
                saving = false;
            }
        });
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
