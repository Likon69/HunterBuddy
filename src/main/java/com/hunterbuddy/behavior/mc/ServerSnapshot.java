package com.hunterbuddy.behavior.mc;

public record ServerSnapshot(long pickups, long setbacks, boolean hurtThisTick, int lastAttackerId) {
}
