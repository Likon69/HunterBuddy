package com.hunterbuddy.pet;

import java.util.Arrays;
import java.util.Random;

public final class PetBrain {
    private static final PetState[] ACTIVITIES = {PetState.WALK, PetState.REST, PetState.SMOKE, PetState.DRINK,
        PetState.THROW, PetState.SIT, PetState.DANCE, PetState.WAVE, PetState.TURN, PetState.STAGGER, PetState.STUMBLE, PetState.SETTLE};
    private final Random random;
    private final PetState[] recent = new PetState[4];
    private final float[] nextFamily = new float[5];
    public PetState state = PetState.WALK;
    public float age;
    public float duration = 6;
    public float drunkenness;
    public boolean highEdge;
    public boolean atEdge;
    public boolean seatAvailable = true;
    public boolean elevated;
    public int surfaceKind;
    public boolean grounded = true;
    public boolean roomToLie = true;
    public boolean night;
    public boolean externalDanger;
    public boolean canDrink;
    public int danceImageCount;
    public boolean imageDance;
    public int danceStyle;
    public int danceImageStart;
    public boolean preparingPose;
    public boolean imagePose;
    public boolean holdingOverlay;
    public float poseTurn;
    public float minimumSleep = 30;
    public float maximumSleep = 120;
    private float clock;
    private float ambience;
    private float dangerUntil;
    private float speechUntil;
    private boolean actionDone;
    private boolean sleepLine;
    private int recentIndex;
    private float sleepMinimum;
    private boolean lastImageDance;
    private int lastPuppetStyle = -1;
    private boolean greetingPending;
    private float nextGreeting;

    public PetBrain() {
        random = new Random();
    }

    public PetBrain(long seed) {
        random = new Random(seed);
    }

    public void reset() {
        state = PetState.WALK;
        age = 0;
        duration = 4 + random.nextFloat() * 5;
        drunkenness = 0;
        actionDone = false;
        sleepLine = false;
        clock = 0;
        ambience = 0;
        dangerUntil = 0;
        speechUntil = 0;
        recentIndex = 0;
        externalDanger = false;
        preparingPose = false;
        imagePose = false;
        holdingOverlay = false;
        poseTurn = 0;
        imageDance = false;
        lastImageDance = false;
        lastPuppetStyle = -1;
        greetingPending = true;
        nextGreeting = 0;
        Arrays.fill(recent, null);
        Arrays.fill(nextFamily, 0);
    }

    public void greet() {
        if (clock >= nextGreeting && state != PetState.WAVE) greetingPending = true;
    }

    public void spoke() {
        speechUntil = clock + 12 + random.nextFloat() * 12;
    }

    public boolean resting() {
        return state == PetState.SETTLE || state == PetState.LIE_DOWN || state == PetState.SLEEP || state == PetState.RISE;
    }

    public boolean danger() {
        return externalDanger || clock < dangerUntil;
    }

    public void react(PetEvent event, boolean hide) {
        PetState response;
        switch (event) {
            case PLAYER_ARRIVE, PLAYER_CLOSE -> response = hide ? PetState.HIDE : PetState.PANIC;
            case TOTEM_POP, TOTEM_LAST, LANDING_FAIL, PLAYER_DEATH -> response = PetState.PANIC;
            case STASH_FOUND, SPAWNER_FOUND, LANDING_OK -> response = PetState.CELEBRATE;
            default -> { return; }
        }
        if (response != PetState.CELEBRATE) dangerUntil = clock + 15;
        if (resting() || state == response || clock < nextFamily[4]) return;
        nextFamily[4] = clock + 12;
        enter(response, 3, 6);
    }

    public void update(float dt, boolean idle, double maxDrunk, double chatter, boolean bottles) {
        clock += dt;
        if (!preparingPose && !holdingOverlay) age += dt;
        ambience += dt;
        drunkenness = Math.max(0, Math.min((float) maxDrunk, drunkenness - dt * 0.007f));
        if (preparingPose || holdingOverlay) return;
        if (state == PetState.SIT && !atEdge) {
            if (!elevated) enter(PetState.REST, 5, 9);
            else age = 0;
        }
        if (resting()) {
            boolean wake = state == PetState.SLEEP && (danger() || greetingPending) && age >= sleepMinimum;
            if (state == PetState.SLEEP && !sleepLine && age >= 2) {
                sleepLine = true;
                if (random.nextFloat() < 0.65f) PetEvents.publish(PetEvent.SLEEP);
            }
            if (age < duration && !wake) return;
            switch (state) {
                case SETTLE -> enter(PetState.LIE_DOWN, 2.5f, 3.5f);
                case LIE_DOWN -> {
                    sleepLine = false;
                    sleepMinimum = Math.max(30, minimumSleep);
                    enter(PetState.SLEEP, sleepMinimum, Math.max(sleepMinimum, maximumSleep));
                }
                case SLEEP -> {
                    nextFamily[3] = clock + 180 + random.nextFloat() * 120;
                    enter(PetState.RISE, 4, 6);
                    PetEvents.publish(PetEvent.WAKE_UP);
                }
                case RISE -> enter(PetState.REST, 7, 14);
                default -> { }
            }
            return;
        }
        if (!actionDone && age >= duration * 0.6f) {
            actionDone = true;
            if (state == PetState.DRINK) {
                drunkenness = Math.min((float) maxDrunk, drunkenness + 0.18f);
                PetEvents.publish(PetEvent.DRINK);
            } else if (state == PetState.SMOKE) PetEvents.publish(PetEvent.SMOKE);
            else if (state == PetState.THROW && bottles) PetEvents.publish(PetEvent.THROW_BOTTLE);
        }
        if (chatter > 0 && clock >= speechUntil && !danger() && ambience > 8 + 100 / chatter) {
            ambience = 0;
            PetEvents.publish(random.nextBoolean() ? PetEvent.IDLE : PetEvent.LAUGH);
        }
        if (greetingPending && grounded && !danger() && clock >= nextFamily[2]
            && (state == PetState.WALK || state == PetState.REST)) {
            greetingPending = false;
            nextGreeting = clock + 120;
            enter(PetState.WAVE, 4, 6);
            return;
        }
        if (age < duration) return;
        int family = family(state);
        if (family == 1) nextFamily[1] = clock + 40 + random.nextFloat() * 35;
        if (family == 2) nextFamily[2] = clock + 18 + random.nextFloat() * 20;
        float total = 0;
        PetState choice = null;
        for (PetState candidate : ACTIVITIES) {
            float weight = weight(candidate, idle, bottles);
            total += weight;
            if (weight > 0 && random.nextFloat() * total < weight) choice = candidate;
        }
        if (choice == null) choice = state == PetState.REST ? PetState.WALK : PetState.REST;
        if (choice == PetState.SETTLE) enter(choice, 1.8f, 2.5f);
        else if (choice == PetState.TURN) enter(choice, 0.6f, 1);
        else if (choice == PetState.SIT) enter(choice, 12, 28);
        else if (choice == PetState.REST) enter(choice, 5, 14);
        else enter(choice, 4, 10);
    }

    private float weight(PetState candidate, boolean idle, boolean bottles) {
        int family = family(candidate);
        if (candidate == state || clock < nextFamily[family]) return 0;
        if (!grounded && candidate != PetState.WALK && candidate != PetState.REST) return 0;
        if (candidate == PetState.DRINK && (!canDrink || clock < speechUntil || danger())) return 0;
        if (candidate == PetState.THROW && (!bottles || danger())) return 0;
        if (candidate == PetState.SIT && (!elevated || !grounded || !seatAvailable)) return 0;
        if ((candidate == PetState.STAGGER || candidate == PetState.STUMBLE) && drunkenness < 0.25f) return 0;
        if (candidate == PetState.SETTLE && (!grounded || surfaceKind == 1 || !roomToLie || danger() || clock < speechUntil)) return 0;
        float weight = switch (candidate) {
            case WALK -> danger() ? 6 : 3;
            case REST -> 2;
            case SIT -> highEdge ? 18 : 8;
            case SETTLE -> idle ? 12 : night ? 1.4f + drunkenness : 0.18f;
            case SMOKE, DRINK, THROW -> 0.55f;
            case STAGGER, STUMBLE -> drunkenness * 2;
            default -> danger() ? 0 : 0.7f;
        };
        for (int i = 0; i < recent.length; i++) {
            if (recent[(recentIndex + recent.length - 1 - i) % recent.length] == candidate) weight *= 0.05f + i * 0.1f;
        }
        return weight;
    }

    private static int family(PetState value) {
        return switch (value) {
            case SMOKE, DRINK, THROW -> 1;
            case SIT, DANCE, WAVE, TURN, STUMBLE -> 2;
            case SETTLE, LIE_DOWN, SLEEP, RISE -> 3;
            case PANIC, HIDE, CELEBRATE -> 4;
            default -> 0;
        };
    }

    private void enter(PetState next, float min, float max) {
        state = next;
        if (next == PetState.DANCE) {
            imageDance = danceImageCount > 0 && !lastImageDance;
            lastImageDance = imageDance;
            if (imageDance) danceImageStart = random.nextInt(danceImageCount);
            else {
                danceStyle = random.nextInt(4);
                if (danceStyle == lastPuppetStyle) danceStyle = (danceStyle + 1) % 4;
                lastPuppetStyle = danceStyle;
            }
        }
        age = 0;
        duration = min + random.nextFloat() * (max - min);
        actionDone = false;
        if (next != PetState.LIE_DOWN && next != PetState.SLEEP && next != PetState.RISE) {
            recent[recentIndex] = next;
            recentIndex = (recentIndex + 1) % recent.length;
        }
    }
}
