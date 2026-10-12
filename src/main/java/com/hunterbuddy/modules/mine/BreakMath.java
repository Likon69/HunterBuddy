package com.hunterbuddy.modules.mine;

import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public final class BreakMath {
    private BreakMath() {
    }

    public static int bestToolSlot(ClientPlayerEntity p, BlockState s) {
        int slot = bestToolSlotOrNone(p, s);
        if (slot == -1) {
            return selected(p);
        }

        return slot;
    }

    public static int bestToolSlotOrNone(ClientPlayerEntity p, BlockState s) {
        int best = -1;
        float bestSpeed = 0.0F;

        for (int i = 0; i < 9; i++) {
            float v = p.getInventory().getStack(i).getMiningSpeedMultiplier(s);
            if (v > bestSpeed) {
                bestSpeed = v;
                best = i;
            }
        }

        return best;
    }

    public static int fasterThanHeld(ClientPlayerEntity p, BlockState s) {
        float bestSpeed = p.getInventory().getStack(selected(p)).getMiningSpeedMultiplier(s);
        int best = -1;

        for (int i = 0; i < 9; i++) {
            float v = p.getInventory().getStack(i).getMiningSpeedMultiplier(s);
            if (v > bestSpeed) {
                bestSpeed = v;
                best = i;
            }
        }

        return best;
    }

    public static boolean harvestable(ClientPlayerEntity p, BlockState s) {
        if (s.isToolRequired()) {
            return p.getInventory().getStack(bestToolSlot(p, s)).isSuitableFor(s);
        }

        return true;
    }

    public static float speed(ClientPlayerEntity p, BlockState s) {
        ItemStack stack = p.getInventory().getStack(bestToolSlot(p, s));
        float f = stack.getMiningSpeedMultiplier(s);

        if (f > 1.0F) {
            int level = enchantLevel(stack, Enchantments.EFFICIENCY);
            if (level > 0 && !stack.isEmpty()) {
                f += level * level + 1;
            }
        }

        if (StatusEffectUtil.hasHaste(p)) {
            f *= 1.0F + (StatusEffectUtil.getHasteAmplifier(p) + 1) * 0.2F;
        }

        StatusEffectInstance fatigue = p.getStatusEffect(StatusEffects.MINING_FATIGUE);
        if (fatigue != null) {
            f *= switch (fatigue.getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
        }

        if (p.isSubmergedIn(FluidTags.WATER) && enchantLevel(p.getEquippedStack(EquipmentSlot.HEAD), Enchantments.AQUA_AFFINITY) <= 0) {
            f /= 5.0F;
        }

        if (!p.isOnGround()) {
            f /= 5.0F;
        }

        return f;
    }

    public static float delta(ClientPlayerEntity p, BlockState s, BlockView world, BlockPos pos) {
        float h = s.getHardness(world, pos);
        if (h == -1.0F) {
            return 0.0F;
        }

        int divisor = harvestable(p, s) ? 30 : 100;
        return speed(p, s) / h / divisor;
    }

    private static int selected(ClientPlayerEntity p) {
        return ((PlayerInventoryAccessor) p.getInventory()).getSelectedSlot();
    }

    private static int enchantLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(key)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }
}
