package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.regear.accessor.InputAccessor;
import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Input.class)
public abstract class InputMixin implements InputAccessor {
   @Shadow public Vec2f movementVector;

   @Unique private float hunterBuddy$overrideForward = Float.NaN;
   @Unique private float hunterBuddy$overrideSideways = Float.NaN;

   @Override
   public float getMovementForward() {
      if (!Float.isNaN(this.hunterBuddy$overrideForward)) return this.hunterBuddy$overrideForward;

      return this.movementVector != null ? this.movementVector.y : 0.0F;
   }

   @Override
   public void setMovementForward(float value) {
      this.hunterBuddy$overrideForward = value;
      this.applyOverrides();
   }

   @Override
   public float getMovementSideways() {
      if (!Float.isNaN(this.hunterBuddy$overrideSideways)) return this.hunterBuddy$overrideSideways;

      return this.movementVector != null ? this.movementVector.x : 0.0F;
   }

   @Override
   public void setMovementSideways(float value) {
      this.hunterBuddy$overrideSideways = value;
      this.applyOverrides();
   }

   @Unique
   private void applyOverrides() {
      if (this.movementVector != null) {
         float sideways = Float.isNaN(this.hunterBuddy$overrideSideways) ? this.movementVector.x : this.hunterBuddy$overrideSideways;
         float forward = Float.isNaN(this.hunterBuddy$overrideForward) ? this.movementVector.y : this.hunterBuddy$overrideForward;

         if (!Float.isNaN(this.hunterBuddy$overrideSideways) && !Float.isNaN(this.hunterBuddy$overrideForward)) {
            float length = (float) Math.sqrt(sideways * sideways + forward * forward);
            if (length > 1.0E-4F) {
               sideways /= length;
               forward /= length;
            }
         }

         this.movementVector = new Vec2f(sideways, forward);
         this.hunterBuddy$overrideForward = Float.NaN;
         this.hunterBuddy$overrideSideways = Float.NaN;
      }
   }
}
