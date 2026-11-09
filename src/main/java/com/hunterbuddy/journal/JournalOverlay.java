package com.hunterbuddy.journal;

import com.hunterbuddy.hud.toast.ModuleToastBlur;
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
import net.minecraft.client.gui.hud.ChatHud;
import org.lwjgl.glfw.GLFW;

public final class JournalOverlay {
    private static JournalOverlay active;
    private final ClientLog config;
    private final JournalLayout layout = new JournalLayout();
    private final JournalRows rows = new JournalRows();
    private final JournalSelection selection = new JournalSelection();
    private final JournalPainter painter = new JournalPainter();
    private final ToDoubleFunction<String> measure;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "HunterBuddy Journal Export");
        thread.setDaemon(true);
        return thread;
    });
    private JournalStore.Snapshot snapshot;
    private boolean open;
    private boolean hidden = true;
    private boolean expanded;
    private boolean focused;
    private boolean selecting;
    private boolean dragging;
    private boolean following = true;
    private double dragX;
    private double dragY;
    private int first;
    private int font;
    private long lastScroll;
    private volatile String status = "";
    private volatile boolean saving;
    private String count = "0 messages";

    public JournalOverlay(ClientLog config) {
        this.config = config;
        measure = text -> HudRenderer.INSTANCE.textWidth(text, false, config.textScale.get());
    }

    public static JournalOverlay active() {
        return active;
    }

    public static void activate(JournalOverlay overlay) {
        overlay.hidden = true;
        active = overlay;
    }

    public static void deactivate(JournalOverlay overlay) {
        overlay.close();
        if (active == overlay) active = null;
    }

    public boolean inputFocused() {
        return open && focused;
    }

    public void close() {
        open = focused = selecting = dragging = false;
        selection.clear();
        rows.clear();
        snapshot = null;
    }

    public void shutdown() {
        writer.shutdown();
    }

    public void fontChanged() {
        font++;
    }

    public void render(DrawContext context) {
        var screen = MeteorClient.mc.getWindow();
        Utils.unscaledProjection();
        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(context);
        try {
            double gui = screen.getScaleFactor();
            double chatScale = MeteorClient.mc.options.getChatScale().getValue();
            double lineHeight = (int) (9 * (MeteorClient.mc.options.getChatLineSpacing().getValue() + 1));
            double chatRight = (ChatHud.getWidth(MeteorClient.mc.options.getChatWidth().getValue()) + 8 * chatScale) * gui;
            double chatTop = (screen.getScaledHeight() - 40) * gui
                - MeteorClient.mc.inGameHud.getChatHud().getVisibleLineCount() * lineHeight * chatScale * gui;
            layout.update(renderer, config, screen.getFramebufferWidth(), screen.getFramebufferHeight(),
                Math.max(24, gui * 18), chatRight, chatTop, expanded, font);
            double mx = Pointer.x();
            double my = Pointer.y();
            if (!open) {
                painter.icon(renderer, layout.closedButton, config, layout.closedButton.contains(mx, my));
                return;
            }
            ModuleToastBlur.INSTANCE.begin(2);
            if (dragging || selecting) hold(mx, my);
            refresh();
            painter.window(renderer, layout, config, rows, selection, first, hidden, expanded, count, status, mx, my);
        } finally {
            renderer.end();
            Utils.scaledProjection();
        }
    }

    private void refresh() {
        long id = rows.size() > first ? rows.get(first).entry().id() : 0;
        int part = rows.size() > first ? rows.get(first).part() : 0;
        if (snapshot == null || snapshot.revision() != config.store.revision()) {
            snapshot = config.store.snapshot();
            count = snapshot.entries().length + " messages";
        }
        boolean changed = rows.update(snapshot, hidden, Math.max(1, layout.content.w() - layout.prefixWidth - 5 * config.scale.get()),
            config.textScale.get(), font, measure);
        if (following) first = Math.max(0, rows.size() - layout.visible);
        else if (changed && id > 0) first = rows.index(id, part);
        clamp();
    }

    public boolean click(double x, double y, int button) {
        JournalLayout.Action action = layout.hit(x, y, open);
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
                following = false;
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
        first -= (int) Math.signum(amount) * Math.max(1, (int) Math.ceil(Math.abs(amount) * 3));
        clamp();
        following = first >= Math.max(0, rows.size() - layout.visible);
        return true;
    }

    public boolean key(int key, int modifiers) {
        if (!open || !focused) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) return true;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_C) {
            if (snapshot == null) return false;
            String copied = selection.copy(config.store.snapshot().entries(), hidden);
            if (copied.isEmpty()) return false;
            MeteorClient.mc.keyboard.setClipboard(copied);
            status = "Selected messages copied";
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_A) {
            if (snapshot != null && snapshot.entries().length > 0) {
                selection.start(snapshot.entries()[0].id(), false);
                selection.extend(snapshot.entries()[snapshot.entries().length - 1].id());
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) { following = true; first = Math.max(0, rows.size() - layout.visible); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { following = false; first = 0; return true; }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            first += (key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * Math.max(1, layout.visible - 1);
            clamp();
            following = first == Math.max(0, rows.size() - layout.visible);
            return true;
        }
        return false;
    }

    private void act(JournalLayout.Action action) {
        focused = true;
        switch (action) {
            case LOG -> { if (open) close(); else { open = true; following = true; status = ""; } }
            case CLEAR -> { config.store.clear(); selection.clear(); following = true; status = "Journal cleared"; }
            case SAVE -> save();
            case SIZE -> expanded = !expanded;
            case COORDS -> { hidden = !hidden; status = hidden ? "Coordinates are hidden" : "Coordinates are visible"; }
            case TEXT_DOWN -> config.textScale.set(Math.max(0.5, config.textScale.get() - 0.1));
            case TEXT_UP -> config.textScale.set(Math.min(1.6, config.textScale.get() + 0.1));
            case SCALE_DOWN -> config.scale.set(Math.max(0.6, config.scale.get() - 0.1));
            case SCALE_UP -> config.scale.set(Math.min(1.8, config.scale.get() + 0.1));
        }
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
            first += y < layout.content.y() ? -1 : 1;
            clamp();
            following = false;
            lastScroll = now;
        }
        int index = rowAt(y);
        if (index >= 0) selection.extend(rows.get(index).entry().id());
    }

    private int rowAt(double y) {
        if (rows.size() == 0 || layout.visible == 0) return -1;
        int offset = Math.max(0, Math.min(layout.visible - 1, (int) ((y - layout.content.y()) / layout.rowHeight)));
        return Math.min(rows.size() - 1, first + offset);
    }

    private void clamp() {
        first = Math.max(0, Math.min(Math.max(0, rows.size() - layout.visible), first));
    }

    private static boolean shift() {
        long handle = MeteorClient.mc.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}
