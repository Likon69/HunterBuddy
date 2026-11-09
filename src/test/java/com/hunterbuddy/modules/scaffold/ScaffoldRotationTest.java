package com.hunterbuddy.modules.scaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class ScaffoldRotationTest {
    @Test
    void doesNotPlaceWithoutTheMovementWindow() {
        ScaffoldRotation rotation = new ScaffoldRotation();
        AtomicInteger placements = new AtomicInteger();
        rotation.schedule(Vec3d.ZERO, true, true, placements::incrementAndGet);
        rotation.sent(new PlayerMoveC2SPacket.LookAndOnGround(0, 90, true, false));
        rotation.post();
        assertEquals(0, placements.get());
        assertTrue(rotation.schedule(Vec3d.ZERO, true, true, placements::incrementAndGet));
    }

    @Test
    void limitsPendingPlacementsToOnePerMovementCycle() {
        ScaffoldRotation rotation = new ScaffoldRotation();
        AtomicInteger placements = new AtomicInteger();
        assertTrue(rotation.schedule(Vec3d.ZERO, true, true, placements::incrementAndGet));
        assertFalse(rotation.schedule(Vec3d.ZERO, true, true, placements::incrementAndGet));
        rotation.post();
        assertEquals(0, placements.get());
    }

    @Test
    void cancellationDiscardsPendingPlacement() {
        ScaffoldRotation rotation = new ScaffoldRotation();
        AtomicInteger placements = new AtomicInteger();
        rotation.schedule(Vec3d.ZERO, true, true, placements::incrementAndGet);
        rotation.reset();
        rotation.post();
        assertEquals(0, placements.get());
        assertTrue(rotation.schedule(Vec3d.ZERO, true, true, placements::incrementAndGet));
    }
}
