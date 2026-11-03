package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.regear.accessor.InputAccessor;
import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Input.class)
public abstract class InputMixin implements InputAccessor {
    @Unique
    private static final float NORMALIZE_THRESHOLD = 1.0E-4F;

    @Shadow
    protected Vec2f movementVector;

    @Unique
    private float hunterBuddy$forwardOverride = Float.NaN;
    @Unique
    private float hunterBuddy$sidewaysOverride = Float.NaN;

    @Override
    public float getMovementForward() {
        if (!Float.isNaN(hunterBuddy$forwardOverride)) return hunterBuddy$forwardOverride;
        return movementVector == null ? 0.0F : movementVector.y;
    }

    @Override
    public float getMovementSideways() {
        if (!Float.isNaN(hunterBuddy$sidewaysOverride)) return hunterBuddy$sidewaysOverride;
        return movementVector == null ? 0.0F : movementVector.x;
    }

    @Override
    public void setMovementForward(float value) {
        hunterBuddy$forwardOverride = value;
        hunterBuddy$apply();
    }

    @Override
    public void setMovementSideways(float value) {
        hunterBuddy$sidewaysOverride = value;
        hunterBuddy$apply();
    }

    @Unique
    private void hunterBuddy$apply() {
        if (movementVector == null) return;
        float sideways = Float.isNaN(hunterBuddy$sidewaysOverride) ? movementVector.x : hunterBuddy$sidewaysOverride;
        float forward = Float.isNaN(hunterBuddy$forwardOverride) ? movementVector.y : hunterBuddy$forwardOverride;
        if (hunterBuddy$bothOverridden()) {
            float length = (float) Math.sqrt(sideways * sideways + forward * forward);
            if (length > NORMALIZE_THRESHOLD) {
                sideways /= length;
                forward /= length;
            }
        }
        movementVector = new Vec2f(sideways, forward);
        hunterBuddy$forwardOverride = Float.NaN;
        hunterBuddy$sidewaysOverride = Float.NaN;
    }

    @Unique
    private boolean hunterBuddy$bothOverridden() {
        return !Float.isNaN(hunterBuddy$forwardOverride) && !Float.isNaN(hunterBuddy$sidewaysOverride);
    }
}
