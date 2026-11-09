package com.hunterbuddy.util;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

public final class AttackCooldown {
    private AttackCooldown() {}

    public static float ticks(EntityAttributeInstance current, ItemStack held, ItemStack weapon) {
        EntityAttributeInstance speed = new EntityAttributeInstance(current.getAttribute(), instance -> {});
        speed.setFrom(current);
        held.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(current.getAttribute())) speed.removeModifier(modifier.id());
        });
        weapon.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(current.getAttribute())) {
                speed.removeModifier(modifier.id());
                speed.addTemporaryModifier(modifier);
            }
        });
        return ticks(speed.getValue());
    }

    static float ticks(double speed) {
        return (float) (20.0 / speed);
    }

    public static float progress(int ticks, float baseTime, float cooldown) {
        return MathHelper.clamp((ticks + baseTime) / cooldown, 0.0f, 1.0f);
    }
}
