package com.hunterbuddy.pet;

import java.util.Arrays;
import java.util.Random;

public final class PetMotion {
    public enum Travel { GROUNDED, JUMP, FALL }
    private final Random random;
    public float x;
    public float y;
    public float previousX;
    public float previousY;
    public int direction = 1;
    public int width = 1;
    public int height = 1;
    public Travel travel = Travel.FALL;
    public float landing;
    public boolean highEdge;
    public boolean atEdge;
    public int edgeDirection;
    public boolean seatAvailable;
    public boolean seatReachable;
    public boolean seeking;
    public boolean elevated;
    public int surfaceKind = -1;
    public boolean roomToLie;
    public boolean hiding;
    public float hideTop;

    private float velocity;
    private float destinationX;
    private float destinationY;
    private float originX;
    private float originY;
    private float progress;
    private float journey;
    private float decision;
    private float clock;
    private boolean initialized;
    private boolean turned;
    private boolean emerging;
    private boolean wandering;
    private boolean perching;
    private boolean seatMission;
    private float seekTimer;
    private float aim;
    private float lastFlip = -10;
    private float lastHop = -100;
    private int seatSide;
    private int lastSeatSide;
    private final float[] visited = new float[3];

    public PetMotion() {
        random = new Random();
    }

    public PetMotion(long seed) {
        random = new Random(seed);
    }

    public void reset() {
        initialized = false;
        direction = 1;
        travel = Travel.FALL;
        velocity = 0;
        decision = 0;
        landing = 0;
        clock = 0;
        hiding = false;
        emerging = false;
        wandering = false;
        perching = false;
        seatMission = false;
        seeking = false;
        lastFlip = -10;
        lastHop = -100;
        seatSide = 0;
        lastSeatSide = 0;
        Arrays.fill(visited, 0);
    }

    public void update(PetBrain brain, float dt, PetSurfaces surfaces, float size, float bodyWidth, boolean heldPose) {
        width = surfaces.width;
        height = surfaces.height;
        if (width < 1 || height < 1) return;
        if (!initialized) {
            x = width * 0.5f;
            y = height - size;
            initialized = true;
        }
        float half = Math.min(bodyWidth * 0.5f, width * 0.5f);
        previousX = x;
        previousY = y;
        clock += dt;
        visited[Math.min(2, Math.max(0, (int) (x * 3 / width)))] = clock;
        if (brain.state == PetState.TURN && brain.age > brain.duration * 0.5f && !turned) {
            direction = -direction;
            lastFlip = clock;
            turned = true;
        }
        if (brain.state != PetState.TURN) turned = false;
        landing = Math.max(0, landing - dt * 3);
        int support = surfaces.support(x, y);
        surfaceKind = support >= 0 ? surfaces.kind[support] : -1;
        elevated = support >= 0 && surfaces.kind[support] != 0;
        highEdge = elevated && Math.min(x - surfaces.left[support], surfaces.right[support] - x) < size * 0.35f;
        seatAvailable = elevated && (surfaces.left[support] >= size * 0.4f || surfaces.right[support] <= width - size * 0.4f);
        atEdge = elevated && (surfaces.left[support] >= size * 0.4f && x - surfaces.left[support] <= 3
            || surfaces.right[support] <= width - size * 0.4f && surfaces.right[support] - x <= 3);
        edgeDirection = atEdge && x - surfaces.left[support] <= 3 ? -1 : 1;
        seatReachable = elevated ? seatAvailable : reachableSeat(surfaces, size);
        roomToLie = support >= 0 && x - surfaces.left[support] > size * 0.9f && surfaces.right[support] - x > size * 0.65f;
        boolean sinking = brain.state == PetState.HIDE && glideToHotbar(surfaces, dt, size, half);
        if (sinking) emerging = true;
        hiding = sinking || emerging && emerge(surfaces, dt);
        if (hiding) return;
        if (travel == Travel.GROUNDED && support < 0) {
            travel = Travel.FALL;
            velocity = 0;
        }
        if (travel == Travel.JUMP && surfaces.support(destinationX, destinationY) < 0) {
            travel = Travel.FALL;
            velocity = 0;
        }
        if (travel == Travel.JUMP) {
            progress = Math.min(1, progress + dt / journey);
            x = originX + (destinationX - originX) * progress;
            y = originY + (destinationY - originY) * progress - (float) Math.sin(progress * Math.PI) * size * 0.8f;
            if (!surfaces.safe(x, y, size)) {
                x = previousX;
                y = previousY;
                travel = Travel.FALL;
                velocity = 0;
            } else if (progress == 1) land();
        } else if (travel == Travel.FALL) {
            velocity += size * 5 * dt;
            y = Math.min(y, height);
            float nextY = y + velocity * dt;
            x = Math.max(half, Math.min(width - half, x));
            int below = surfaces.below(x, y, nextY);
            if (below >= 0) {
                y = surfaces.top[below];
                land();
            } else {
                y = Math.min(height, nextY);
                if (nextY >= height) recover(surfaces, size);
            }
        } else {
            decision += dt;
            x = Math.max(half, Math.min(width - half, x));
            boolean seat = brain.state == PetState.SIT && !elevated && !heldPose;
            if (!seat) {
                seatMission = false;
                seekTimer = 0;
            } else if (!seatMission && seekTimer == 0) seatMission = startSeat(surfaces, size);
            if (seat) seekTimer += dt;
            if (seekTimer > 25) seatMission = false;
            seeking = seat && seatMission;
            if (brain.state != PetState.SIT) seatSide = 0;
            else if (elevated && !heldPose) {
                if (seatSide == 0) seatSide = atEdge ? edgeDirection : pickSeatSide(surfaces, support, size);
                lastSeatSide = seatSide;
                if (!atEdge) {
                    float target = seatSide < 0 ? surfaces.left[support] + 2 : surfaces.right[support] - 2;
                    face(target >= x ? 1 : -1);
                    x += Math.copySign(Math.min(Math.abs(target - x), dt * size * 0.5f), target - x);
                }
            }
            if (brain.idle && !roomToLie && !heldPose && support >= 0 && surfaceKind != 1) {
                float middle = (surfaces.left[support] + surfaces.right[support]) * 0.5f;
                face(middle >= x ? 1 : -1);
                x += Math.copySign(Math.min(Math.abs(middle - x), dt * size * 0.5f), middle - x);
            }
            boolean wander = (brain.state == PetState.WALK || seeking) && !heldPose;
            boolean walk = wander || brain.state == PetState.HIDE || brain.state == PetState.STAGGER;
            if (wander && !wandering && !seeking) startWander(surfaces, size, half);
            if (wander) {
                if (perching && wandering && Math.abs(aim - x) <= size * 2.5f) {
                    perching = false;
                    if (!hop(brain, surfaces, size, half, support, aim, !seeking)) {
                        seatMission = false;
                        wandering = false;
                    }
                }
                if (Math.abs(aim - x) < 2) wandering = false;
                else face(aim >= x ? 1 : -1);
            }
            if (travel != Travel.JUMP && !heldPose && (walk || !surfaces.safe(x, y, size)) && !(wander && !wandering)) {
                float speed = size * (brain.state == PetState.HIDE ? 1.4f : 0.5f);
                float next = x + direction * dt * speed * (1 + brain.drunkenness * (float) Math.sin(clock * 5) * 0.4f);
                if (support >= 0 && next >= Math.max(half, surfaces.left[support]) && next <= Math.min(width - half, surfaces.right[support]) && surfaces.safe(next, y, size)) x = next;
                else if (wander) {
                    if (!hop(brain, surfaces, size, half, support, aim, !seeking)) reroute(surfaces, support, half);
                } else if (decision < 2) face(-direction);
            }
            if (travel != Travel.JUMP && !heldPose && decision > 4 && (brain.idle ? elevated : brain.state == PetState.STAGGER)) {
                if (!hop(brain, surfaces, size, half, support, x, false)) face(-direction);
            }
        }
    }

    private boolean glideToHotbar(PetSurfaces surfaces, float dt, float size, float half) {
        int hotbar = surfaces.hotbarSurface();
        if (hotbar < 0) return false;
        float blend = 1 - (float) Math.exp(-dt * 7);
        float low = Math.min(surfaces.left[hotbar] + half, (surfaces.left[hotbar] + surfaces.right[hotbar]) * 0.5f);
        float high = Math.max(surfaces.right[hotbar] - half, (surfaces.left[hotbar] + surfaces.right[hotbar]) * 0.5f);
        float targetX = Math.max(low, Math.min(high, x));
        face(targetX >= x ? 1 : -1);
        x += (targetX - x) * blend;
        y += (surfaces.top[hotbar] + size * 1.3f - y) * blend;
        hideTop = surfaces.top[hotbar];
        travel = Travel.GROUNDED;
        velocity = 0;
        return true;
    }

    private boolean emerge(PetSurfaces surfaces, float dt) {
        int hotbar = surfaces.hotbarSurface();
        if (hotbar < 0) {
            emerging = false;
            return false;
        }
        float top = surfaces.top[hotbar];
        y += (top - y) * (1 - (float) Math.exp(-dt * 7));
        hideTop = top;
        travel = Travel.GROUNDED;
        velocity = 0;
        if (Math.abs(top - y) < 1) {
            y = top;
            emerging = false;
            return false;
        }
        return true;
    }

    private boolean hop(PetBrain brain, PetSurfaces surfaces, float size, float half, int support, float goal, boolean strict) {
        int best = -1;
        float bestX = 0;
        float bestScore = Float.POSITIVE_INFINITY;
        float reach = size * 4;
        for (int i = 0; i < surfaces.count; i++) {
            if (i == support || surfaces.right[i] <= surfaces.left[i] || brain.idle && surfaces.kind[i] != 0) continue;
            float tx = Math.max(surfaces.left[i], Math.min(surfaces.right[i], Math.max(half, Math.min(width - half, goal))));
            if (strict) tx = Math.max(Math.max(x - reach, surfaces.left[i]), Math.min(Math.min(x + reach, surfaces.right[i]), tx));
            else if (Math.abs(tx - x) < 1) tx = surfaces.left[i] + (surfaces.right[i] - surfaces.left[i]) * 0.2f;
            tx = Math.max(half, Math.min(width - half, tx));
            if (tx < surfaces.left[i] || tx > surfaces.right[i]) continue;
            if (!surfaces.safe(tx, surfaces.top[i], size) || surfaces.top[i] < size * 0.7f) continue;
            float rise = y - surfaces.top[i];
            if (rise > size * 1.4f || Math.abs(tx - x) > reach && rise > 0) continue;
            float score = Math.abs(tx - goal);
            if (strict && score >= Math.abs(x - goal) - 1 || score >= bestScore) continue;
            best = i;
            bestX = tx;
            bestScore = score;
        }
        if (best < 0) return false;
        decision = 0;
        lastHop = clock;
        originX = x;
        originY = y;
        destinationX = bestX;
        destinationY = surfaces.top[best];
        if (bestX >= x != direction > 0) lastFlip = clock;
        direction = bestX >= x ? 1 : -1;
        progress = 0;
        travel = Travel.JUMP;
        journey = Math.max(0.6f, Math.abs(bestX - x) / (size * 2));
        return true;
    }

    private void face(int wanted) {
        if (wanted == direction || clock - lastFlip < 0.6f) return;
        direction = wanted;
        lastFlip = clock;
    }

    private void reroute(PetSurfaces surfaces, int support, float half) {
        perching = false;
        wandering = false;
        if (support < 0) return;
        float low = Math.max(half, surfaces.left[support]);
        float high = Math.min(width - half, surfaces.right[support]);
        boolean toRight = high - x >= x - low;
        float room = toRight ? high - x : x - low;
        if (room < 4) return;
        aim = x + (toRight ? room : -room) * (0.4f + random.nextFloat() * 0.5f);
        wandering = true;
    }

    private void startWander(PetSurfaces surfaces, float size, float half) {
        int third = 0;
        for (int i = 1; i < visited.length; i++) if (visited[i] < visited[third]) third = i;
        float far = width * 0.25f;
        int support = surfaces.support(x, y);
        float low = Math.max(half, width * third / 3f);
        float high = Math.min(width - half, width * (third + 1) / 3f);
        float goal;
        if (clock - visited[third] > 40 || support < 0) goal = low + random.nextFloat() * Math.max(0, high - low);
        else {
            low = Math.max(half, surfaces.left[support]);
            high = Math.min(width - half, surfaces.right[support]);
            float reach = Math.min(far, (high - low) * 0.4f);
            boolean toRight = high - x >= x - low ? random.nextFloat() < 0.7f : random.nextFloat() < 0.3f;
            float from = toRight ? Math.min(high, x + reach) : low;
            float to = toRight ? high : Math.max(low, x - reach);
            goal = from + random.nextFloat() * Math.max(0, to - from);
            aim = goal;
            wandering = true;
            perching = false;
            return;
        }
        if (Math.abs(goal - x) < far) {
            boolean toRight = x < width * 0.5f;
            float from = toRight ? Math.min(width - half, x + far) : half;
            float to = toRight ? width - half : Math.max(half, x - far);
            goal = from + random.nextFloat() * Math.max(0, to - from);
        }
        aim = goal;
        wandering = true;
        perching = false;
    }

    private boolean reachableSeat(PetSurfaces surfaces, float size) {
        for (int i = 0; i < surfaces.count; i++) if (seatable(surfaces, i, size)) return true;
        return false;
    }

    private boolean seatable(PetSurfaces surfaces, int i, float size) {
        return surfaces.kind[i] != 0 && surfaces.right[i] > surfaces.left[i] && y - surfaces.top[i] <= size * 1.4f
            && surfaces.top[i] >= size * 0.7f && (surfaces.left[i] >= size * 0.4f || surfaces.right[i] <= width - size * 0.4f);
    }

    private boolean startSeat(PetSurfaces surfaces, float size) {
        int best = -1;
        float nearest = Float.POSITIVE_INFINITY;
        for (int i = 0; i < surfaces.count; i++) {
            if (!seatable(surfaces, i, size)) continue;
            float distance = Math.max(0, Math.max(surfaces.left[i] - x, x - surfaces.right[i]));
            if (distance < nearest) {
                nearest = distance;
                best = i;
            }
        }
        if (best < 0) return false;
        aim = Math.max(surfaces.left[best], Math.min(surfaces.right[best], x));
        wandering = true;
        perching = true;
        return true;
    }

    private int pickSeatSide(PetSurfaces surfaces, int support, float size) {
        boolean left = surfaces.left[support] >= size * 0.4f;
        boolean right = surfaces.right[support] <= width - size * 0.4f;
        if (left && right) return lastSeatSide == 0 ? (x - surfaces.left[support] < surfaces.right[support] - x ? -1 : 1) : -lastSeatSide;
        return left ? -1 : 1;
    }

    private void recover(PetSurfaces surfaces, float size) {
        int best = -1;
        float nearest = Float.POSITIVE_INFINITY;
        for (int i = 0; i < surfaces.count; i++) {
            if (surfaces.right[i] <= surfaces.left[i]) continue;
            float tx = Math.max(surfaces.left[i], Math.min(surfaces.right[i], x));
            if (!surfaces.safe(tx, surfaces.top[i], size)) continue;
            float distance = Math.abs(tx - x) + Math.abs(surfaces.top[i] - y);
            if (distance < nearest) {
                best = i;
                nearest = distance;
                destinationX = tx;
                destinationY = surfaces.top[i];
            }
        }
        velocity = 0;
        if (best < 0) return;
        originX = x;
        originY = y;
        progress = 0;
        journey = Math.max(0.6f, nearest / (size * 2));
        travel = Travel.JUMP;
    }

    private void land() {
        travel = Travel.GROUNDED;
        velocity = 0;
        landing = 1;
    }
}
