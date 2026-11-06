package com.hunterbuddy.gui.theme;

import com.hunterbuddy.modules.mixin.accessors.GuiRendererScissorAccessor;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.Scissor;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.ArrayList;
import java.util.IdentityHashMap;

final class HbTextBatches {
    private final HbTypography typography;
    private final HbGuiTheme theme;
    private final IdentityHashMap<Scissor, Zone> zones = new IdentityHashMap<>();

    HbTextBatches(HbTypography typography, HbGuiTheme theme) {
        this.typography = typography;
        this.theme = theme;
    }

    void add(GuiRenderer renderer, String text, double x, double y, double size, Color color, boolean strong, double alpha) {
        if (text.isEmpty()) return;
        Scissor scissor = ((GuiRendererScissorAccessor) (Object) renderer).hunterbuddy$getScissorStack().top();
        Zone zone = zones.get(scissor);
        if (zone == null) {
            zone = new Zone();
            zones.put(scissor, zone);
        }
        if (!zone.scheduled) {
            renderer.post(zone);
            zone.scheduled = true;
        }
        zone.add(text, x, y, size, color, strong, alpha);
    }

    private final class Zone implements Runnable {
        private final ArrayList<Operation> pool = new ArrayList<>();
        private final ArrayList<Group> groups = new ArrayList<>();
        private int used;
        private boolean scheduled;

        void add(String text, double x, double y, double size, Color color, boolean strong, double alpha) {
            if (used == pool.size()) pool.add(new Operation());
            Operation operation = pool.get(used++);
            operation.text = text;
            operation.x = x;
            operation.y = y;
            operation.alpha = alpha;
            operation.color.set(color);
            operation.next = null;
            Group group = null;
            for (int i = 0; i < groups.size(); i++) {
                Group candidate = groups.get(i);
                if (candidate.size == size && candidate.strong == strong) {
                    group = candidate;
                    break;
                }
            }
            if (group == null) {
                group = new Group(size, strong);
                groups.add(group);
            }
            if (group.last == null) group.first = operation;
            else group.last.next = operation;
            group.last = operation;
        }

        @Override
        public void run() {
            for (int i = 0; i < groups.size(); i++) {
                Group group = groups.get(i);
                if (group.first == null) continue;
                TextRenderer font = typography.font(group.strong);
                font.begin(theme.hbScale(group.size / typography.baseHeight(group.strong)));
                Operation operation = group.first;
                while (operation != null) {
                    font.setAlpha(operation.alpha);
                    font.render(operation.text, operation.x, operation.y, operation.color, false);
                    operation.text = null;
                    operation = operation.next;
                }
                font.end();
                group.first = null;
                group.last = null;
            }
            used = 0;
            scheduled = false;
        }
    }

    private static final class Group {
        final double size;
        final boolean strong;
        Operation first;
        Operation last;

        Group(double size, boolean strong) {
            this.size = size;
            this.strong = strong;
        }
    }

    private static final class Operation {
        final Color color = new Color();
        String text;
        double x;
        double y;
        double alpha;
        Operation next;
    }
}
