package com.hunterbuddy.gui.theme;

import com.hunterbuddy.gui.theme.widgets.HbWindowSnap;
import com.hunterbuddy.gui.theme.widgets.HbWindowSnap.Box;
import com.hunterbuddy.gui.theme.widgets.HbWindowSnap.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HbWindowSnapTest {
    private static final Box OTHER = new Box(400, 300, 200, 150);

    private static Result snap(Box dragged, List<Box> others) {
        return HbWindowSnap.snap(dragged, others, 1920, 1080, 8, 10, 4);
    }

    @Test
    void leftEdgeSnapsToTheOtherLeftEdge() {
        Result result = snap(new Box(405, 700, 100, 100), List.of(OTHER));
        assertEquals(400, result.x());
        assertEquals(1, result.guides().size());
    }

    @Test
    void rightEdgeSnapsNextToTheOtherLeftEdgeWithTheGap() {
        Result result = snap(new Box(297, 400, 100, 100), List.of(OTHER));
        assertEquals(296, result.x());
    }

    @Test
    void screenEdgeWinsWhenNoWindowIsInRange() {
        Result result = snap(new Box(6, 985, 100, 100), List.of(OTHER));
        assertEquals(0, result.x());
        assertEquals(980, result.y());
    }

    @Test
    void gridStepsWhenNothingElseIsInRange() {
        assertEquals(104, snap(new Box(102, 700, 100, 100), List.of()).x());
        assertEquals(112, snap(new Box(111, 700, 100, 100), List.of()).x());
    }

    @Test
    void adjacencyNeedsOverlapOnTheOtherAxis() {
        assertEquals(296, snap(new Box(298, 700, 100, 100), List.of(OTHER)).x());
        assertEquals(300, snap(new Box(298, 300, 100, 100), List.of(OTHER)).x());
    }

    @Test
    void alignmentIgnoresTheOtherAxis() {
        assertEquals(400, snap(new Box(403, 900, 100, 100), List.of(OTHER)).x());
    }

    @Test
    void theClosestCandidateWinsEvenWhenItIsTheScreenEdge() {
        assertEquals(0, snap(new Box(1, 700, 100, 100), List.of(new Box(8, 0, 50, 50))).x());
    }

    @Test
    void windowEdgesTakePriorityOverTheScreen() {
        Result result = snap(new Box(8, 700, 100, 100), List.of(new Box(12, 0, 50, 50)));
        assertEquals(12, result.x());
        assertTrue(result.guides().stream().anyMatch(g -> g.vertical() && g.at() == 12));
    }
}
