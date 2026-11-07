package com.hunterbuddy.gui.theme.widgets;

import java.util.ArrayList;
import java.util.List;

public final class HbWindowSnap {
    public record Box(double x, double y, double width, double height) {
    }

    public record Guide(boolean vertical, double at, double from, double to) {
    }

    public record Result(int x, int y, List<Guide> guides) {
    }

    private record Pick(double position, double edge, int partner, boolean screen) {
    }

    private HbWindowSnap() {
    }

    public static Result snap(Box dragged, List<Box> others, double screenWidth, double screenHeight,
                              double grid, double range, double gap) {
        Pick pickX = pick(dragged.x(), dragged.width(), dragged.y(), dragged.height(), others, true, screenWidth, grid, range, gap);
        Pick pickY = pick(dragged.y(), dragged.height(), dragged.x(), dragged.width(), others, false, screenHeight, grid, range, gap);
        int x = (int) Math.round(pickX.position());
        int y = (int) Math.round(pickY.position());
        List<Guide> guides = new ArrayList<>();
        addGuide(guides, true, pickX, y, dragged.height(), others, screenHeight);
        addGuide(guides, false, pickY, x, dragged.width(), others, screenWidth);
        return new Result(x, y, guides);
    }

    private static void addGuide(List<Guide> guides, boolean vertical, Pick pick, double cross, double crossSize,
                                 List<Box> others, double screenSpan) {
        if (pick.partner() >= 0) {
            Box other = others.get(pick.partner());
            double otherStart = vertical ? other.y() : other.x();
            double otherSize = vertical ? other.height() : other.width();
            guides.add(new Guide(vertical, pick.edge(), Math.min(cross, otherStart), Math.max(cross + crossSize, otherStart + otherSize)));
        } else if (pick.screen()) {
            guides.add(new Guide(vertical, pick.edge(), 0, screenSpan));
        }
    }

    private static Pick pick(double position, double size, double cross, double crossSize, List<Box> others,
                             boolean horizontal, double screen, double grid, double range, double gap) {
        double bestDelta = Double.MAX_VALUE;
        double bestEdge = 0;
        int bestPartner = -1;
        boolean bestScreen = false;
        double far = position + size;
        for (int i = 0; i < others.size(); i++) {
            Box other = others.get(i);
            double start = horizontal ? other.x() : other.y();
            double end = start + (horizontal ? other.width() : other.height());
            double crossStart = horizontal ? other.y() : other.x();
            double crossEnd = crossStart + (horizontal ? other.height() : other.width());
            boolean beside = cross < crossEnd + gap && cross + crossSize > crossStart - gap;
            double[][] candidates = beside
                ? new double[][]{{start - position, start}, {end - far, end}, {end - position, end}, {end + gap - position, end + gap},
                    {start - far, start}, {start - gap - far, start - gap}}
                : new double[][]{{start - position, start}, {end - far, end}};
            for (double[] candidate : candidates) {
                if (Math.abs(candidate[0]) < Math.abs(bestDelta)) {
                    bestDelta = candidate[0];
                    bestEdge = candidate[1];
                    bestPartner = i;
                    bestScreen = false;
                }
            }
        }
        double[][] screenCandidates = {{-position, 0}, {screen - far, screen}};
        for (double[] candidate : screenCandidates) {
            if (Math.abs(candidate[0]) < Math.abs(bestDelta)) {
                bestDelta = candidate[0];
                bestEdge = candidate[1];
                bestPartner = -1;
                bestScreen = true;
            }
        }
        if (Math.abs(bestDelta) <= range) return new Pick(position + bestDelta, bestEdge, bestPartner, bestScreen);
        double gridPosition = Math.round(position / grid) * grid;
        return new Pick(gridPosition, gridPosition, -1, false);
    }
}
