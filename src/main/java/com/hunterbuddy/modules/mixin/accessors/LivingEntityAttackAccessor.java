package com.hunterbuddy.modules.mixin.accessors;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAttackAccessor {
    @Accessor("ticksSinceLastAttack")
    int getTicksSinceLastAttack();
}
