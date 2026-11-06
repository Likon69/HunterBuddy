package com.hunterbuddy.pet;

import java.util.Arrays;
import java.util.Random;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.sound.SoundInstance;

public final class PetDialogue {
    private final long[] pending = new long[PetEvent.ALL.length];
    private final long[] nextAllowed = new long[PetEvent.ALL.length];
    private final PetResources.Line[] recent = new PetResources.Line[4];
    private final Random random = new Random();
    public PetResources.Line current;
    public PetResources.Line queued;
    private PetEvent queuedEvent;
    private SoundInstance voice;
    private PetEvent speaking;
    private int recentIndex;
    private long startedAt;
    private long lastSpoken;
    private int revision = -1;

    public void offer(PetEvent event, long now) {
        int index = event.ordinal();
        if (now < nextAllowed[index]) return;
        pending[index] = now + (event.important() ? 180000 : 15000);
    }

    public void update(long now, boolean focused, boolean quiet, boolean sounds, double gapSeconds, PetFullPose poses, PetResources.Rig rig) {
        if (revision != PetResources.INSTANCE.revision) {
            reset();
            revision = PetResources.INSTANCE.revision;
        }
        if (!focused) {
            stop();
            Arrays.fill(pending, 0);
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
        if (queued != null && (PetFullPose.find(rig, queued.pose) == null || poses.ready(queued.pose))) start(now, sounds);
        PetEvent best = null;
        for (PetEvent event : PetEvent.ALL) {
            int index = event.ordinal();
            if (pending[index] == 0) continue;
            if (pending[index] < now || now < nextAllowed[index]) {
                pending[index] = 0;
                continue;
            }
            if (quiet && !event.important()) continue;
            if (best == null || event.priority > best.priority) best = event;
        }
        if (best == null || now - lastSpoken < gapSeconds * 1000) return;
        if (current != null && (speaking == null || best.priority <= speaking.priority)) return;
        if (queuedEvent != null && best.priority <= queuedEvent.priority) return;
        PetResources.Line[] group = PetResources.INSTANCE.lines[best.ordinal()];
        pending[best.ordinal()] = 0;
        if (group == null || group.length == 0) return;
        int start = random.nextInt(group.length);
        PetResources.Line chosen = null;
        for (int i = 0; i < group.length; i++) {
            PetResources.Line candidate = group[(start + i) % group.length];
            if (!wasRecent(candidate)) {
                chosen = candidate;
                break;
            }
        }
        nextAllowed[best.ordinal()] = now + best.cooldownMs;
        if (chosen == null && best == PetEvent.SLEEP) chosen = group[start];
        if (chosen == null) return;
        stop();
        queued = chosen;
        queuedEvent = best;
        if (PetFullPose.find(rig, chosen.pose) == null || poses.ready(chosen.pose)) start(now, sounds);
    }

    private void start(long now, boolean sounds) {
        PetResources.Line chosen = queued;
        current = chosen;
        speaking = queuedEvent;
        queued = null;
        queuedEvent = null;
        startedAt = now;
        lastSpoken = now;
        recent[recentIndex] = chosen;
        recentIndex = (recentIndex + 1) % recent.length;
        if (sounds && MeteorClient.mc.getSoundManager().get(chosen.sound) != null) {
            voice = chosen.voice;
            MeteorClient.mc.getSoundManager().play(voice);
        }
    }

    private boolean wasRecent(PetResources.Line candidate) {
        for (PetResources.Line line : recent) {
            if (line != null && (line.sound.equals(candidate.sound) || line.value.equals(candidate.value))) return true;
        }
        return false;
    }

    public void stop() {
        if (voice != null) MeteorClient.mc.getSoundManager().stop(voice);
        voice = null;
        current = null;
        queued = null;
        queuedEvent = null;
        speaking = null;
    }

    public void reset() {
        stop();
        Arrays.fill(pending, 0);
        Arrays.fill(nextAllowed, 0);
        Arrays.fill(recent, null);
        recentIndex = 0;
        lastSpoken = 0;
    }
}
