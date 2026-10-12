package com.hunterbuddy.modules.mixin.accessors;

import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputMovementAccessor {
   @Accessor("movementVector")
   void hunterBuddy$setMovementVector(Vec2f vector);
}
