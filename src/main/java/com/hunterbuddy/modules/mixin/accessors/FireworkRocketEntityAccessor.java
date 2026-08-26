package com.hunterbuddy.modules.mixin.accessors;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The three things a firework will not tell you about itself.
 *
 * <p>{@code shooter} is what the entity is attached to and pushing -- resolved client side
 * from the tracked shooter id on the first tick -- and life against lifeTime is how much of
 * the push is left. Nothing here is written, only read.
 */
@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
    @Accessor("shooter")
    LivingEntity getShooter();

    @Accessor("life")
    int getLife();

    @Accessor("lifeTime")
    int getLifeTime();
}
