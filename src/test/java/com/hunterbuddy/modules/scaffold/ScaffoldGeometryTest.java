package com.hunterbuddy.modules.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class ScaffoldGeometryTest {
    @Test
    void predictsTheNextFeetAndExtendsAlongHorizontalMotion() {
        ScaffoldPath path = ScaffoldPath.create(new Vec3d(0.8, 64, 0.5), new Vec3d(0.3, -0.0784, 0), Vec3d.ZERO, true);
        assertEquals(new BlockPos(1, 63, 0), path.at(0));
        assertEquals(new BlockPos(4, 63, 0), path.at(3));
    }

    @Test
    void disabledPredictionKeepsCurrentFeetAndNegativeCoordinates() {
        ScaffoldPath path = ScaffoldPath.create(new Vec3d(-0.2, 64, -0.2), new Vec3d(-0.3, -1, 0), Vec3d.ZERO, false);
        assertEquals(new BlockPos(-1, 63, -1), path.at(0));
        assertEquals(new BlockPos(-4, 63, -1), path.at(3));
    }

    @Test
    void blockedMovementUsesInputAndStationaryMovementDoesNotDrift() {
        Vec3d feet = new Vec3d(0.5, 64, 0.5);
        ScaffoldPath moving = ScaffoldPath.create(feet, Vec3d.ZERO, new Vec3d(0, 0, 1), true);
        assertEquals(new BlockPos(0, 63, 3), moving.at(3));
        ScaffoldPath still = ScaffoldPath.create(feet, Vec3d.ZERO, Vec3d.ZERO, true);
        assertEquals(still.at(0), still.at(3));
    }

    @Test
    void sideClicksStayOnTheFaceBelowItsUpperEdge() {
        Box box = new Box(0, 0, 0, 1, 1, 1);
        Vec3d point = SupportHit.point(box, Direction.EAST, new Vec3d(3, 3, -2));
        assertEquals(new Vec3d(1, 0.95, 0.05), point);
        assertEquals(new Vec3d(0.95, 1, 0.05), SupportHit.point(box, Direction.UP, new Vec3d(3, 3, -2)));
    }

    @Test
    void thinSupportsKeepTheClickInsideTheirOutline() {
        Vec3d point = SupportHit.point(new Box(0, 0, 0, 0.1, 0.1, 0.1), Direction.NORTH, new Vec3d(-2, 3, -2));
        assertEquals(0.025, point.x, 0.000001);
        assertEquals(0.075, point.y, 0.000001);
        assertEquals(0, point.z);
    }

    @Test
    void rejectsBackFacesAndClicksOutsideReach() {
        BlockHitResult hit = new BlockHitResult(new Vec3d(1, 0.95, 0.5), Direction.EAST, BlockPos.ORIGIN, false);
        assertTrue(SupportHit.inFrontAndReach(hit, new Vec3d(2, 0.95, 0.5), 1));
        assertFalse(SupportHit.inFrontAndReach(hit, new Vec3d(0, 0.95, 0.5), 4));
        assertFalse(SupportHit.inFrontAndReach(hit, hit.getPos(), 4));
        assertFalse(SupportHit.inFrontAndReach(hit, new Vec3d(2.01, 0.95, 0.5), 1));
    }
}
