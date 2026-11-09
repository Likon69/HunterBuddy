package com.hunterbuddy.journal;

import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.modules.messenger.ui.Rect;
import com.hunterbuddy.hud.text.HudText;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;

public final class JournalPainter {
    private final GlassPainter glass = new GlassPainter();
    private final double[] buttonWidths = new double[JournalLayout.Action.ALL.length];
    private int revision = -1;
    private boolean previousHidden;
    private boolean previousExpanded;
    private String previousCount;
    private String previousFooter;
    private String title;
    private String footer;
    private double countWidth;
    private double logoHeight;
    private double titleHeight;
    private double prefixHeight;
    private double buttonHeight;
    private double messageHeight;
    private double footerHeight;

    private void measure(HudRenderer renderer, JournalLayout layout, JournalSettings config, boolean hidden,
                         boolean expanded, String count, String status) {
        double t = config.textScale.get();
        double s = config.scale.get();
        boolean changed = revision != layout.revision;
        if (changed) {
            revision = layout.revision;
            logoHeight = HudText.textHeight(renderer, false, 0.82 * t);
            titleHeight = HudText.textHeight(renderer, false, 0.95 * t);
            prefixHeight = HudText.textHeight(renderer, false, 0.65 * t);
            buttonHeight = HudText.textHeight(renderer, false, 0.72 * t);
            messageHeight = HudText.textHeight(renderer, false, t);
            footerHeight = HudText.textHeight(renderer, false, 0.62 * t);
        }
        if (changed || hidden != previousHidden || expanded != previousExpanded) {
            for (JournalLayout.Action action : JournalLayout.Action.ALL) {
                if (action != JournalLayout.Action.LOG) buttonWidths[action.ordinal()] = HudText.textWidth(renderer, label(action, hidden, expanded), false, 0.72 * t);
            }
            previousHidden = hidden;
            previousExpanded = expanded;
        }
        if (changed || !count.equals(previousCount)) {
            previousCount = count;
            countWidth = HudText.textWidth(renderer, count, false, 0.65 * t);
            Rect log = layout.buttons[JournalLayout.Action.LOG.ordinal()];
            double titleWidth = Math.max(0, layout.window.x() + layout.window.w() - log.x() - log.w() - countWidth - 76 * s);
            title = titleWidth > 40 ? GlassPainter.fit(renderer, "Client Journal", titleWidth, 0.95 * t) : "";
        }
        if (changed || !status.equals(previousFooter)) {
            previousFooter = status;
            footer = GlassPainter.fit(renderer, status.isEmpty() ? "Wheel to scroll | Drag to select | Ctrl+C to copy" : status,
                layout.window.w() - 28 * s, 0.62 * t);
        }
    }

    private static String label(JournalLayout.Action action, boolean hidden, boolean expanded) {
        return switch (action) {
            case COORDS -> hidden ? "Coords: Hidden" : "Coords: Visible";
            case SIZE -> expanded ? "Restore" : "Expand";
            default -> action.label;
        };
    }

    private void logo(HudRenderer renderer, Rect rect, JournalSettings config, boolean hover) {
        double s = config.scale.get();
        for (int i = 0; i < 3; i++) renderer.quad(rect.x() + 12 * s, rect.y() + rect.h() / 2 + (-6 + i * 5) * s,
            (i == 2 ? 10 : 16) * s, 2 * s, glass.tint(config.accent.get(), hover ? 1 : 0.75));
        HudText.text(renderer, "Log", rect.x() + 35 * s,
            rect.y() + (rect.h() - logoHeight) / 2,
            config.accent.get(), false, 0.82 * config.textScale.get());
    }

    public void window(HudRenderer renderer, JournalLayout layout, JournalSettings config, JournalRows rows,
                       JournalSelection selection, boolean hidden, boolean expanded, String count,
                       String status, double mx, double my) {
        measure(renderer, layout, config, hidden, expanded, count, status);
        Rect rect = layout.window;
        double s = config.scale.get();
        double t = config.textScale.get();
        glass.card(renderer, rect.x(), rect.y(), rect.w(), rect.h(), s, 1, true, config.background.get());
        Rect log = layout.buttons[JournalLayout.Action.LOG.ordinal()];
        logo(renderer, log, config, log.contains(mx, my));
        double titleX = log.x() + log.w() + 14 * s;
        if (!title.isEmpty()) HudText.text(renderer, title, titleX, log.y() + (log.h() - titleHeight) / 2,
            glass.white(0.95), false, 0.95 * t);
        if (rect.x() + rect.w() - countWidth - 48 * s > log.x() + log.w() + 10 * s) HudText.text(renderer, count, rect.x() + rect.w() - countWidth - 48 * s,
            log.y() + (log.h() - prefixHeight) / 2, glass.white(0.5), false, 0.65 * t);
        Rect close = layout.buttons[JournalLayout.Action.CLOSE.ordinal()];
        boolean closeHover = close.contains(mx, my);
        glass.pill(renderer, close.x(), close.y(), close.w(), close.h(), s,
            closeHover ? glass.tint(config.error.get(), 0.2) : glass.white(0.05));
        double inset = 7 * s;
        renderer.line(close.x() + inset, close.y() + inset, close.x() + close.w() - inset, close.y() + close.h() - inset,
            glass.white(closeHover ? 1 : 0.6));
        renderer.line(close.x() + close.w() - inset, close.y() + inset, close.x() + inset, close.y() + close.h() - inset,
            glass.white(closeHover ? 1 : 0.6));
        for (JournalLayout.Action action : JournalLayout.Action.ALL) {
            if (action == JournalLayout.Action.LOG || action == JournalLayout.Action.CLOSE) continue;
            Rect button = layout.buttons[action.ordinal()];
            boolean hover = button.contains(mx, my);
            boolean visible = action == JournalLayout.Action.COORDS && !hidden;
            glass.pill(renderer, button.x(), button.y(), button.w(), button.h(), s,
                visible ? glass.tint(config.warning.get(), 0.17) : hover ? glass.tint(config.accent.get(), 0.17) : glass.white(0.06));
            String label = label(action, hidden, expanded);
            double tw = buttonWidths[action.ordinal()];
            HudText.text(renderer, label, button.x() + (button.w() - tw) / 2,
                button.y() + (button.h() - buttonHeight) / 2,
                visible ? config.warning.get() : glass.white(hover ? 1 : 0.82), false, 0.72 * t);
        }
        Rect body = layout.content;
        renderer.quad(body.x(), body.y() - 5 * s, body.w(), s, glass.white(0.06));
        if (rows.size() == 0 && body.h() >= messageHeight + 10 * s) HudText.text(renderer, "No local messages yet", body.x() + 8 * s, body.y() + 10 * s,
            glass.white(0.45), false, t);
        for (int i = 0; i < rows.size(); i++) {
            JournalRows.Row row = rows.get(i);
            double y = body.y() + i * layout.rowHeight;
            if (selection.contains(row.entry().id())) renderer.quad(body.x() - 3 * s, y, body.w(), layout.rowHeight,
                glass.tint(config.accent.get(), 0.17));
            if (!row.prefix().isEmpty()) HudText.text(renderer, row.prefix(), body.x(),
                y + (layout.rowHeight - prefixHeight) / 2, glass.white(0.48), false, 0.65 * t);
            HudText.text(renderer, row.message(), body.x() + layout.prefixWidth,
                y + (layout.rowHeight - messageHeight) / 2, config.color(row.entry()), false, t);
        }
        if (rows.fraction() < 1 && layout.visible > 0) {
            double thumb = Math.min(body.h(), Math.max(18 * s, body.h() * rows.fraction()));
            double top = body.y() + (body.h() - thumb) * Math.max(0, Math.min(1, rows.position()));
            renderer.quad(body.x() + body.w() + 5 * s, body.y(), 3 * s, body.h(), glass.white(0.08));
            renderer.quad(body.x() + body.w() + 5 * s, top, 3 * s, thumb, glass.tint(config.accent.get(), 0.6));
        }
        HudText.text(renderer, footer, rect.x() + 14 * s,
            rect.y() + rect.h() - footerHeight - 9 * s, glass.white(0.55), false, 0.62 * t);
    }
}
