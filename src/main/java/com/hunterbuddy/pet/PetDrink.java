package com.hunterbuddy.pet;

public final class PetDrink {
    public boolean available;
    public float headAngle;
    public float armAngle;

    public void solve(PetResources.Rig rig) {
        available = false;
        PetResources.Part head = null;
        PetResources.Part arm = null;
        for (PetResources.Part part : rig.parts()) {
            if (part.index() == 0) head = part;
            if (part.index() == 3) arm = part;
        }
        if (head == null || arm == null) return;
        float mx = rig.anchors()[4] - head.pivotX();
        float my = rig.anchors()[5] - head.pivotY();
        float bx = rig.anchors()[2] - arm.pivotX();
        float by = rig.anchors()[3] - arm.pivotY();
        float dx = arm.pivotX() - head.pivotX();
        float dy = arm.pivotY() - head.pivotY();
        float distance = (float) Math.hypot(dx, dy);
        float mouthRadius = (float) Math.hypot(mx, my);
        float bottleRadius = (float) Math.hypot(bx, by);
        if (distance < 0.001f || mouthRadius < 0.001f || bottleRadius < 0.001f) return;
        float along = (mouthRadius * mouthRadius - bottleRadius * bottleRadius + distance * distance) / (2 * distance);
        float heightSquared = mouthRadius * mouthRadius - along * along;
        if (heightSquared < 0) return;
        float height = (float) Math.sqrt(heightSquared);
        for (int sign = -1; sign <= 1; sign += 2) {
            float x = along * dx / distance - sign * height * dy / distance;
            float y = along * dy / distance + sign * height * dx / distance;
            float ha = wrap((float) Math.atan2(y, x) - (float) Math.atan2(my, mx));
            float aa = wrap((float) Math.atan2(y - dy, x - dx) - (float) Math.atan2(by, bx));
            if (ha > -0.25f || ha < -1.5f || aa > -0.4f || aa < -2.2f) continue;
            headAngle = ha;
            armAngle = aa;
            available = true;
            return;
        }
    }

    private static float wrap(float angle) {
        while (angle > Math.PI) angle -= (float) (Math.PI * 2);
        while (angle < -Math.PI) angle += (float) (Math.PI * 2);
        return angle;
    }
}
