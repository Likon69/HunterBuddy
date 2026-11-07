package com.hunterbuddy.pet;

public enum PetSfxAction {
    STEP("step", 120, 0), JUMP("jump", 200, 0), LAND("land", 200, 0), FALL("fall", 300, 0),
    BEER_OPEN("beer_open", 800, 1), DRINK("drink", 500, 1), BURP("burp", 800, 1), HIC("hic", 800, 1),
    SMOKE_PUFF("smoke_puff", 120, 1), COUGH("cough", 800, 1), THROW("throw", 250, 1),
    BOTTLE_BOUNCE("bottle_bounce", 120, 1), BOTTLE_SHATTER("bottle_shatter", 120, 1), SNORE("snore", 1000, 1),
    SIT("sit", 500, 1), LIE_DOWN("lie_down", 800, 1), WAKE("wake", 800, 1), SCARED("scared", 1000, 2),
    TURN("turn", 1500, 0);

    public static final PetSfxAction[] ALL = values();
    public final String key;
    public final int interval;
    public final int priority;

    PetSfxAction(String key, int interval, int priority) {
        this.key = key;
        this.interval = interval;
        this.priority = priority;
    }
}
