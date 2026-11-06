package com.hunterbuddy.pet;

public enum PetEvent {
    PLAYER_ARRIVE("player_arrive", 2, 15),
    PLAYER_CLOSE("player_close", 3, 20),
    PLAYER_LEAVE("player_leave", 1, 15),
    ITEM_SPOTTED("item_spotted", 1, 30),
    TRAIL_ACQUIRED("trail_acquired", 1, 30),
    TRAIL_LOST("trail_lost", 2, 30),
    STASH_FOUND("stash_found", 2, 15),
    SPAWNER_FOUND("spawner_found", 2, 15),
    TOTEM_POP("totem_pop", 3, 5),
    TOTEM_LAST("totem_last", 3, 30),
    ELYTRA_LOW("elytra_low", 3, 60),
    ROCKETS_EMPTY("rockets_empty", 3, 60),
    ROCKET_BOOST("rocket_boost", 1, 30),
    REGEAR("regear", 1, 30),
    LANDING_OK("landing_ok", 2, 15),
    LANDING_FAIL("landing_fail", 3, 15),
    MANUAL_TAKEOVER("manual_takeover", 1, 30),
    FLYING("flying", 0, 90),
    LAVA_CAST("lava_cast", 3, 30),
    EAT_GAPPLE("eat_gapple", 1, 30),
    TPS_DEAD("tps_dead", 3, 60),
    DISCONNECT("disconnect", 3, 15),
    PLAYER_DEATH("player_death", 3, 15),
    SLEEP("sleep", 1, 30),
    WAKE_UP("wake_up", 1, 15),
    DRINK("drink", 0, 30),
    SMOKE("smoke", 0, 30),
    THROW_BOTTLE("throw_bottle", 0, 20),
    LAUGH("laugh", 0, 30),
    IDLE("idle", 0, 30);

    public static final PetEvent[] ALL = values();
    public final String key;
    public final int priority;
    public final long cooldownMs;

    PetEvent(String key, int priority, int cooldownSeconds) {
        this.key = key;
        this.priority = priority;
        this.cooldownMs = cooldownSeconds * 1000L;
    }

    public boolean important() {
        return priority >= 2;
    }
}
