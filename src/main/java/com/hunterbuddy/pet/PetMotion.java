package com.hunterbuddy.pet;

import java.util.Random;

public final class PetMotion {
    public enum Travel { GROUNDED, JUMP, CLIMB, FALL }
    private final Random random = new Random();
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
    public boolean elevated;
    public int surfaceKind = -1;
    public boolean roomToLie;

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
    private float climbAngle;

    public void reset() {
        initialized = false;
        direction = 1;
        travel = Travel.FALL;
        velocity = 0;
        decision = 0;
        landing = 0;
        clock = 0;
    }

    public void update(PetBrain brain, float dt, PetSurfaces surfaces, float size, boolean climb, boolean heldPose) {
        width = surfaces.width;
        height = surfaces.height;
        if (width < 1 || height < 1) return;
        if (!initialized) {
            x = Math.min(width * 0.15f, size);
            y = height - size;
            initialized = true;
        }
        previousX = x;
        previousY = y;
        clock += dt;
        if (brain.state == PetState.TURN && brain.age > brain.duration * 0.5f && !turned) {
            direction = -direction;
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
        roomToLie = support >= 0 && x - surfaces.left[support] > size * 0.9f && surfaces.right[support] - x > size * 0.65f;
        if (travel == Travel.GROUNDED && support < 0) {
            travel = Travel.FALL;
            velocity = 0;
        }
        if ((travel == Travel.JUMP || travel == Travel.CLIMB) && surfaces.support(destinationX, destinationY) < 0
            && !surfaces.wall(destinationX, destinationY)) {
            travel = Travel.FALL;
            velocity = 0;
        }
        if (travel == Travel.JUMP || travel == Travel.CLIMB) {
            progress = Math.min(1, progress + dt / journey);
            if (travel == Travel.CLIMB) {
                float approach = Math.min(1, progress * 4);
                float ascent = Math.max(0, (progress - 0.25f) / 0.75f);
                x = originX + (destinationX - originX) * approach;
                y = originY + (destinationY - originY) * ascent;
                if (approach < 1) y -= (float) Math.sin(approach * Math.PI) * size * 0.3f;
            } else {
                x = originX + (destinationX - originX) * progress;
                y = originY + (destinationY - originY) * progress;
            }
            if (travel == Travel.JUMP) y -= (float) Math.sin(progress * Math.PI) * size * 0.8f;
            if (!surfaces.safe(x, y, size)) {
                x = previousX;
                y = previousY;
                travel = Travel.FALL;
                velocity = 0;
            } else if (progress == 1) {
                if (surfaces.wall(destinationX, destinationY) && surfaces.support(x, y) < 0) {
                    travel = Travel.FALL;
                    velocity = 0;
                } else land();
            }
        } else if (travel == Travel.FALL) {
            velocity += size * 5 * dt;
            y = Math.min(y, height);
            float nextY = y + velocity * dt;
            x = Math.max(0, Math.min(width, x));
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
            if (brain.state == PetState.SIT && elevated && !atEdge && !heldPose) {
                float target = x - surfaces.left[support] < surfaces.right[support] - x ? surfaces.left[support] + 2 : surfaces.right[support] - 2;
                if (surfaces.left[support] < size * 0.4f) target = surfaces.right[support] - 2;
                if (surfaces.right[support] > width - size * 0.4f) target = surfaces.left[support] + 2;
                direction = target >= x ? 1 : -1;
                x += Math.copySign(Math.min(Math.abs(target - x), dt * size * 0.5f), target - x);
            }
            boolean walk = brain.state == PetState.WALK || brain.state == PetState.HIDE || brain.state == PetState.STAGGER;
            if (!heldPose && (walk || !surfaces.safe(x, y, size))) {
                float speed = size * (brain.state == PetState.HIDE ? 1.4f : 0.4f);
                float next = x + direction * dt * speed * (1 + brain.drunkenness * (float) Math.sin(clock * 5) * 0.4f);
                if (support >= 0 && next >= surfaces.left[support] && next <= surfaces.right[support] && surfaces.safe(next, y, size)) x = next;
                else if (decision < 2) direction = -direction;
            }
            boolean hotbar = support >= 0 && surfaces.kind[support] == 1;
            if (brain.state != PetState.SIT && !heldPose && decision > (hotbar ? 2 : 4) && (walk || hotbar)) {
                decision = 0;
                choose(surfaces, size, climb, support);
            }
        }
    }

    public float wallAngle() {
        return travel == Travel.CLIMB && progress >= 0.25f ? climbAngle : 0;
    }

    private void choose(PetSurfaces surfaces, float size, boolean climb, int support) {
        if (surfaces.count == 0) return;
        if (climb && (x < size || x > width - size) && random.nextInt(4) == 0) {
            originX = x;
            originY = y;
            destinationX = x < width * 0.5f ? 0 : width;
            climbAngle = destinationX == 0 ? 1.5707963f : -1.5707963f;
            destinationY = Math.max(size, y - size * (1 + random.nextFloat() * 2));
            progress = 0;
            travel = Travel.CLIMB;
            journey = 2 + random.nextFloat() * 2;
            return;
        }
        int start = random.nextInt(surfaces.count);
        for (int n = 0; n < surfaces.count; n++) {
            int i = (start + n) % surfaces.count;
            if (i == support || surfaces.right[i] <= surfaces.left[i]) continue;
            float tx = Math.max(surfaces.left[i], Math.min(surfaces.right[i], x));
            if (climb && y - surfaces.top[i] > size * 0.8f) {
                tx = Math.abs(x - surfaces.left[i]) < Math.abs(x - surfaces.right[i]) ? surfaces.left[i] : surfaces.right[i];
            }
            if (Math.abs(tx - x) < 1) tx = surfaces.left[i] + (surfaces.right[i] - surfaces.left[i]) * 0.2f;
            if (!surfaces.safe(tx, surfaces.top[i], size) || surfaces.top[i] < size * 0.7f) continue;
            float distance = Math.abs(tx - x);
            float rise = y - surfaces.top[i];
            if (rise > size * 1.4f && !climb) continue;
            if (distance > size * 4 && rise > 0) continue;
            climbAngle = tx == 0 ? 1.5707963f : tx == width ? -1.5707963f : tx == surfaces.left[i] ? -1.5707963f : 1.5707963f;
            originX = x;
            originY = y;
            destinationX = tx;
            destinationY = surfaces.top[i];
            direction = tx >= x ? 1 : -1;
            progress = 0;
            travel = climb && rise > size * 0.8f ? Travel.CLIMB : Travel.JUMP;
            journey = travel == Travel.CLIMB ? Math.max(0.8f, rise / (size * 0.5f)) : Math.max(0.6f, distance / (size * 2));
            return;
        }
        direction = -direction;
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
