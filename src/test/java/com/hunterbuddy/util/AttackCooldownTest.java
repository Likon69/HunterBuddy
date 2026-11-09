package com.hunterbuddy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AttackCooldownTest {
    @Test
    void swordDoesNotReuseEmptyHandRecharge() {
        float visible = AttackCooldown.ticks(4.0);
        float weapon = AttackCooldown.ticks(1.6);
        assertEquals(5.0f, visible);
        assertEquals(12.5f, weapon);
        assertEquals(1.0f, AttackCooldown.progress(5, 0.5f, visible));
        assertTrue(AttackCooldown.progress(5, 0.5f, weapon) < 1.0f);
        assertEquals(1.0f, AttackCooldown.progress(12, 0.5f, weapon));
    }

    @Test
    void axeWaitsForItsOwnRecharge() {
        float weapon = AttackCooldown.ticks(1.0);
        assertEquals(20.0f, weapon);
        assertTrue(AttackCooldown.progress(19, 0.5f, weapon) < 1.0f);
        assertEquals(1.0f, AttackCooldown.progress(20, 0.5f, weapon));
    }

    @Test
    void effectiveSpeedIncludesAttributeMultipliers() {
        assertEquals(20.0f / 1.92f, AttackCooldown.ticks(1.6 * 1.2), 0.0001f);
        assertEquals(20.0f / 0.8f, AttackCooldown.ticks(1.6 * 0.5), 0.0001f);
    }

    @Test
    void zeroSpeedNeverReportsFullCharge() {
        float cooldown = AttackCooldown.ticks(0.0);
        assertTrue(Float.isInfinite(cooldown));
        assertEquals(0.0f, AttackCooldown.progress(1000, 0.5f, cooldown));
    }

    @Test
    void progressClampsElapsedTicks() {
        assertEquals(0.0f, AttackCooldown.progress(-1, 0.0f, 12.5f));
        assertEquals(0.0f, AttackCooldown.progress(0, 0.0f, 12.5f));
        assertEquals(0.44f, AttackCooldown.progress(5, 0.5f, 12.5f), 0.0001f);
        assertEquals(1.0f, AttackCooldown.progress(50, 0.5f, 12.5f));
    }
}
