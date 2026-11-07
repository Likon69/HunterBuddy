package com.hunterbuddy.pet;

import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;

public final class PetBrain {
    private static final float GREETING_PATIENCE = 60;
    private static final float GREETING_TIMEOUT = 30;
    private static final float SEAT_GRACE = 2;
    private static final PetState[] ACTIVITIES = {PetState.WALK, PetState.REST, PetState.SMOKE, PetState.DRINK,
        PetState.THROW, PetState.SIT, PetState.DANCE, PetState.TURN, PetState.STAGGER, PetState.STUMBLE, PetState.SETTLE,
        PetState.CUSTOM};
    private final Random random;
    private final PetState[] recent = new PetState[4];
    private final float[] nextFamily = new float[7];
    public PetState state = PetState.WALK;
    public float age;
    public float duration = 6;
    public float drunkenness;
    public boolean highEdge;
    public boolean atEdge;
    public boolean seatReachable;
    public boolean seeking;
    public boolean elevated;
    public int surfaceKind;
    public boolean grounded = true;
    public boolean roomToLie = true;
    public boolean externalDanger;
    public boolean idle;
    public boolean worldStable = true;
    public int danceImageCount;
    public boolean imageDance;
    public boolean imageDrink;
    public boolean imageSit;
    public int danceStyle;
    public int danceImageStart;
    public PetAnimation[] animations = new PetAnimation[0];
    public PetAnimation animation;
    public Consumer<String> announce;
    public boolean preparingPose;
    public boolean imagePose;
    public boolean holdingOverlay;
    public boolean sitDrink;
    public boolean keepSeated;
    public float minimumSleep = 30;
    public float maximumSleep = 120;
    private float clock;
    private float dangerUntil;
    private float speechUntil;
    private boolean actionDone;
    private boolean sleepLine;
    private boolean idleSleep;
    private int recentIndex;
    private float sleepMinimum;
    private boolean lastImageDance = true;
    private boolean lastImageDrink;
    private boolean lastImageSit;
    private float seatLost;
    private float ambientAt;
    private int lastPuppetStyle = -1;
    private PetAnimation lastAnimation;
    private boolean greetingPending;
    private float greetingPublishedAt = -1;
    private boolean sitPlanned;
    private boolean sitDrinkDone;
    private int sitDrinksLeft;
    private int sitDrinksTaken;
    private boolean sitBlockLogged;
    private float lastDrinkAt = -1000;
    private float sitDrinkAge;
    private float sitDrinkLength;
    private float nextSitDrink;
    private PetState alertResponse = PetState.PANIC;
    private PetState wakeTarget;
    public int instance;

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
        dangerUntil = 0;
        speechUntil = 0;
        recentIndex = 0;
        externalDanger = false;
        preparingPose = false;
        imagePose = false;
        holdingOverlay = false;
        imageDance = false;
        imageDrink = false;
        lastImageDrink = false;
        lastImageSit = false;
        imageSit = false;
        ambientAt = 0;
        lastPuppetStyle = -1;
        animation = null;
        lastAnimation = null;
        greetingPending = true;
        greetingPublishedAt = -1;
        alertResponse = PetState.PANIC;
        wakeTarget = null;
        Arrays.fill(recent, null);
        Arrays.fill(nextFamily, 0);
    }

    public void spoke() {
        speechUntil = clock + 12 + random.nextFloat() * 12;
    }

    public void greeted() {
        if (!greetingPending) return;
        greetingPending = false;
        enter(PetState.WAVE, 4, 6);
    }

    public boolean asleep() {
        return state == PetState.SETTLE || state == PetState.LIE_DOWN || state == PetState.SLEEP;
    }

    public boolean resting() {
        return state == PetState.SETTLE || state == PetState.LIE_DOWN || state == PetState.SLEEP || state == PetState.RISE;
    }

    public boolean danger() {
        return externalDanger || clock < dangerUntil;
    }

    public void react(PetEvent event, boolean hide, boolean accepted) {
        PetState response;
        switch (event) {
            case PLAYER_ARRIVE, PLAYER_CLOSE -> response = hide ? PetState.HIDE : PetState.PANIC;
            case TOTEM_POP, TOTEM_LAST, LANDING_FAIL, PLAYER_DEATH -> response = PetState.PANIC;
            case STASH_FOUND, SPAWNER_FOUND, LANDING_OK -> response = PetState.CELEBRATE;
            default -> response = null;
        }
        boolean wake = event == PetEvent.WAKE_UP || accepted && event.important();
        if (wake && asleep() && !danger() && wakeTarget == null) wakeTarget = response == PetState.CELEBRATE ? response : PetState.REST;
        if (response == null) return;
        if (response != PetState.CELEBRATE) {
            dangerUntil = clock + 15;
            alertResponse = response;
        }
        if (resting() || state == response || clock < nextFamily[4]) return;
        nextFamily[4] = clock + 12;
        enter(response, 3, 6);
    }

    public void update(float dt, boolean idle, double maxDrunk, double chatter, boolean bottles) {
        this.idle = idle;
        clock += dt;
        if (!preparingPose && !holdingOverlay) {
            age += dt;
            if (sitDrink) sitDrinkAge += dt;
        }
        drunkenness = Math.max(0, Math.min((float) maxDrunk, drunkenness - dt * 0.007f));
        if (preparingPose || holdingOverlay) return;
        seatedDrink();
        if (state == PetState.SIT && !atEdge) {
            seatLost += dt;
            if (elevated || seeking) age = 0;
            else if (seatLost >= SEAT_GRACE) enter(PetState.REST, 5, 9);
        } else seatLost = 0;
        ambient(chatter);
        if (resting()) {
            boolean alarm = (danger() || wakeTarget != null) && state != PetState.RISE;
            if (state == PetState.SLEEP && !sleepLine && age >= 2) {
                sleepLine = true;
                PetEvents.publish(PetEvent.SLEEP);
            }
            if (!alarm && (age < duration || state == PetState.SLEEP && idle)) return;
            switch (state) {
                case SETTLE -> {
                    if (alarm) alarmed();
                    else enter(PetState.LIE_DOWN, 2.5f, 3.5f);
                }
                case LIE_DOWN -> {
                    if (alarm) enter(PetState.RISE, 2.5f, 3.5f);
                    else {
                        sleepLine = false;
                        idleSleep = idle;
                        sleepMinimum = Math.max(30, minimumSleep);
                        enter(PetState.SLEEP, sleepMinimum, Math.max(sleepMinimum, maximumSleep));
                    }
                }
                case SLEEP -> {
                    nextFamily[3] = clock + (idle ? 60 : 180) + random.nextFloat() * (idle ? 30 : 120);
                    enter(PetState.RISE, 2.5f, 3.5f);
                    if (idleSleep) PetEvents.publish(PetEvent.WAKE_UP);
                }
                case RISE -> {
                    if (danger() || wakeTarget != null) alarmed();
                    else enter(PetState.REST, 7, 14);
                }
                default -> { }
            }
            return;
        }
        if (!actionDone && age >= duration * 0.6f && !(state == PetState.THROW && imagePose)) {
            actionDone = true;
            if (state == PetState.DRINK) {
                lastDrinkAt = clock;
                drunkenness = Math.min((float) maxDrunk, drunkenness + 0.18f);
                PetEvents.publish(PetEvent.DRINK);
            } else if (state == PetState.SMOKE) PetEvents.publish(PetEvent.SMOKE);
            else if (state == PetState.THROW && bottles) PetEvents.publish(PetEvent.THROW_BOTTLE);
        }
        if (greetingPending && greetingPublishedAt < 0 && worldStable && grounded && (!danger() || clock >= GREETING_PATIENCE)
            && (state == PetState.WALK || state == PetState.REST)) {
            greetingPublishedAt = clock;
            PetEvents.publish(PetEvent.GREETING);
        }
        if (greetingPending && greetingPublishedAt >= 0 && clock - greetingPublishedAt >= GREETING_TIMEOUT) greetingPending = false;
        if (idle && (state == PetState.DANCE || state == PetState.SIT)) age = duration;
        if (age < duration) return;
        int family = family(state);
        if (family == 1) nextFamily[1] = clock + 90 + random.nextFloat() * 60;
        if (family == 2) nextFamily[2] = clock + 18 + random.nextFloat() * 20;
        if (family == 6) nextFamily[6] = clock + 45 + random.nextFloat() * 45;
        float total = 0;
        PetState choice = null;
        for (PetState candidate : ACTIVITIES) {
            float weight = weight(candidate, bottles);
            total += weight;
            if (weight > 0 && random.nextFloat() * total < weight) choice = candidate;
        }
        if (choice == null) choice = state == PetState.REST ? (idle ? PetState.TURN : PetState.WALK) : PetState.REST;
        if (choice == PetState.SETTLE) enter(choice, 1.8f, 2.5f);
        else if (choice == PetState.TURN) enter(choice, 0.6f, 1);
        else if (choice == PetState.SIT) enter(choice, 30, 75);
        else if (choice == PetState.REST) enter(choice, 5, 14);
        else if (choice == PetState.THROW) enter(choice, 2, 3);
        else if (choice == PetState.CUSTOM) startAnimation();
        else if (choice == PetState.DANCE) enter(choice, 15, 120);
        else if (choice == PetState.WALK) enter(choice, 10, 20);
        else enter(choice, 4, 10);
    }

    private void startAnimation() {
        float total = 0;
        PetAnimation chosen = null;
        for (PetAnimation candidate : animations) {
            float weight = animationWeight(candidate);
            total += weight;
            if (weight > 0 && random.nextFloat() * total < weight) chosen = candidate;
        }
        animation = chosen;
        lastAnimation = chosen;
        enter(PetState.CUSTOM, chosen.length(), chosen.length());
        if (announce != null) announce.accept("[pet] animation " + chosen.name());
    }

    private float animationWeight(PetAnimation candidate) {
        return candidate == lastAnimation && animations.length > 1 ? 0 : candidate.weight();
    }

    private float customWeight() {
        float total = 0;
        for (PetAnimation candidate : animations) total += animationWeight(candidate);
        return total;
    }

    public float drinkProgress() {
        if (state == PetState.DRINK) return age / duration;
        return sitDrink ? Math.min(1, sitDrinkAge / sitDrinkLength) : 0;
    }

    private void seatedDrink() {
        if (state != PetState.SIT || imageSit) {
            sitDrink = false;
            sitPlanned = false;
            return;
        }
        if (!sitPlanned) {
            sitPlanned = true;
            sitDrinksLeft = random.nextBoolean() ? 1 : 2;
            sitDrinksTaken = 0;
            sitBlockLogged = false;
            nextSitDrink = duration * (0.1f + random.nextFloat() * 0.15f);
            log("[pet] seated drink planned " + sitDrinksLeft);
        }
        if (sitDrink) {
            if (!sitDrinkDone && sitDrinkAge >= sitDrinkLength * 0.6f) {
                sitDrinkDone = true;
                drunkenness = Math.min(1, drunkenness + 0.18f);
                PetEvents.publish(PetEvent.DRINK);
            }
            if (sitDrinkAge >= sitDrinkLength || danger()) {
                sitDrink = false;
                sitDrinksLeft--;
                sitDrinksTaken++;
                nextSitDrink = age + 12 + random.nextFloat() * 10;
            }
        } else if (sitDrinksLeft > 0 && age >= nextSitDrink) {
            String reason = sitDrinkBlock();
            if (reason == null) {
                sitDrink = true;
                sitDrinkDone = false;
                sitDrinkAge = 0;
                sitDrinkLength = 6 + random.nextFloat() * 2;
                lastDrinkAt = clock;
                log("[pet] seated drink");
            } else if (!sitBlockLogged) {
                sitBlockLogged = true;
                log("[pet] seated drink blocked: " + reason);
            }
        }
    }

    private String sitDrinkBlock() {
        if (!elevated || !atEdge || !grounded) return "not seated at an edge";
        if (idle) return "idle";
        if (danger()) return "danger";
        if (clock < speechUntil) return "speaking";
        boolean cooled = sitDrinksTaken > 0 || clock >= nextFamily[1] || clock - lastDrinkAt >= 60;
        if (!cooled) return "drink cooldown";
        if (age + 9 >= duration) return "sit nearly over";
        return null;
    }

    private void log(String line) {
        if (announce != null) announce.accept(line);
    }

    private void alarmed() {
        nextFamily[4] = clock + 12;
        if (wakeTarget != null) {
            PetState target = wakeTarget;
            wakeTarget = null;
            if (target == PetState.REST) enter(target, 5, 9);
            else enter(target, 3, 6);
        } else if (alertResponse == PetState.HIDE) enter(PetState.WALK, 4, 8);
        else enter(alertResponse, 3, 6);
    }

    private void ambient(double chatter) {
        if (chatter <= 0) {
            ambientAt = 0;
            return;
        }
        if (ambientAt == 0) ambientAt = clock + ambientInterval(chatter);
        boolean calm = state == PetState.WALK || state == PetState.REST || state == PetState.SIT;
        if (!calm || !grounded || danger() || idle || clock < speechUntil || !worldStable || clock < ambientAt) return;
        ambientAt = clock + ambientInterval(chatter);
        PetEvents.publish(PetEvent.IDLE);
    }

    private float ambientInterval(double chatter) {
        return (240 + random.nextFloat() * 240) / (float) chatter;
    }

    private float weight(PetState candidate, boolean bottles) {
        int family = family(candidate);
        if (candidate == state || clock < nextFamily[family]) return 0;
        if (idle && candidate != PetState.SETTLE && candidate != PetState.REST) return 0;
        if (!grounded && candidate != PetState.WALK && candidate != PetState.REST) return 0;
        if (candidate == PetState.DRINK && (clock < speechUntil || danger())) return 0;
        if (candidate == PetState.THROW && (!bottles || danger())) return 0;
        if (candidate == PetState.SIT && (!grounded || !seatReachable)) return 0;
        if ((candidate == PetState.STAGGER || candidate == PetState.STUMBLE) && drunkenness < 0.25f) return 0;
        if (greetingPending && candidate != PetState.WALK && candidate != PetState.REST) return 0;
        if (candidate == PetState.CUSTOM && danger()) return 0;
        if (candidate == PetState.SETTLE && (!grounded || surfaceKind == 1 || !roomToLie || danger() || clock < speechUntil)) return 0;
        float weight = switch (candidate) {
            case WALK -> danger() ? 6 : 4;
            case REST -> 2;
            case SIT -> highEdge ? 12 : 6;
            case SETTLE -> idle ? 12 : 0;
            case SMOKE, DRINK -> 4.5f;
            case THROW -> 4.5f;
            case CUSTOM -> customWeight();
            case DANCE -> danger() ? 0 : 4;
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
            case SIT, WAVE, TURN, STUMBLE -> 2;
            case SETTLE, LIE_DOWN, SLEEP, RISE -> 3;
            case PANIC, HIDE, CELEBRATE -> 4;
            case DANCE -> 5;
            case CUSTOM -> 6;
            default -> 0;
        };
    }

    public boolean seatHeld() {
        return seatLost < SEAT_GRACE;
    }

    public void dropSitImage() {
        if (state == PetState.SIT && imageSit) {
            imageSit = false;
            lastImageSit = false;
        }
    }

    public void denyImage() {
        dropSitImage();
        if (state == PetState.DRINK && imageDrink) {
            imageDrink = false;
            lastImageDrink = false;
        }
        if (state == PetState.DANCE && imageDance) {
            imageDance = false;
            lastImageDance = false;
            pickPuppetStyle();
        }
    }

    private void pickPuppetStyle() {
        danceStyle = random.nextInt(4);
        if (danceStyle == lastPuppetStyle) danceStyle = (danceStyle + 1) % 4;
        lastPuppetStyle = danceStyle;
    }

    private void enter(PetState next, float min, float max) {
        if (announce != null) log("[pet] state " + state + " -> " + next);
        sitDrink = false;
        sitPlanned = false;
        if (state == PetState.SIT && sitDrinksTaken > 0) nextFamily[1] = clock + 90 + random.nextFloat() * 60;
        sitDrinksTaken = 0;
        if (state == PetState.DANCE) nextFamily[5] = clock + 60 + random.nextFloat() * 40;
        state = next;
        if (next == PetState.SIT) {
            imageSit = !lastImageSit;
            lastImageSit = imageSit;
        }
        if (next == PetState.DRINK) {
            imageDrink = !lastImageDrink;
            lastImageDrink = imageDrink;
        }
        if (next == PetState.DANCE) {
            PetEvents.publish(PetEvent.DANCE);
            imageDance = danceImageCount > 0 && !lastImageDance;
            lastImageDance = imageDance;
            if (imageDance) danceImageStart = random.nextInt(danceImageCount);
            else pickPuppetStyle();
        }
        seatLost = 0;
        instance++;
        age = 0;
        duration = min + random.nextFloat() * (max - min);
        actionDone = false;
        if (next != PetState.LIE_DOWN && next != PetState.SLEEP && next != PetState.RISE) {
            recent[recentIndex] = next;
            recentIndex = (recentIndex + 1) % recent.length;
        }
    }
}
