package com.hunterbuddy.journal;

import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
import com.hunterbuddy.modules.ClientLog;
import com.hunterbuddy.modules.messenger.ui.Rect;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;

public final class JournalPainter {
    private final GlassPainter glass = new GlassPainter();

    public void icon(HudRenderer renderer, Rect rect, ClientLog config, boolean hover) {
        double s = config.scale.get();
        glass.card(renderer, rect.x(), rect.y(), rect.w(), rect.h(), s, 1, true, config.background.get());
        logo(renderer, rect, config, hover);
    }

    private void logo(HudRenderer renderer, Rect rect, ClientLog config, boolean hover) {
        double s = config.scale.get();
        for (int i = 0; i < 3; i++) renderer.quad(rect.x() + 12 * s, rect.y() + rect.h() / 2 + (-6 + i * 5) * s,
            (i == 2 ? 10 : 16) * s, 2 * s, glass.tint(config.accent.get(), hover ? 1 : 0.75));
        renderer.text("Log", rect.x() + 35 * s,
            GlassPainter.centered(renderer, rect.y() + rect.h() / 2, 0.82 * config.textScale.get()),
            config.accent.get(), false, 0.82 * config.textScale.get());
    }

    public void window(HudRenderer renderer, JournalLayout layout, ClientLog config, JournalRows rows,
                       JournalSelection selection, int first, boolean hidden, boolean expanded, String count,
                       String status, double mx, double my) {
        Rect rect = layout.window;
        double s = config.scale.get();
        double t = config.textScale.get();
        ModuleToastBlur.INSTANCE.draw(rect.x(), rect.y(), rect.w(), rect.h(), GlassPainter.radius(s), 1);
        glass.card(renderer, rect.x(), rect.y(), rect.w(), rect.h(), s, 1, true, config.background.get());
        glass.accentBar(renderer, rect.x(), rect.y(), rect.h(), s, 0.8, config.accent.get());
        Rect log = layout.buttons[JournalLayout.Action.LOG.ordinal()];
        logo(renderer, log, config, log.contains(mx, my));
        double countWidth = renderer.textWidth(count, false, 0.65 * t);
        double titleX = log.x() + log.w() + 14 * s;
        double titleWidth = Math.max(0, rect.x() + rect.w() - titleX - countWidth - 28 * s);
        if (titleWidth > 40) renderer.text(GlassPainter.fit(renderer, "Client Journal", titleWidth, 0.95 * t),
            titleX, GlassPainter.centered(renderer, log.y() + log.h() / 2, 0.95 * t),
            glass.white(0.95), false, 0.95 * t);
        if (rect.x() + rect.w() - countWidth - 16 * s > log.x() + log.w() + 10 * s) renderer.text(count, rect.x() + rect.w() - countWidth - 16 * s,
            GlassPainter.centered(renderer, log.y() + log.h() / 2, 0.65 * t), glass.white(0.5), false, 0.65 * t);
        for (JournalLayout.Action action : JournalLayout.Action.ALL) {
            if (action == JournalLayout.Action.LOG) continue;
            Rect button = layout.buttons[action.ordinal()];
            boolean hover = button.contains(mx, my);
            boolean visible = action == JournalLayout.Action.COORDS && !hidden;
            glass.pill(renderer, button.x(), button.y(), button.w(), button.h(), s,
                visible ? glass.tint(config.warning.get(), 0.17) : hover ? glass.tint(config.accent.get(), 0.17) : glass.white(0.06));
            String label = switch (action) {
                case COORDS -> hidden ? "Coords: Hidden" : "Coords: Visible";
                case SIZE -> expanded ? "Restore" : "Expand";
                default -> action.label;
            };
            double tw = renderer.textWidth(label, false, 0.72 * t);
            renderer.text(label, button.x() + (button.w() - tw) / 2,
                GlassPainter.centered(renderer, button.y() + button.h() / 2, 0.72 * t),
                visible ? config.warning.get() : glass.white(hover ? 1 : 0.82), false, 0.72 * t);
        }
        Rect body = layout.content;
        renderer.quad(body.x(), body.y() - 5 * s, body.w(), s, glass.white(0.06));
        if (rows.size() == 0 && body.h() >= renderer.textHeight(false, t) + 10 * s) renderer.text("No local messages yet", body.x() + 8 * s, body.y() + 10 * s,
            glass.white(0.45), false, t);
        for (int i = 0; i < layout.visible && first + i < rows.size(); i++) {
            JournalRows.Row row = rows.get(first + i);
            double y = body.y() + i * layout.rowHeight;
            if (selection.contains(row.entry().id())) renderer.quad(body.x() - 3 * s, y, body.w(), layout.rowHeight,
                glass.tint(config.accent.get(), 0.17));
            if (!row.prefix().isEmpty()) renderer.text(GlassPainter.fit(renderer, row.prefix(), Math.max(1, layout.prefixWidth - 8 * s), 0.65 * t), body.x(),
                GlassPainter.centered(renderer, y + layout.rowHeight / 2, 0.65 * t), glass.white(0.48), false, 0.65 * t);
            renderer.text(row.message(), body.x() + layout.prefixWidth,
                GlassPainter.centered(renderer, y + layout.rowHeight / 2, t), config.color(row.entry()), false, t);
        }
        if (rows.size() > layout.visible && layout.visible > 0) {
            double thumb = Math.min(body.h(), Math.max(18 * s, body.h() * layout.visible / rows.size()));
            double top = body.y() + (body.h() - thumb) * first / Math.max(1, rows.size() - layout.visible);
            renderer.quad(body.x() + body.w() + 5 * s, body.y(), 3 * s, body.h(), glass.white(0.08));
            renderer.quad(body.x() + body.w() + 5 * s, top, 3 * s, thumb, glass.tint(config.accent.get(), 0.6));
        }
        String footer = status.isEmpty() ? "Wheel to scroll | Drag to select | Ctrl+C to copy" : status;
        renderer.text(GlassPainter.fit(renderer, footer, rect.w() - 28 * s, 0.62 * t), rect.x() + 14 * s,
            rect.y() + rect.h() - renderer.textHeight(false, 0.62 * t) - 9 * s, glass.white(0.55), false, 0.62 * t);
    }
}
