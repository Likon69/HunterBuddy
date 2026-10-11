package com.hunterbuddy.behavior.core;

import java.util.Objects;

public record Sensors(
    double x, double y, double z, int feetX, int feetY, int feetZ,
    float yaw, float pitch, double velocityX, double velocityY, double velocityZ,
    boolean onGround, boolean inWater, boolean submerged, boolean inLava,
    boolean gliding, boolean climbing, boolean horizontalCollision,
    float health, int food, int air, boolean dead, boolean hurtThisTick,
    int lastAttackerId, int selectedSlot, int serverSlot,
    boolean screenOpen, boolean screenOurs, String screenType, boolean usingItem,
    boolean breakingBlock, String dimensionKey, int worldId, int playerId,
    boolean inQueue, boolean serverLagging, boolean baritoneAvailable,
    boolean baritonePathing, boolean baritoneHasPath, boolean baritoneGoalActive,
    boolean baritoneCalcInProgress, boolean baritoneInControl, String lastPathEvent,
    boolean elytraActive, boolean elytraDestinationSet, long blocksMined,
    long pickups, long setbacks, boolean mlepMining, boolean regearRunning,
    boolean regearPending, boolean eating, boolean combatTarget, String movementHolder,
    int rockets, int obsidian, int enderChests, int playersVisible
) {
    public Sensors {
        Objects.requireNonNull(screenType);
        Objects.requireNonNull(dimensionKey);
        Objects.requireNonNull(lastPathEvent);
    }
}
