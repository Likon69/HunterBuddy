package com.hunterbuddy.pet;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Random;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.sound.SoundInstance;

public final class PetDialogue {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final long REPEAT_COOLDOWN_MS = 900000;
    private static final long POSE_WAIT_MS = 12000;
    private final long[] pending = new long[PetEvent.ALL.length];
    private final long[] nextAllowed = new long[PetEvent.ALL.length];
    private final PetLineChoice choice = new PetLineChoice();
    private final Random random = new Random();
    public PetResources.Line current;
    public PetResources.Line queued;
    private PetEvent queuedEvent;
    private SoundInstance voice;
    private PetEvent speaking;
    private long startedAt;
    private long queuedAt;
    private long lastSpoken;
    private long lastReaction;
    private long lastFlavour;
    private long lastGesture;
    private long lastAlert;
    private boolean flyingArmed;
    private boolean boostArmed;
    private boolean wasGliding;
    private int revision = -1;

    public boolean offer(PetEvent event, long now, boolean asleep) {
        if (silenced(event, asleep) || event == PetEvent.FLYING && !flyingArmed || event == PetEvent.ROCKET_BOOST && !boostArmed) return false;
        int index = event.ordinal();
        if (now < nextAllowed[index]) return false;
        pending[index] = event == PetEvent.GREETING || event == PetEvent.SLEEP ? Long.MAX_VALUE : now + (event.important() ? 10000 : 4000);
        return true;
    }

    public PetEvent speaking() {
        return speaking;
    }

    private static boolean silenced(PetEvent event, boolean asleep) {
        return asleep && event != PetEvent.SLEEP && event != PetEvent.WAKE_UP && !event.important();
    }

    public void dropSleep() {
        pending[PetEvent.SLEEP.ordinal()] = 0;
    }

    public void glide(boolean gliding) {
        if (gliding && !wasGliding) {
            flyingArmed = true;
            boostArmed = true;
        }
        wasGliding = gliding;
    }

    public void update(long now, boolean focused, boolean stable, boolean quiet, boolean sounds, double gapSeconds, double flavourSeconds, boolean flavourOk, boolean active, boolean gliding, boolean asleep, PetFullPose poses, PetResources.Rig rig) {
        if (revision != PetResources.INSTANCE.revision) {
            reset(now);
            choice.reset();
            revision = PetResources.INSTANCE.revision;
        }
        if (!focused) {
            stop();
            for (PetEvent event : PetEvent.ALL) if (event != PetEvent.GREETING) pending[event.ordinal()] = 0;
            return;
        }
        if (quiet && speaking != null && !speaking.important()) stop();
        if (!sounds && voice != null) {
            MeteorClient.mc.getSoundManager().stop(voice);
            voice = null;
        }
        if (current != null && (now - startedAt > 30000 || (voice == null && now - startedAt > 4500)
            || (voice != null && now - startedAt > 600 && !MeteorClient.mc.getSoundManager().isPlaying(voice)))) stop();
        if (quiet && queuedEvent != null && !queuedEvent.important()) {
            queued = null;
            queuedEvent = null;
        }
        if (asleep && queuedEvent != null && silenced(queuedEvent, true)) {
            queued = null;
            queuedEvent = null;
        }
        if (current != null && posed(rig, current) && !poses.ready(current.pose)) stop();
        if (queued != null && posed(rig, queued) && now - queuedAt > POSE_WAIT_MS) {
            queued = null;
            queuedEvent = null;
        }
        if (queued != null && (!posed(rig, queued) || poses.ready(queued.pose))) start(now, sounds);
        PetEvent best = null;
        for (PetEvent event : PetEvent.ALL) {
            int index = event.ordinal();
            if (pending[index] == 0) continue;
            if (pending[index] < now || now < nextAllowed[index]) {
                pending[index] = 0;
                continue;
            }
            if (silenced(event, asleep)) {
                pending[index] = 0;
                continue;
            }
            if (quiet && !event.important()) continue;
            if (!budgetAllows(event, now, flavourSeconds, flavourOk, active, gliding)) continue;
            if (best == null || event.priority > best.priority) best = event;
        }
        if (best == null || !stable || now - lastSpoken < gapSeconds * 1000) return;
        if (current != null && (speaking == null || best.priority <= speaking.priority)) return;
        if (queuedEvent != null && best.priority <= queuedEvent.priority) return;
        PetResources.Line[] group = PetResources.INSTANCE.lines[best.ordinal()];
        pending[best.ordinal()] = 0;
        if (group == null || group.length == 0) return;
        if (best.gesture() && random.nextFloat() >= 0.25f) return;
        PetResources.Line chosen = choice.pick(group);
        if (chosen == null) return;
        stop();
        queued = chosen;
        queuedEvent = best;
        queuedAt = now;
        if (!posed(rig, chosen) || poses.ready(chosen.pose)) start(now, sounds);
    }

    private static boolean posed(PetResources.Rig rig, PetResources.Line line) {
        return PetFullPose.find(rig, line.pose) != null;
    }

    private void start(long now, boolean sounds) {
        PetResources.Line chosen = queued;
        current = chosen;
        speaking = queuedEvent;
        nextAllowed[speaking.ordinal()] = now + cooldownFor(speaking, PetResources.INSTANCE.lines[speaking.ordinal()]);
        queued = null;
        queuedEvent = null;
        startedAt = now;
        lastSpoken = now;
        if (speaking == PetEvent.FLYING) flyingArmed = false;
        if (speaking == PetEvent.ROCKET_BOOST) boostArmed = false;
        LOG.info("[pet] said " + speaking.key + ": " + chosen.value);
        PetEvent.Kind kind = speaking.kind();
        if (kind == PetEvent.Kind.FLAVOUR) {
            if (speaking.gesture()) lastGesture = now;
            else lastFlavour = now;
        } else if (kind == PetEvent.Kind.ALERT) lastAlert = now;
        else if (kind == PetEvent.Kind.REACTION && speaking != PetEvent.GREETING && speaking != PetEvent.SLEEP) lastReaction = now;
        if (sounds && MeteorClient.mc.getSoundManager().get(chosen.sound) != null) {
            voice = chosen.voice;
            MeteorClient.mc.getSoundManager().play(voice);
        }
    }

    private static long cooldownFor(PetEvent event, PetResources.Line[] group) {
        boolean oncePerFlight = event == PetEvent.FLYING || event == PetEvent.ROCKET_BOOST;
        boolean repeats = !event.important() && group.length == 1;
        return oncePerFlight || repeats ? Math.max(event.cooldownMs(), REPEAT_COOLDOWN_MS) : event.cooldownMs();
    }

    private boolean budgetAllows(PetEvent event, long now, double flavourSeconds, boolean flavourOk, boolean active, boolean gliding) {
        return switch (event.kind()) {
            case ALERT -> true;
            case FLAVOUR -> flavourOk && now - lastAlert >= 60000 && (event == PetEvent.FLYING || active && !gliding)
                && now - (event.gesture() ? lastGesture : lastFlavour) >= (event.gesture() ? 180000 : flavourSeconds * 1000);
            case REACTION -> event == PetEvent.GREETING || event == PetEvent.SLEEP || now - lastReaction >= 90000;
        };
    }

    public void stop() {
        if (voice != null) MeteorClient.mc.getSoundManager().stop(voice);
        voice = null;
        current = null;
        queued = null;
        queuedEvent = null;
        speaking = null;
    }

    public void reset(long now) {
        stop();
        Arrays.fill(pending, 0);
        nextAllowed[PetEvent.GREETING.ordinal()] = 0;
        lastSpoken = 0;
        lastReaction = now - 75000;
        lastFlavour = now;
        lastGesture = now;
        lastAlert = 0;
        flyingArmed = false;
        boostArmed = false;
        wasGliding = false;
    }
}
