package com.hunterbuddy.modules.messenger.ui;

import com.hunterbuddy.modules.messenger.Conversation;
import com.hunterbuddy.modules.messenger.Messenger;
import com.hunterbuddy.modules.messenger.MessengerSettings;
import com.hunterbuddy.modules.messenger.PrivateMessage;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class ConversationWindow {
    public enum Hit {
        OUTSIDE,
        BODY,
        HEADER,
        CLOSE,
        CLEAR,
        IGNORE,
        INPUT
    }

    enum Press {
        CLICK,
        DRAG,
        WORD
    }

    private enum Select {
        BEGIN,
        EXTEND,
        WORD,
        COPY
    }

    private record PendingSelect(Select kind, double x, double y) {
    }

    private record PendingPress(Press kind, double relativeX) {
    }

    private static final String[] BUTTONS = {"x", "ignore", "clear"};
    private static final long FADE_MS = 180L;
    private static final long CONFIRM_MS = 3000L;
    private static final long COPIED_MS = 1200L;
    private static final long AUTO_SCROLL_MS = 120L;
    private static final Color WHITE_FAINT = new Color(255, 255, 255, 14);
    private static final Color WHITE_CLEAR = new Color(255, 255, 255, 0);

    private final String peer;
    private final TextSelection selection = new TextSelection();
    private final List<PendingSelect> pendingSelect = new ArrayList<>();
    private final List<PendingPress> pendingPress = new ArrayList<>();
    private List<RowHit> hits = new ArrayList<>();
    private List<RowHit> nextHits = new ArrayList<>();
    private final InputField input = new InputField();
    private final MessageLayout layout = new MessageLayout();

    private double x;
    private double y;
    private boolean placed;
    private long openedAt = System.currentTimeMillis();
    private int scrollRows;
    private Hit armed;
    private long armedAt;
    private double inputTextX;
    private long copiedAt;
    private long lastAutoScroll;
    private PrivateMessage selectionHead;

    private Rect bounds = Rect.NONE;
    private Rect header = Rect.NONE;
    private Rect inputBox = Rect.NONE;
    private Rect area = Rect.NONE;
    private final Rect[] buttons = {Rect.NONE, Rect.NONE, Rect.NONE};

    public ConversationWindow(String peer) {
        this.peer = peer;
    }

    public String peer() {
        return peer;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public void place(double nx, double ny) {
        x = nx;
        y = ny;
        placed = true;
    }

    public boolean confirm(Hit action) {
        long now = System.currentTimeMillis();
        if (armed == action && now - armedAt <= CONFIRM_MS) {
            armed = null;
            return true;
        }

        armed = action;
        armedAt = now;
        return false;
    }

    public void scroll(double vertical) {
        scrollRows = Math.max(0, scrollRows + (int) Math.round(vertical * 3.0));
    }

    public void submit(Messenger messenger) {
        if (messenger.send(peer, input.text())) {
            input.clear();
            scrollRows = 0;
        }
    }

    public void key(int key, int modifiers) {
        input.key(key, modifiers);
    }

    public void type(String text) {
        input.insert(text);
    }

    void requestPress(double mouseX, Press kind) {
        pendingPress.add(new PendingPress(kind, mouseX - inputTextX));
    }

    void selectionBegin(double mx, double my) {
        pendingSelect.add(new PendingSelect(Select.BEGIN, mx, my));
    }

    void selectionExtend(double mx, double my) {
        PendingSelect last = pendingSelect.isEmpty() ? null : pendingSelect.get(pendingSelect.size() - 1);
        if (last != null && last.kind() == Select.EXTEND) pendingSelect.remove(pendingSelect.size() - 1);
        pendingSelect.add(new PendingSelect(Select.EXTEND, mx, my));
    }

    void selectionWord(double mx, double my) {
        pendingSelect.add(new PendingSelect(Select.WORD, mx, my));
    }

    void copyAt(double mx, double my) {
        pendingSelect.add(new PendingSelect(Select.COPY, mx, my));
    }

    boolean hasSelection() {
        return !selection.isEmpty();
    }

    boolean inputHasSelection() {
        return input.hasSelection();
    }

    void clearSelection() {
        selection.clear();
        pendingSelect.clear();
    }

    void selectAll(Messenger messenger) {
        Conversation conversation = messenger.existing(peer);
        if (conversation == null || messenger.cfg().streamMode.get()) return;
        selection.selectAll(conversation.messages());
    }

    boolean copySelection(Messenger messenger) {
        Conversation conversation = messenger.existing(peer);
        if (conversation == null) return false;
        return copy(conversation.messages(), messenger.cfg().copyWithNames.get());
    }

    public Hit hit(double mx, double my) {
        if (!bounds.contains(mx, my)) return Hit.OUTSIDE;
        for (int i = 0; i < BUTTONS.length; i++) {
            if (buttons[i].contains(mx, my)) return i == 0 ? Hit.CLOSE : i == 1 ? Hit.IGNORE : Hit.CLEAR;
        }
        if (header.contains(mx, my)) return Hit.HEADER;
        if (inputBox.contains(mx, my)) return Hit.INPUT;
        return Hit.BODY;
    }

    public boolean contains(double mx, double my) {
        return bounds.contains(mx, my);
    }

    public void render(Painter p, Messenger messenger, double mx, double my, boolean focused, int sw, int sh) {
        MessengerSettings cfg = messenger.cfg();
        double u = p.lineHeight();
        double pad = Math.round(u * 0.5);
        double rowH = u + cfg.lineSpacing.get();
        double radius = cfg.cornerRadius.get();
        double w = Math.min(cfg.windowWidth.get(), sw - 8.0);
        double headerH = u + pad * 2.0;
        double areaH = cfg.windowLines.get() * rowH + pad;
        double inputH = u + pad * 1.4;
        double h = headerH + areaH + inputH + pad;

        if (!placed) {
            x = sw * 0.3;
            y = sh * 0.2;
            placed = true;
        }
        x = Math.max(0.0, Math.min(x, sw - w));
        y = Math.max(0.0, Math.min(y, sh - h));

        double progress = 1.0;
        if (cfg.animations.get()) {
            double t = Math.min(1.0, (System.currentTimeMillis() - openedAt) / (double) FADE_MS);
            progress = 1.0 - Math.pow(1.0 - t, 3.0);
        }
        p.alpha(progress);
        double top = y + (1.0 - progress) * 10.0;

        bounds = new Rect(x, top, w, h);
        header = new Rect(x, top, w, headerH);

        Color text = cfg.textColor.get();
        Color dim = cfg.dimColor.get();
        Color accent = cfg.accentColor.get();

        GlassPanel.draw(p, cfg, bounds, headerH, PeerColor.of(peer));

        double buttonH = u + pad * 0.7;
        double buttonY = top + (headerH - buttonH) / 2.0;
        double edge = x + w - pad;
        for (int i = 0; i < BUTTONS.length; i++) {
            Hit action = i == 0 ? Hit.CLOSE : i == 1 ? Hit.IGNORE : Hit.CLEAR;
            boolean isArmed = armed == action && System.currentTimeMillis() - armedAt <= CONFIRM_MS;
            String label = isArmed ? "confirm?" : BUTTONS[i];

            double bw = Math.max(buttonH, p.width(label) + pad * 1.2);
            double bx = edge - bw;
            buttons[i] = new Rect(bx, buttonY, bw, buttonH);

            boolean hover = buttons[i].contains(mx, my);
            Color fill = isArmed ? p.with(cfg.dangerColor.get(), 255)
                : hover ? (i == 0 ? p.with(cfg.dangerColor.get(), 210) : p.with(accent, 150))
                : p.with(cfg.incomingColor.get(), 160);
            p.round(bx, buttonY, bw, buttonH, buttonH / 2.0, fill);
            p.text(label, bx + (bw - p.width(label)) / 2.0, buttonY + (buttonH - u) / 2.0, hover || isArmed ? text : dim);
            edge = bx - pad * 0.5;
        }

        boolean copied = System.currentTimeMillis() - copiedAt < COPIED_MS;
        p.text(p.fit(copied ? "Copied" : peer, edge - x - pad), x + pad, top + (headerH - u) / 2.0, copied ? accent : text);

        Conversation conversation = messenger.existing(peer);
        double areaTop = top + headerH + pad * 0.5;
        double areaBottom = top + headerH + areaH;
        double areaLeft = x + pad;
        double areaRight = x + w - pad - 5.0;
        double padX = pad * 0.8;

        layout.update(p, conversation, areaRight - areaLeft, padX, cfg.textScale.get(),
            cfg.timestamps.get(), cfg.streamMode.get(), p.hasShadow());
        List<MessageLayout.Row> rows = layout.rows();
        int capacity = cfg.windowLines.get();
        area = new Rect(areaLeft, areaTop, areaRight - areaLeft, areaBottom - areaTop);
        PrivateMessage head = conversation == null || conversation.messages().isEmpty() ? null : conversation.messages().get(0);
        if (head != selectionHead) {
            selection.clear();
            selectionHead = head;
        }
        scrollRows = Math.max(0, Math.min(scrollRows, Math.max(0, rows.size() - capacity)));

        drawRows(p, cfg, rows, areaTop, areaBottom, areaLeft, areaRight, padX, rowH, u, radius);
        List<RowHit> done = hits;
        hits = nextHits;
        nextHits = done;
        resolveSelection(p, cfg, conversation);
        drawScrollbar(p, cfg, rows.size(), capacity, x + w - pad - 3.0, areaTop, areaBottom - areaTop);

        double boxX = x + pad;
        double boxY = top + headerH + areaH;
        double boxW = w - pad * 2.0;
        inputBox = new Rect(boxX, boxY, boxW, inputH);

        Color border = focused ? p.with(accent, 255) : p.with(dim, 80);
        Color boxFill = cfg.glass.get() == MessengerSettings.Glass.Tint ? p.with(cfg.headerColor.get(), 120) : cfg.headerColor.get();
        p.bordered(boxX, boxY, boxW, inputH, radius * 0.6, boxFill, border);

        inputTextX = boxX + pad * 0.7;
        double inner = boxW - pad * 1.4;
        input.limit(Messenger.messageLimit() - cfg.command().length() - peer.length() - 3);

        for (PendingPress press : pendingPress) {
            switch (press.kind()) {
                case CLICK -> input.click(p, press.relativeX());
                case DRAG -> input.drag(p, press.relativeX());
                case WORD -> input.word(p, press.relativeX());
            }
        }
        pendingPress.clear();
        input.render(p, inputTextX, boxY + (inputH - u) / 2.0, inner, focused, text, dim, accent,
            cfg.selectionColor.get(), "Type a message...");
    }

    private void drawRows(Painter p, MessengerSettings cfg, List<MessageLayout.Row> rows, double areaTop,
                          double areaBottom, double left, double right, double padX, double rowH, double u,
                          double radius) {
        double yy = areaBottom;
        boolean glossy = cfg.glass.get() == MessengerSettings.Glass.Tint && cfg.sheen.get();
        double bubbleRadius = radius * 0.8;
        nextHits.clear();

        for (int i = rows.size() - 1 - scrollRows; i >= 0; i--) {
            MessageLayout.Row row = rows.get(i);
            double rh = row.kind() == MessageLayout.Kind.GAP ? rowH * 0.35 : rowH;
            if (yy - rh < areaTop - 0.01) break;
            yy -= rh;
            if (row.kind() == MessageLayout.Kind.GAP) continue;

            double bx = row.incoming() ? left : right - row.bubbleWidth();
            Color fill = row.incoming() ? cfg.incomingColor.get() : cfg.outgoingColor.get();
            p.round(bx, yy, row.bubbleWidth(), rh, bubbleRadius, fill, row.first(), row.last());

            if (glossy && row.first() && row.kind() == MessageLayout.Kind.TEXT) {
                double inset = Math.min(bubbleRadius, row.bubbleWidth() / 2.0);
                p.gradient(bx + inset, yy + 1.0, row.bubbleWidth() - inset * 2.0, rh * 0.5, WHITE_FAINT, WHITE_CLEAR);
            }

            double textX = bx + padX;
            double ty = yy + (rh - u) / 2.0;
            nextHits.add(new RowHit(row, new Rect(bx, yy, row.bubbleWidth(), rh), textX));

            if (row.kind() == MessageLayout.Kind.TIME) {
                p.text(row.text(), bx + row.bubbleWidth() - padX - p.width(row.text()), ty, cfg.dimColor.get());
                continue;
            }

            int[] marked = selection.range(row.message(), row.offset(), row.offset() + row.text().length());
            if (marked != null) {
                double start = p.advance(row.text().substring(0, marked[0]));
                p.rect(textX + start, yy + 1.0, p.advance(row.text().substring(0, marked[1])) - start, rh - 2.0,
                    cfg.selectionColor.get());
            }
            p.text(row.text(), textX, ty, cfg.textColor.get());
        }
    }

    private void resolveSelection(Painter p, MessengerSettings cfg, Conversation conversation) {
        if (conversation == null || cfg.streamMode.get()) {
            selection.clear();
            pendingSelect.clear();
            return;
        }

        List<PrivateMessage> messages = conversation.messages();
        for (PendingSelect action : pendingSelect) {
            RowHit nearest = nearestHit(action.y());
            TextPosition position = nearest == null ? null : positionAt(p, nearest, action.x());
            if (position == null) {
                if (action.kind() == Select.BEGIN) selection.clear();
                continue;
            }

            switch (action.kind()) {
                case BEGIN -> selection.begin(position);
                case EXTEND -> {
                    selection.extend(position);
                    autoScroll(action.y());
                }
                case WORD -> {
                    if (nearest.row().kind() == MessageLayout.Kind.TEXT) {
                        selection.selectWord(textOf(messages, position.message()), position.message(), position.offset());
                    }
                }
                case COPY -> {
                    if (hitUnder(action.x(), action.y()) == null) continue;
                    if (!selection.covers(position)) {
                        selection.selectMessage(position.message(), textOf(messages, position.message()).length());
                    }
                    copy(messages, cfg.copyWithNames.get());
                }
            }
        }
        pendingSelect.clear();
    }

    private boolean copy(List<PrivateMessage> messages, boolean withNames) {
        String text = selection.text(messages, withNames);
        if (text.isEmpty()) return false;

        Clipboard.copy(text);
        copiedAt = System.currentTimeMillis();
        return true;
    }

    private void autoScroll(double my) {
        long now = System.currentTimeMillis();
        if (now - lastAutoScroll < AUTO_SCROLL_MS) return;

        if (my < area.y()) {
            scrollRows++;
            lastAutoScroll = now;
        } else if (my > area.y() + area.h()) {
            scrollRows = Math.max(0, scrollRows - 1);
            lastAutoScroll = now;
        }
    }

    private RowHit hitUnder(double mx, double my) {
        for (RowHit hit : hits) {
            if (hit.box().contains(mx, my)) return hit;
        }
        return null;
    }

    private RowHit nearestHit(double my) {
        RowHit best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RowHit hit : hits) {
            Rect box = hit.box();
            double distance = my < box.y() ? box.y() - my : my >= box.y() + box.h() ? my - (box.y() + box.h()) : 0.0;
            if (distance < bestDistance) {
                best = hit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private TextPosition positionAt(Painter p, RowHit hit, double mx) {
        MessageLayout.Row row = hit.row();
        if (row.kind() == MessageLayout.Kind.TIME) return new TextPosition(row.message(), row.offset());

        String line = row.text();
        double relative = mx - hit.textX();
        int nearest = 0;
        double nearestGap = Math.abs(relative);
        int i = 0;
        while (i < line.length()) {
            i = line.offsetByCodePoints(i, 1);
            double gap = Math.abs(p.advance(line.substring(0, i)) - relative);
            if (gap >= nearestGap) break;
            nearest = i;
            nearestGap = gap;
        }
        return new TextPosition(row.message(), row.offset() + nearest);
    }

    private static String textOf(List<PrivateMessage> messages, int index) {
        return index >= 0 && index < messages.size() ? messages.get(index).text() : "";
    }

    private void drawScrollbar(Painter p, MessengerSettings cfg, int total, int capacity, double sx, double top,
                               double height) {
        if (total <= capacity) return;

        double thumb = Math.max(12.0, height * capacity / total);
        double span = total - capacity;
        double fraction = (span - scrollRows) / span;

        p.round(sx, top, 3.0, height, 1.5, p.with(cfg.dimColor.get(), 40));
        p.round(sx, top + fraction * (height - thumb), 3.0, thumb, 1.5, p.with(cfg.dimColor.get(), 170));
    }
}
