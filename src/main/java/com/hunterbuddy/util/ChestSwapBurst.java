package com.hunterbuddy.util;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class ChestSwapBurst {
   private ChestSwapBurst() {
   }

   public static int useSwap(Hand hand) {
      MinecraftClient mc = MeteorClient.mc;
      if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || !canSwap(hand)) return -1;
      ItemStack stack = mc.player.getStackInHand(hand);
      int sequence;
      try (PendingUpdateManager prediction = mc.world.getPendingUpdateManager().incrementSequence()) {
         sequence = prediction.getSequence();
         mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, sequence, mc.player.getYaw(), mc.player.getPitch()));
      }
      ActionResult result = stack.use(mc.world, mc.player, hand);
      ItemStack held = result instanceof ActionResult.Success success && success.getNewHandStack() != null
         ? success.getNewHandStack() : mc.player.getStackInHand(hand);
      if (held != stack) mc.player.setStackInHand(hand, held);
      return result.isAccepted() ? sequence : -1;
   }

   public static boolean canSwap(Hand hand) {
      MinecraftClient mc = MeteorClient.mc;
      if (mc.player == null || hand == null) return false;
      ItemStack held = mc.player.getStackInHand(hand);
      ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
      EquippableComponent equippable = held.get(DataComponentTypes.EQUIPPABLE);
      return !held.isEmpty() && equippable != null && equippable.slot() == EquipmentSlot.CHEST
         && held.getCount() <= 1
         && !EnchantmentHelper.hasAnyEnchantmentsWith(chest, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)
         && !ItemStack.areItemsAndComponentsEqual(held, chest);
   }

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

   public static int gliderSlot(ClientPlayerEntity player) {
      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (isGlider(stack) && !stack.willBreakNextUse()) {
            return i;
         }
      }

      return -1;
   }

   public static boolean hasGlider(ClientPlayerEntity player) {
      return isGlider(player.getEquippedStack(EquipmentSlot.CHEST)) || gliderHand(player) != null || gliderSlot(player) != -1;
   }

   public static boolean wearGlider(ClientPlayerEntity player) {
      if (isGlider(player.getEquippedStack(EquipmentSlot.CHEST))) {
         return true;
      }

      Hand hand = gliderHand(player);
      if (hand != null && !player.getStackInHand(hand).willBreakNextUse()) {
         swapSilently(hand);
         return true;
      }

      int slot = gliderSlot(player);
      if (slot == -1) {
         return false;
      }

      meteordevelopment.meteorclient.utils.player.InvUtils.move().from(slot).toArmor(2);
      return true;
   }

   public static boolean rocketAttached(ClientPlayerEntity player) {
      net.minecraft.client.MinecraftClient mc = meteordevelopment.meteorclient.MeteorClient.mc;
      if (mc.world == null) {
         return false;
      }

      for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
         if (entity instanceof net.minecraft.entity.projectile.FireworkRocketEntity firework
            && firework.isAlive()
            && ((com.hunterbuddy.modules.mixin.accessors.FireworkRocketEntityAccessor)firework).getShooter() == player) {
            return true;
         }
      }

      return false;
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
