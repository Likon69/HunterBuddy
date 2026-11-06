package com.hunterbuddy.pet;

import java.util.Random;

public final class PetBrain {
    private final Random random = new Random();
    public PetState state = PetState.WALK;
    public float age;
    public float duration = 6;
    public float drunkenness;
    private boolean actionDone;
    private float ambience;

    public void reset() {
        state = PetState.WALK;
        age = 0;
        duration = 4 + random.nextFloat() * 5;
        drunkenness = 0;
        actionDone = false;
        ambience = 0;
    }

    public void react(PetEvent event, boolean hide) {
        switch (event) {
            case PLAYER_ARRIVE, PLAYER_CLOSE -> enter(hide ? PetState.HIDE : PetState.PANIC, 5, 9);
            case TOTEM_POP, TOTEM_LAST, LANDING_FAIL, PLAYER_DEATH -> enter(PetState.PANIC, 3, 5);
            case STASH_FOUND, SPAWNER_FOUND, LANDING_OK -> enter(PetState.CELEBRATE, 3, 5);
            default -> { }
        }
    }

    public void update(float dt, boolean asleep, double maxDrunk, double chatter, boolean bottles) {
        drunkenness = Math.max(0, Math.min((float) maxDrunk, drunkenness - dt * 0.007f));
        age += dt;
        ambience += dt;
        if (asleep && state != PetState.SLEEP) {
            enter(PetState.SLEEP, 3600, 3600);
            PetEvents.publish(PetEvent.SLEEP);
        } else if (!asleep && state == PetState.SLEEP) {
            enter(PetState.REST, 2, 4);
            PetEvents.publish(PetEvent.WAKE_UP);
        }
        if (state == PetState.SLEEP) return;
        if (!actionDone && age > duration * 0.6f) {
            actionDone = true;
            if (state == PetState.DRINK) {
                drunkenness = Math.min((float) maxDrunk, drunkenness + 0.18f);
                PetEvents.publish(PetEvent.DRINK);
            } else if (state == PetState.SMOKE) PetEvents.publish(PetEvent.SMOKE);
            else if (state == PetState.THROW && bottles) PetEvents.publish(PetEvent.THROW_BOTTLE);
        }
        if (chatter > 0 && ambience > 8 + 100 / chatter) {
            ambience = 0;
            PetEvents.publish(random.nextBoolean() ? PetEvent.IDLE : PetEvent.LAUGH);
        }
        if (age < duration) return;
        float choice = random.nextFloat();
        if (choice < 0.34f) enter(PetState.WALK, 4, 12);
        else if (choice < 0.52f) enter(PetState.REST, 3, 7);
        else if (choice < 0.69f) enter(PetState.SMOKE, 4, 7);
        else if (choice < 0.9f || !bottles) enter(PetState.DRINK, 4, 6);
        else enter(PetState.THROW, 2, 3);
        if (bottles && drunkenness > 0.4f && random.nextFloat() < drunkenness * 0.45f) enter(PetState.THROW, 2, 3);
    }

    private void enter(PetState next, float min, float max) {
        state = next;
        age = 0;
        duration = min + random.nextFloat() * (max - min);
        actionDone = false;
    }
}
