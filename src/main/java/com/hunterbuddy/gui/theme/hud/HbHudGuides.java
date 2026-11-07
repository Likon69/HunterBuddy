package com.hunterbuddy.gui.theme.hud;

import com.hunterbuddy.gui.theme.widgets.HbWindowSnap;
import meteordevelopment.meteorclient.systems.hud.HudElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class HbHudGuides {
    private static final double CENTER_TOLERANCE = 0.5;

    private HbHudGuides() {
    }

    static List<HbWindowSnap.Guide> of(List<HudElement> selection, Iterable<HudElement> elements, int width, int height, int border) {
        if (selection.isEmpty()) return List.of();
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (HudElement element : selection) {
            left = Math.min(left, element.x);
            top = Math.min(top, element.y);
            right = Math.max(right, element.x + element.getWidth());
            bottom = Math.max(bottom, element.y + element.getHeight());
        }
        Map<Integer, double[]> vertical = new TreeMap<>();
        Map<Integer, double[]> horizontal = new TreeMap<>();
        int[] screenX = {0, border, width - border, width};
        int[] screenY = {0, border, height - border, height};
        for (int edge : new int[]{left, right}) {
            for (int target : screenX) if (edge == target) merge(vertical, target, 0, height);
        }
        for (int edge : new int[]{top, bottom}) {
            for (int target : screenY) if (edge == target) merge(horizontal, target, 0, width);
        }
        if (Math.abs((left + right) / 2.0 - width / 2.0) <= CENTER_TOLERANCE) merge(vertical, width / 2, 0, height);
        if (Math.abs((top + bottom) / 2.0 - height / 2.0) <= CENTER_TOLERANCE) merge(horizontal, height / 2, 0, width);
        for (HudElement other : elements) {
            if (selection.contains(other)) continue;
            int otherLeft = other.x;
            int otherRight = other.x + other.getWidth();
            int otherTop = other.y;
            int otherBottom = other.y + other.getHeight();
            for (int edge : new int[]{left, right}) {
                for (int target : new int[]{otherLeft, otherRight}) {
                    if (edge == target) merge(vertical, target, Math.min(top, otherTop), Math.max(bottom, otherBottom));
                }
            }
            for (int edge : new int[]{top, bottom}) {
                for (int target : new int[]{otherTop, otherBottom}) {
                    if (edge == target) merge(horizontal, target, Math.min(left, otherLeft), Math.max(right, otherRight));
                }
            }
        }
        List<HbWindowSnap.Guide> guides = new ArrayList<>();
        vertical.forEach((at, span) -> guides.add(new HbWindowSnap.Guide(true, at, span[0], span[1])));
        horizontal.forEach((at, span) -> guides.add(new HbWindowSnap.Guide(false, at, span[0], span[1])));
        return guides;
    }

    private static void merge(Map<Integer, double[]> lines, int at, double from, double to) {
        double[] span = lines.get(at);
        if (span == null) lines.put(at, new double[]{from, to});
        else {
            span[0] = Math.min(span[0], from);
            span[1] = Math.max(span[1], to);
        }
    }
}
