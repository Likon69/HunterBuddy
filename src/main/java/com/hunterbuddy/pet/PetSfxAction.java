package com.hunterbuddy.pet;

public enum PetSfxAction {
    STEP("step", 120), JUMP("jump", 200), LAND("land", 200), CLIMB("climb", 160), FALL("fall", 300),
    BEER_OPEN("beer_open", 800), DRINK("drink", 500), BURP("burp", 800), HIC("hic", 800),
    SMOKE_PUFF("smoke_puff", 120), COUGH("cough", 800), THROW("throw", 250),
    BOTTLE_BOUNCE("bottle_bounce", 120), BOTTLE_SHATTER("bottle_shatter", 120), SNORE("snore", 1000),
    SIT("sit", 500), LIE_DOWN("lie_down", 800), WAKE("wake", 800), SCARED("scared", 1000),
    DANCE_STEP("dance_step", 180), SHINE("shine", 800), TURN("turn", 250);

    public static final PetSfxAction[] ALL = values();
    public final String key;
    public final int interval;

    PetSfxAction(String key, int interval) {
        this.key = key;
        this.interval = interval;
    }
}
