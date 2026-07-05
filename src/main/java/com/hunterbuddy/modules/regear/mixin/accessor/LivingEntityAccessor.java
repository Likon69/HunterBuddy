package com.hunterbuddy.modules.regear.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.entity.LivingEntity;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
   @Invoker("getJumpVelocity")
   float invokeGetJumpVelocity();
}
