package com.hunterbuddy.pet;

public final class PetMotion {
    public float x;
    public float y;
    public float previousX;
    public float previousY;
    public int direction = 1;
    public int width = 1;
    public int height = 1;
    private float path;
    private float previousSize;

    public void reset() {
        path = 0;
        direction = 1;
        previousSize = 0;
    }

    public void update(PetBrain brain, float dt, int screenWidth, int screenHeight, float size, float guiScale) {
        width = screenWidth;
        height = screenHeight;
        previousX = x;
        previousY = y;
        float margin = size * 0.42f;
        float bottom = Math.max(size, height - 25 * guiScale - 6);
        float top = Math.min(bottom, size + 8);
        float vertical = Math.max(0, bottom - top);
        float horizontal = Math.max(0, width - margin * 2);
        float total = horizontal + 2 * vertical;
        if (brain.state == PetState.WALK || brain.state == PetState.HIDE) {
            float irregular = 1 + brain.drunkenness * (float) Math.sin(brain.age * 5) * 0.45f;
            path += direction * dt * size * (brain.state == PetState.HIDE ? 2.2f : 0.38f) * irregular;
        }
        if (path < 0) { path = 0; direction = 1; }
        if (path > total) { path = total; direction = -1; }
        if (path < vertical) {
            x = margin;
            y = top + path;
        } else if (path < vertical + horizontal) {
            x = margin + path - vertical;
            y = bottom;
        } else {
            x = width - margin;
            y = bottom - (path - vertical - horizontal);
        }
        if (brain.state == PetState.HIDE) {
            float edge = width * 0.5f + (x < width * 0.5f ? -1 : 1) * (91 * guiScale + margin * 0.35f);
            float blend = 1 - (float) Math.exp(-dt * 7);
            x = previousX + (Math.max(margin, Math.min(width - margin, edge)) - previousX) * blend;
            y = previousY + (height - 3 - previousY) * blend;
            path = vertical + Math.max(0, Math.min(horizontal, x - margin));
        }
        if (previousSize != size || !Float.isFinite(previousX)) {
            previousX = x;
            previousY = y;
        }
        previousSize = size;
    }
}
