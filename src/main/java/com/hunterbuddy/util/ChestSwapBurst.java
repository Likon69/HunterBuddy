package com.hunterbuddy.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class ChestSwapBurst {
   private ChestSwapBurst() {
   }

   /**
    * The swap itself, with the arm kept still. Equipping from the hand comes
    * back as a success the client answers by swinging, and at one burst every
    * few ticks that is an arm waving several times a second for an exchange
    * that is over inside the same tick. The packet the server reads is
    * untouched; only the animation is dropped.
    */
   public static void swapSilently(Hand hand) {
      net.minecraft.client.MinecraftClient mc = meteordevelopment.meteorclient.MeteorClient.mc;
      if (mc.player == null || mc.interactionManager == null) {
         return;
      }

      mc.interactionManager.interactItem(mc.player, hand);
      mc.player.handSwinging = false;
      mc.player.handSwingTicks = 0;
      mc.player.handSwingProgress = 0.0F;
      mc.player.lastHandSwingProgress = 0.0F;
   }

   public static boolean isGlider(ItemStack stack) {
      return !stack.isEmpty() && LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST);
   }

   public static boolean isChestPiece(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !isGlider(stack);
   }

   public static Hand gliderHand(ClientPlayerEntity player) {
      if (isGlider(player.getStackInHand(Hand.MAIN_HAND))) {
         return Hand.MAIN_HAND;
      }

      return isGlider(player.getStackInHand(Hand.OFF_HAND)) ? Hand.OFF_HAND : null;
   }

   public static Hand chestPieceHand(ClientPlayerEntity player) {
      if (isChestPiece(player.getStackInHand(Hand.MAIN_HAND))) {
         return Hand.MAIN_HAND;
      }

      return isChestPiece(player.getStackInHand(Hand.OFF_HAND)) ? Hand.OFF_HAND : null;
   }

   public static String pairProblem(ClientPlayerEntity player) {
      ItemStack worn = player.getEquippedStack(EquipmentSlot.CHEST);
      Hand hand = gliderHand(player);
      if (!isChestPiece(worn) || hand == null) {
         return "chest swap needs a glider and a chest piece, one worn and the other in a hand";
      }

      if (EnchantmentHelper.hasAnyEnchantmentsWith(worn, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE) && !player.isCreative()) {
         return "chest piece has Curse of Binding";
      }

      return player.getStackInHand(hand).willBreakNextUse() ? "elytra is spent" : null;
   }
}
