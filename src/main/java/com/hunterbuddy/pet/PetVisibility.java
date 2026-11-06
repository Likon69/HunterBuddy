package com.hunterbuddy.pet;

public final class PetVisibility {
    private PetVisibility() {
    }

    public static void keepEyesVisible(PetMotion motion, float eyeX, float eyeY, float size) {
        float margin = Math.max(2, size * 0.12f);
        float horizontal = Math.min(margin, motion.width * 0.25f);
        float vertical = Math.min(margin, motion.height * 0.25f);
        float dx = Math.max(horizontal, Math.min(motion.width - horizontal, eyeX)) - eyeX;
        float dy = Math.max(vertical, Math.min(motion.height - vertical, eyeY)) - eyeY;
        motion.x += dx;
        motion.y += dy;
        motion.previousX += dx;
        motion.previousY += dy;
    }
}
