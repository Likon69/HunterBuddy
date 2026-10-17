package com.hunterbuddy.modules.messenger.ui;

import com.hunterbuddy.modules.messenger.Conversation;
import com.hunterbuddy.modules.messenger.Messenger;
import com.hunterbuddy.modules.messenger.MessengerSettings;
import com.hunterbuddy.util.HudPulse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class ContactList {
    public enum Kind {
        NONE,
        INSIDE,
        OPEN,
        REMOVE,
        CLEAR_ALL
    }

    public record Result(Kind kind, String peer) {
        static final Result NONE = new Result(Kind.NONE, null);
        static final Result INSIDE = new Result(Kind.INSIDE, null);
    }

    private record Slot(Rect row, Rect remove, String peer) {
    }

    private static final int MAX_ROWS = 8;
    private static final long FADE_MS = 160L;
    private static final double GAP = 8.0;
    private static final double DOT = 6.0;
    private static final long CONFIRM_MS = 3000L;

    private final HudPulse.Tracker pulse = new HudPulse.Tracker();
    private final List<Slot> slots = new ArrayList<>();
    private Rect bounds = Rect.NONE;
    private Rect clearAll = Rect.NONE;
    private final Set<String> known = new HashSet<>();
    private int scroll;
    private boolean clearArmed;
    private long clearArmedAt;
    private long openedAt = System.currentTimeMillis();

    public void restartFade() {
        openedAt = System.currentTimeMillis();
        scroll = 0;
    }

    public boolean contains(double mx, double my) {
        return bounds.contains(mx, my);
    }

    public void scroll(double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
    }

    public Result click(double mx, double my) {
        if (!bounds.contains(mx, my)) return Result.NONE;
        if (clearAll.contains(mx, my)) {
            long now = System.currentTimeMillis();
            if (clearArmed && now - clearArmedAt <= CONFIRM_MS) {
                clearArmed = false;
                return new Result(Kind.CLEAR_ALL, null);
            }
            clearArmed = true;
            clearArmedAt = now;
            return Result.INSIDE;
        }

        for (Slot slot : slots) {
            if (slot.remove().contains(mx, my)) return new Result(Kind.REMOVE, slot.peer());
            if (slot.row().contains(mx, my)) return new Result(Kind.OPEN, slot.peer());
        }
        return Result.INSIDE;
    }

    public void render(Painter p, Messenger messenger, Rect anchor, double mx, double my, int sw, int sh) {
        MessengerSettings cfg = messenger.cfg();
        List<Conversation> all = messenger.sortedConversations();
        Set<String> peers = new HashSet<>();
        for (Conversation conversation : all) peers.add(conversation.peer());
        if (!peers.containsAll(known)) pulse.clear();
        known.clear();
        known.addAll(peers);
        ChatTheme theme = cfg.chatStyle.get();
        double u = p.lineHeight();
        double pad = theme.padding(u);
        double rowH = u + pad * (theme == ChatTheme.Bubbles ? 1.8 : 1.2);

        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - MAX_ROWS)));
        int shown = Math.min(MAX_ROWS, all.size() - scroll);

        double headerH = u + pad * 2.0;
        double footerH = all.isEmpty() ? 0.0 : u + pad * 1.6;
        double w = Math.max(u * 12.0, p.width("No messages yet") + pad * 2.0);
        for (Conversation conversation : all) {
            w = Math.max(w, p.width(conversation.peer()) + u * 5.0 + pad * 3.0 + DOT + pad * 0.5);
        }
        double h = headerH + Math.max(1, shown) * rowH + footerH + pad * 0.5;

        double lx;
        double ly;
        MessengerSettings.ListSide side = cfg.listSide.get();
        if (side == MessengerSettings.ListSide.Auto) {
            side = anchor.x() + anchor.w() / 2.0 < sw / 2.0 ? MessengerSettings.ListSide.Right : MessengerSettings.ListSide.Left;
        }
        switch (side) {
            case Left -> {
                lx = anchor.x() - GAP - w;
                ly = anchor.y();
            }
            case Above -> {
                lx = anchor.x() + anchor.w() / 2.0 - w / 2.0;
                ly = anchor.y() - GAP - h;
            }
            case Below -> {
                lx = anchor.x() + anchor.w() / 2.0 - w / 2.0;
                ly = anchor.y() + anchor.h() + GAP;
            }
            default -> {
                lx = anchor.x() + anchor.w() + GAP;
                ly = anchor.y();
            }
        }
        lx = Math.max(0.0, Math.min(lx, sw - w));
        ly = Math.max(0.0, Math.min(ly, sh - h));

        double progress = 1.0;
        if (cfg.animations.get()) {
            double t = Math.min(1.0, (System.currentTimeMillis() - openedAt) / (double) FADE_MS);
            progress = 1.0 - Math.pow(1.0 - t, 3.0);
        }
        p.alpha(progress);
        double top = ly + (1.0 - progress) * 8.0;

        bounds = new Rect(lx, top, w, h);
        slots.clear();
        clearAll = Rect.NONE;

        Color text = cfg.textColor.get();
        Color dim = cfg.dimColor.get();
        Color accent = cfg.accentColor.get();

        theme.panel(p, cfg, bounds, headerH, null);
        p.text("Messages", lx + pad, top + (headerH - u) / 2.0, text);

        int total = messenger.unreadTotal();
        if (total > 0) {
            String label = total + " unread";
            p.text(label, lx + w - pad - p.width(label), top + (headerH - u) / 2.0, accent);
        }

        double yy = top + headerH;

        if (all.isEmpty()) {
            p.text("No messages yet", lx + pad, yy + (rowH - u) / 2.0, dim);
        }

        for (int i = 0; i < shown; i++) {
            Conversation conversation = all.get(scroll + i);
            Rect row = new Rect(lx + pad * 0.5, yy, w - pad, rowH);
            double removeW = u + pad * 0.6;
            Rect remove = new Rect(row.x() + row.w() - removeW - pad * 0.2, yy + (rowH - u - pad * 0.6) / 2.0, removeW, u + pad * 0.6);
            slots.add(new Slot(row, remove, conversation.peer()));

            boolean hoverRemove = remove.contains(mx, my);
            Color peerColor = PeerColor.of(conversation.peer());
            boolean colored = cfg.peerColors.get();
            boolean hover = row.contains(mx, my) && !hoverRemove;
            theme.contact(p, cfg, row, hover, conversation.unread() > 0);
            if (hover) {
                if (colored && cfg.bandWidth.get() > 0) p.round(row.x(), row.y(), cfg.bandWidth.get(), row.h(), 1.5, peerColor);
            }

            double nameX = row.x() + pad * 0.8;
            double nameY = yy + (rowH - u) / 2.0;
            double nameRoom = row.w() - u * 5.0 - pad * 2.0;
            if (colored) {
                p.round(nameX, yy + (rowH - DOT) / 2.0, DOT, DOT, DOT / 2.0, peerColor);
                nameX += DOT + pad * 0.5;
                nameRoom -= DOT + pad * 0.5;
            }
            p.text(p.fit(conversation.peer(), nameRoom), nameX, nameY, text);

            if (conversation.unread() > 0) drawBadge(p, cfg, conversation, remove.x() - pad * 0.5, yy, rowH, u, pad);

            if (hoverRemove) p.round(remove.x(), remove.y(), remove.w(), remove.h(), remove.h() / 2.0, p.with(cfg.dangerColor.get(), 200));
            p.text("x", remove.x() + (remove.w() - p.width("x")) / 2.0, remove.y() + (remove.h() - u) / 2.0, hoverRemove ? text : dim);

            yy += rowH;
        }

        if (!all.isEmpty()) {
            boolean armed = clearArmed && System.currentTimeMillis() - clearArmedAt <= CONFIRM_MS;
            String label = armed ? "confirm?" : "clear all";
            double bw = p.width(label) + pad * 2.0;
            double bh = u + pad * 0.8;
            clearAll = new Rect(lx + (w - bw) / 2.0, top + h - pad * 0.5 - bh - pad * 0.2, bw, bh);

            boolean hover = clearAll.contains(mx, my);
            Color fill = armed ? p.with(cfg.dangerColor.get(), 255)
                : hover ? p.with(accent, 150)
                : p.with(cfg.incomingColor.get(), 160);
            p.round(clearAll.x(), clearAll.y(), bw, bh, theme.buttonRadius(bh), fill);
            p.text(label, clearAll.x() + pad, clearAll.y() + (bh - u) / 2.0, hover || armed ? text : dim);
        }
    }

    private void drawBadge(Painter p, MessengerSettings cfg, Conversation conversation, double right, double rowY,
                           double rowH, double u, double pad) {
        String label = conversation.unread() > 99 ? "99+" : String.valueOf(conversation.unread());
        float fresh = cfg.animations.get()
            ? pulse.freshness(conversation.peer(), String.valueOf(conversation.unread()), 600L) : 0.0f;
        double grow = HudPulse.pop(fresh);

        double bw = Math.max(u + pad * 0.6, p.width(label) + pad * 1.2) * grow;
        double bh = (u + pad * 0.6) * grow;
        double bx = right - bw;
        double by = rowY + (rowH - bh) / 2.0;

        p.round(bx, by, bw, bh, bh / 2.0, cfg.accentColor.get());
        p.text(label, bx + (bw - p.width(label)) / 2.0, by + (bh - u) / 2.0, p.with(cfg.backgroundColor.get(), 255));
    }
}
