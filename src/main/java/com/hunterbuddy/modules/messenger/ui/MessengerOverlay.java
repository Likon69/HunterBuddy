package com.hunterbuddy.modules.messenger.ui;

import com.hunterbuddy.hud.MessengerBubbleHud;
import com.hunterbuddy.modules.messenger.Conversation;
import com.hunterbuddy.modules.messenger.Messenger;
import com.hunterbuddy.modules.messenger.MessengerSettings;
import com.hunterbuddy.modules.messenger.MessengerStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

public final class MessengerOverlay {
    private static final double DRAG_THRESHOLD = 4.0;

    private final Messenger messenger;
    private final ContactList list = new ContactList();
    private final List<ConversationWindow> windows = new ArrayList<>();
    private final Map<String, MessengerStore.Spot> remembered = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private boolean listOpen;
    private ConversationWindow focused;
    private ConversationWindow active;
    private ConversationWindow selecting;
    private ConversationWindow pressing;
    private boolean pressMoved;

    private boolean bubbleDrag;
    private ConversationWindow windowDrag;
    private double dragOffsetX;
    private double dragOffsetY;
    private double dragStartX;
    private double dragStartY;
    private boolean dragMoved;

    public MessengerOverlay(Messenger messenger) {
        this.messenger = messenger;
    }

    public boolean inputFocused() {
        return focused != null;
    }

    public void restore(Map<String, MessengerStore.Spot> spots) {
        windows.clear();
        remembered.clear();
        remembered.putAll(spots);
        forgetPointer();
        focused = null;

        for (Map.Entry<String, MessengerStore.Spot> entry : spots.entrySet()) {
            if (entry.getValue().open() && messenger.existing(entry.getKey()) != null) openWindow(entry.getKey(), false);
        }
    }

    public Map<String, MessengerStore.Spot> spots() {
        Window window = MeteorClient.mc.getWindow();
        Map<String, MessengerStore.Spot> result = new LinkedHashMap<>(remembered);
        for (ConversationWindow w : windows) {
            result.put(w.peer(), new MessengerStore.Spot(
                w.x() / window.getFramebufferWidth(), w.y() / window.getFramebufferHeight(), true));
        }
        return result;
    }

    public void reset() {
        focused = null;
        listOpen = false;
        bubbleDrag = false;
        windowDrag = null;
        forgetPointer();
        clearSelections();
    }

    public void openWindow(String peer, boolean focus) {
        Conversation conversation = messenger.existing(peer);
        if (conversation == null) return;

        ConversationWindow window = find(conversation.peer());

        if (window == null) {
            window = new ConversationWindow(conversation.peer());
            Window screen = MeteorClient.mc.getWindow();
            double sw = screen.getFramebufferWidth();
            double sh = screen.getFramebufferHeight();

            MessengerStore.Spot spot = remembered.get(conversation.peer());
            if (spot != null) window.place(spot.fx() * sw, spot.fy() * sh);
            else window.place(sw * 0.3 + windows.size() * 28.0, sh * 0.2 + windows.size() * 28.0);

            windows.add(window);
            messenger.markDirty();
        } else {
            windows.remove(window);
            windows.add(window);
        }

        if (focus) focused = window;
    }

    public void closeWindow(String peer) {
        ConversationWindow window = find(peer);
        if (window == null) return;

        Window screen = MeteorClient.mc.getWindow();
        remembered.put(window.peer(), new MessengerStore.Spot(
            window.x() / screen.getFramebufferWidth(), window.y() / screen.getFramebufferHeight(), false));

        windows.remove(window);
        if (focused == window) focused = null;
        if (windowDrag == window) windowDrag = null;
        if (active == window) active = null;
        if (selecting == window) selecting = null;
        if (pressing == window) pressing = null;
        messenger.markDirty();
    }

    public void closeAll() {
        windows.clear();
        remembered.clear();
        focused = null;
        windowDrag = null;
        forgetPointer();
    }

    public void render(DrawContext context) {
        if (!messenger.isActive()) return;

        Window screen = MeteorClient.mc.getWindow();
        int sw = screen.getFramebufferWidth();
        int sh = screen.getFramebufferHeight();
        double mx = Pointer.x();
        double my = Pointer.y();
        MessengerSettings cfg = messenger.cfg();

        updateDrag(mx, my, sw, sh);
        updatePointerHold(mx, my);

        if (cfg.markReadOnOpen.get()) {
            for (ConversationWindow window : windows) {
                Conversation conversation = messenger.existing(window.peer());
                if (conversation != null && conversation.unread() > 0) {
                    conversation.markRead();
                    messenger.markDirty();
                }
            }
        }

        MessengerBubbleHud bubble = MessengerBubbleHud.find();
        if (bubble == null) listOpen = false;

        Utils.unscaledProjection();
        try {
            for (ConversationWindow window : new ArrayList<>(windows)) {
                boolean hasFocus = window == focused;
                layer(context, cfg, p -> window.render(p, messenger, mx, my, hasFocus, sw, sh));
            }

            if (listOpen && bubble != null) {
                layer(context, cfg, p -> list.render(p, messenger, bubble.hitRect(), mx, my, sw, sh));
            }
        } finally {
            Utils.scaledProjection();
        }
    }

    public boolean mouseClicked(double mx, double my, int button, boolean doubled) {
        if (!messenger.isActive()) return false;

        if (listOpen) {
            ContactList.Result result = list.click(mx, my);
            switch (result.kind()) {
                case INSIDE -> {
                    return true;
                }
                case OPEN -> {
                    openWindow(result.peer(), true);
                    return true;
                }
                case REMOVE -> {
                    messenger.remove(result.peer());
                    return true;
                }
                case CLEAR_ALL -> {
                    messenger.clearAll();
                    return true;
                }
                default -> {
                }
            }
        }

        for (int i = windows.size() - 1; i >= 0; i--) {
            ConversationWindow window = windows.get(i);
            ConversationWindow.Hit hit = window.hit(mx, my);
            if (hit == ConversationWindow.Hit.OUTSIDE) continue;
            if (button == 1) {
                window.copyAt(mx, my);
                return true;
            }
            if (button != 0) return true;

            windows.remove(window);
            windows.add(window);

            Conversation conversation = messenger.existing(window.peer());
            if (conversation != null) conversation.markRead();

            switch (hit) {
                case CLOSE -> closeWindow(window.peer());
                case CLEAR -> {
                    if (window.confirm(hit)) messenger.clear(window.peer());
                }
                case IGNORE -> {
                    if (window.confirm(hit)) messenger.ignore(window.peer());
                }
                case HEADER -> {
                    clearSelections();
                    focused = window;
                    if (!messenger.cfg().lockPositions.get()) {
                        windowDrag = window;
                        dragOffsetX = mx - window.x();
                        dragOffsetY = my - window.y();
                        dragStartX = mx;
                        dragStartY = my;
                        dragMoved = false;
                    }
                }
                case INPUT -> {
                    clearSelections();
                    focused = window;
                    window.requestPress(mx, doubled ? ConversationWindow.Press.WORD : ConversationWindow.Press.CLICK);
                    if (!doubled) startHold(null, window, mx, my);
                }
                default -> pressBody(window, mx, my, doubled);
            }
            return true;
        }

        MessengerBubbleHud bubble = MessengerBubbleHud.find();
        if (bubble != null && bubble.hits(mx, my)) {
            if (button == 0) {
                bubbleDrag = true;
                dragOffsetX = mx - bubble.x;
                dragOffsetY = my - bubble.y;
                dragStartX = mx;
                dragStartY = my;
                dragMoved = false;
            }
            return true;
        }

        focused = null;
        active = null;
        listOpen = false;
        clearSelections();
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double vertical) {
        if (!messenger.isActive()) return false;

        if (listOpen && list.contains(mx, my)) {
            list.scroll(vertical);
            return true;
        }

        for (int i = windows.size() - 1; i >= 0; i--) {
            ConversationWindow window = windows.get(i);
            if (!window.contains(mx, my)) continue;
            window.scroll(vertical);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int key, int modifiers) {
        if (!messenger.isActive()) return false;

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        ConversationWindow selected = selectedWindow();

        if (selected != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                clearSelections();
                return true;
            }
            if (ctrl && key == GLFW.GLFW_KEY_C && (focused == null || !focused.inputHasSelection())) {
                selected.copySelection(messenger);
                return true;
            }
        }

        if (focused == null) {
            if (ctrl && key == GLFW.GLFW_KEY_A && active != null) {
                active.selectAll(messenger);
                return true;
            }
            return false;
        }

        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> focused = null;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> focused.submit(messenger);
            default -> focused.key(key, modifiers);
        }
        return true;
    }

    public boolean charTyped(int codepoint) {
        if (focused == null || !messenger.isActive()) return false;

        if (!Character.isISOControl(codepoint)) focused.type(new String(Character.toChars(codepoint)));
        return true;
    }

    private void pressBody(ConversationWindow window, double mx, double my, boolean doubled) {
        for (ConversationWindow other : windows) {
            if (other != window) other.clearSelection();
        }
        active = window;

        if (doubled) {
            window.selectionWord(mx, my);
            return;
        }
        window.selectionBegin(mx, my);
        startHold(window, null, mx, my);
    }

    private void startHold(ConversationWindow selectingWindow, ConversationWindow pressingWindow, double mx, double my) {
        selecting = selectingWindow;
        pressing = pressingWindow;
        pressMoved = false;
        dragStartX = mx;
        dragStartY = my;
    }

    private void updatePointerHold(double mx, double my) {
        if (selecting == null && pressing == null) return;

        boolean down = GLFW.glfwGetMouseButton(MeteorClient.mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!down) {
            selecting = null;
            pressing = null;
            return;
        }

        if (!pressMoved && Math.hypot(mx - dragStartX, my - dragStartY) >= DRAG_THRESHOLD) pressMoved = true;
        if (!pressMoved) return;

        if (selecting != null) selecting.selectionExtend(mx, my);
        else pressing.requestPress(mx, ConversationWindow.Press.DRAG);
    }

    private ConversationWindow selectedWindow() {
        for (ConversationWindow window : windows) {
            if (window.hasSelection()) return window;
        }
        return null;
    }

    private void clearSelections() {
        for (ConversationWindow window : windows) window.clearSelection();
    }

    private void forgetPointer() {
        active = null;
        selecting = null;
        pressing = null;
    }

    private void updateDrag(double mx, double my, int sw, int sh) {
        if (!bubbleDrag && windowDrag == null) return;

        boolean down = GLFW.glfwGetMouseButton(MeteorClient.mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (!down) {
            finishDrag();
            return;
        }

        if (!dragMoved && Math.hypot(mx - dragStartX, my - dragStartY) >= DRAG_THRESHOLD) dragMoved = true;
        if (!dragMoved) return;

        if (windowDrag != null) {
            windowDrag.place(mx - dragOffsetX, my - dragOffsetY);
            return;
        }

        MessengerBubbleHud bubble = MessengerBubbleHud.find();
        if (bubble == null) return;

        int nx = (int) Math.max(0, Math.min(mx - dragOffsetX, sw - bubble.getWidth()));
        int ny = (int) Math.max(0, Math.min(my - dragOffsetY, sh - bubble.getHeight()));
        bubble.setPos(nx, ny);
    }

    private void finishDrag() {
        if (bubbleDrag) {
            if (dragMoved) Hud.get().save();
            else {
                listOpen = !listOpen;
                if (listOpen) list.restartFade();
            }
        }
        if (windowDrag != null && dragMoved) messenger.markDirty();

        bubbleDrag = false;
        windowDrag = null;
    }

    private ConversationWindow find(String peer) {
        for (ConversationWindow window : windows) {
            if (window.peer().equalsIgnoreCase(peer)) return window;
        }
        return null;
    }

    private void layer(DrawContext context, MessengerSettings cfg, Consumer<Painter> body) {
        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(context);
        try {
            body.accept(new Painter(renderer, cfg.textScale.get(), cfg.textShadow.get()));
        } finally {
            renderer.end();
        }
    }
}
