package com.hunterbuddy.modules.aura;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AuraHealthTest {
    @Test
    void firstObservationAndRepeatedPacketsNeverCreateDamage() {
        AuraHealth health = new AuraHealth();
        assertEquals(0, health.observe(20, 20, 0, false, 1000));
        assertEquals(0, health.observe(20, 20, 0, false, 1050));
        assertTrue(health.trusted(false, 1050));
        assertEquals(6.5, health.observe(13.5f, 20, 0, false, 1100));
        assertEquals(0, health.observe(13.5f, 20, 0, false, 1150));
        assertEquals(0.675, health.fraction(), 0.00001);
    }

    @Test
    void absorptionExpirationAndHealingNeverInventHealthDamage() {
        AuraHealth health = new AuraHealth();
        health.observe(12, 20, 4, false, 1000);
        assertEquals(0, health.observe(12, 20, 1, false, 1100));
        assertEquals(0, health.observe(13, 20, 0, false, 1200));
        assertEquals(6.5, health.observe(6.5f, 20, 0, false, 1300));
    }

    @Test
    void trailHoldsThenEasesAndRapidHitsRetainThePreviousTrail() {
        AuraHealth health = new AuraHealth();
        health.observe(20, 20, 0, false, 1000);
        health.observe(14, 20, 0, false, 1100);
        assertEquals(20, health.trail(1250));
        assertTrue(health.trail(1500) < 20);
        assertTrue(health.trail(1500) > 14);
        health.observe(7, 20, 0, false, 1250);
        assertEquals(20, health.trail(1400));
        assertEquals(7, health.trail(2000));
        health.observe(10, 20, 0, false, 2100);
        assertEquals(10, health.trail(2100));
    }

    @Test
    void playerHealthIsHiddenUntilItActuallyChangesBelowMaximum() {
        AuraHealth health = new AuraHealth();
        health.observe(20, 20, 0, true, 1000);
        assertFalse(health.trusted(true, 1000));
        assertEquals(0, health.observe(20, 20, 0, true, 1100));
        assertEquals(5, health.observe(15, 20, 0, true, 1200));
        assertTrue(health.trusted(true, 1200));
        assertFalse(health.trusted(true, 2800));
        health.observe(20, 20, 0, true, 2900);
        assertFalse(health.trusted(true, 2900));
    }

    @Test
    void frozenPlayerValuesAndUnansweredHitsAreSuppressed() {
        AuraHealth frozen = new AuraHealth();
        frozen.observe(8, 20, 0, true, 1000);
        frozen.observe(8, 20, 0, true, 1100);
        assertFalse(frozen.trusted(true, 1100));
        frozen.observe(7, 20, 0, true, 1200);
        assertTrue(frozen.trusted(true, 1200));
        frozen.attempted(); frozen.attempted();
        assertFalse(frozen.trusted(true, 1250));
        assertEquals(2, frozen.observe(5, 20, 0, true, 1300));
        assertTrue(frozen.trusted(true, 1300));
    }

    @Test
    void invalidSamplesAndMaxHealthChangesNeverInventDamage() {
        AuraHealth health = new AuraHealth();
        health.observe(20, 20, 0, false, 1000);
        assertEquals(0, health.observe(Float.NaN, 20, 0, false, 1100));
        assertFalse(health.trusted(false, 1100));
        assertEquals(0, health.observe(8, 20, 0, false, 1200));
        assertEquals(0, health.observe(8, Float.POSITIVE_INFINITY, 0, false, 1300));
        assertFalse(health.trusted(false, 1300));
        health.observe(20, 20, 0, false, 1400);
        assertEquals(0, health.observe(10, 10, 0, false, 1500));
        assertEquals(2, health.observe(8, 10, 0, false, 1600));
        assertEquals(0, health.observe(30, 20, 0, false, 1700));
        assertFalse(health.trusted(false, 1700));
        assertTrue(Float.isFinite(health.fraction()));
    }
}
