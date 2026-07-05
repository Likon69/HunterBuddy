package com.hunterbuddy.modules.regear.util;

import com.hunterbuddy.modules.regear.accessor.InputAccessor;
import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.modules.mixin.accessors.UpdateSelectedSlotS2CPacketAccessor;
import java.util.Arrays;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

public class InventoryManager {
   private static InventoryManager INSTANCE;
   private int serverSlot = -1;
   private boolean sendingPacket = false;
   private boolean isEating = false;
   private long lastSetbackTime = -1L;
   private final int[] transactions = new int[4];
   private int transactionIndex = 0;
   private boolean isGrim = false;
   private int currentPriority = 0;

   private InventoryManager() {
      MeteorClient.EVENT_BUS.subscribe(this);
      Arrays.fill(this.transactions, -1);
   }

   public static InventoryManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new InventoryManager();
      }

      return INSTANCE;
   }

   @EventHandler
   public void onPacketSend(Send event) {
      if (!this.sendingPacket) {
         if (event.packet instanceof UpdateSelectedSlotC2SPacket packet) {
            int packetSlot = packet.getSelectedSlot();
            if (!PlayerInventory.isValidHotbarIndex(packetSlot) || this.serverSlot == packetSlot) {
               event.cancel();
               return;
            }

            this.serverSlot = packetSlot;
         }
      }
   }

   @EventHandler(priority = 200)
   public void onPacketReceive(Receive event) {
      if (event.packet instanceof UpdateSelectedSlotS2CPacket packet) {
         this.serverSlot = ((UpdateSelectedSlotS2CPacketAccessor)(Object) packet).getSlot();
      } else if (event.packet instanceof CommonPingS2CPacket packet) {
         if (this.transactionIndex > 3) {
            return;
         }

         int uid = packet.getParameter();
         this.transactions[this.transactionIndex] = uid;
         this.transactionIndex++;
         if (this.transactionIndex == 4) {
            this.grimCheck();
         }
      } else if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.lastSetbackTime = System.currentTimeMillis();
      }
   }

   @EventHandler
   public void onTick(Post event) {
      if (MeteorClient.mc.player != null && this.serverSlot == -1) {
         this.serverSlot = ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot();
      }

      if (!this.isEating && this.currentPriority > 0) {
         this.currentPriority = 0;
      }
   }

   @EventHandler
   public void onDisconnect(GameLeftEvent event) {
      Arrays.fill(this.transactions, -1);
      this.transactionIndex = 0;
      this.isGrim = false;
      this.lastSetbackTime = -1L;
      this.serverSlot = -1;
      this.currentPriority = 0;
      this.isEating = false;
   }

   private void grimCheck() {
      for (int i = 0; i < 4; i++) {
         if (this.transactions[i] != -i) {
            return;
         }
      }

      this.isGrim = true;
   }

   public boolean isGrim() {
      return this.isGrim;
   }

   public boolean hasPassed(long timeMS) {
      return this.lastSetbackTime != -1L && System.currentTimeMillis() - this.lastSetbackTime >= timeMS;
   }

   public void setSlot(int barSlot) {
      this.setSlot(barSlot, 0);
   }

   public void setSlot(int barSlot, boolean highPriority) {
      this.setSlot(barSlot, highPriority ? 20 : 0);
   }

   public void setSlot(int barSlot, int priority) {
      if (MeteorClient.mc.player != null && MeteorClient.mc.getNetworkHandler() != null) {
         if (priority >= this.currentPriority) {
            if (this.serverSlot == -1) {
               this.serverSlot = ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot();
            }

            if (this.serverSlot != barSlot && PlayerInventory.isValidHotbarIndex(barSlot)) {
               this.setSlotForced(barSlot);
               this.currentPriority = priority;
            }
         }
      }
   }

   public void setClientSlot(int barSlot) {
      this.setClientSlot(barSlot, 0);
   }

   public void setClientSlot(int barSlot, int priority) {
      if (MeteorClient.mc.player != null) {
         if (priority >= this.currentPriority) {
            if (((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot() != barSlot && PlayerInventory.isValidHotbarIndex(barSlot)) {
               ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).setSelectedSlot(barSlot);
               this.setSlotForced(barSlot);
               this.currentPriority = priority;
            }
         }
      }
   }

   public void setSlotForced(int barSlot) {
      if (MeteorClient.mc.getNetworkHandler() != null) {
         this.sendingPacket = true;

         try {
            MeteorClient.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(barSlot));
            this.serverSlot = barSlot;
         } finally {
            this.sendingPacket = false;
         }
      }
   }

   public void syncToClient() {
      if (MeteorClient.mc.player != null) {
         if (this.isDesynced()) {
            this.setSlotForced(((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot());
         }
      }
   }

   public boolean isDesynced() {
      return MeteorClient.mc.player == null
         ? false
         : ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot() != this.serverSlot;
   }

   public int getServerSlot() {
      if (MeteorClient.mc.player == null) {
         return -1;
      } else {
         return this.serverSlot == -1 ? ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot() : this.serverSlot;
      }
   }

   public int getClientSlot() {
      return MeteorClient.mc.player == null ? -1 : ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).getSelectedSlot();
   }

   public ItemStack getServerItem() {
      return MeteorClient.mc.player != null && this.getServerSlot() != -1
         ? MeteorClient.mc.player.getInventory().getStack(this.getServerSlot())
         : ItemStack.EMPTY;
   }

   public void setEating(boolean eating) {
      this.isEating = eating;
      if (eating) {
         this.currentPriority = 10;
      } else {
         this.currentPriority = 0;
      }
   }

   public boolean isEating() {
      return this.isEating;
   }

   public int getCurrentPriority() {
      return this.currentPriority;
   }

   public static int getBestWeaponSlot() {
      float bestDamage = 0.0F;
      int bestSlot = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getStack(i);
         float damage = getWeaponDamage(stack);
         if (damage > bestDamage) {
            bestDamage = damage;
            bestSlot = i;
         }
      }

      return bestSlot;
   }

   public static float getWeaponDamage(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0.0F;
      }

      Item item = stack.getItem();
      float baseDamage = 0.0F;
      if (item.toString().toLowerCase().contains("sword")) {
         baseDamage = 4.0F;
      } else if (item instanceof AxeItem axe) {
         baseDamage = 5.0F;
      } else if (item instanceof TridentItem) {
         baseDamage = 8.0F;
      } else {
         if (!(item instanceof MaceItem)) {
            return 0.0F;
         }

         baseDamage = 5.0F;
      }

      int sharpnessLevel = meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.SHARPNESS);
      float sharpnessDamage = sharpnessLevel * 0.5F + 0.5F;
      return baseDamage + sharpnessDamage;
   }

   public static int getBestBreachMaceSlot() {
      int bestSlot = -1;
      int bestBreachLevel = 0;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getStack(i);
         if (stack.getItem() instanceof MaceItem) {
            int breachLevel = meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.BREACH);
            if (breachLevel > bestBreachLevel) {
               bestBreachLevel = breachLevel;
               bestSlot = i;
            }
         }
      }

      return bestSlot;
   }

   public static boolean isHoldingWeapon() {
      ItemStack mainHand = MeteorClient.mc.player.getMainHandStack();
      Item item = mainHand.getItem();
      return item.toString().toLowerCase().contains("sword") || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem;
   }

   public static boolean isHoldingWeaponType(Class<? extends Item> weaponType) {
      return weaponType.isInstance(MeteorClient.mc.player.getMainHandStack().getItem());
   }

   public static ItemStack getCurrentWeapon() {
      ItemStack mainHand = MeteorClient.mc.player.getMainHandStack();
      return isHoldingWeapon() ? mainHand : ItemStack.EMPTY;
   }

   public static void swapToSlot(int slot) {
      if (slot >= 0 && slot < 9) {
         ((PlayerInventoryAccessor)MeteorClient.mc.player.getInventory()).setSelectedSlot(slot);
      }
   }

   public static double getAttackSpeed(ItemStack weapon) {
      if (weapon.isEmpty()) {
         return 4.0;
      } else {
         Item item = weapon.getItem();
         if (item.toString().toLowerCase().contains("sword")) {
            return 1.6;
         } else if (item instanceof AxeItem) {
            return 0.8;
         } else if (item instanceof TridentItem) {
            return 1.1;
         } else {
            return item instanceof MaceItem ? 0.6 : 4.0;
         }
      }
   }

   public static int getAttackCooldownTicks(ItemStack weapon) {
      double attackSpeed = getAttackSpeed(weapon);
      return (int)Math.ceil(20.0 / attackSpeed);
   }

   public static boolean isHolding32k() {
      if (MeteorClient.mc.player == null) {
         return false;
      }

      ItemStack mainHand = MeteorClient.mc.player.getMainHandStack();
      ItemStack offHand = MeteorClient.mc.player.getOffHandStack();
      return is32kWeapon(mainHand) || is32kWeapon(offHand);
   }

   private static boolean is32kWeapon(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      Item item = stack.getItem();
      boolean isWeaponOrTool = item.toString().toLowerCase().contains("sword")
         || item.toString().toLowerCase().contains("pickaxe")
         || item.toString().toLowerCase().contains("axe")
         || item.toString().toLowerCase().contains("shovel");
      return !isWeaponOrTool
         ? false
         : meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 1000
            || meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.SMITE) > 1000
            || meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS) > 1000;
   }

   public static boolean isMovingInput() {
      if (MeteorClient.mc.player == null) {
         return false;
      }

      InputAccessor inputAccessor = (InputAccessor)MeteorClient.mc.player.input;
      return inputAccessor.getMovementForward() != 0.0F
         || inputAccessor.getMovementSideways() != 0.0F
         || MeteorClient.mc.options.jumpKey.isPressed()
         || MeteorClient.mc.options.sneakKey.isPressed();
   }

   public interface IPlayerInteractEntityC2SPacket {
      boolean isAttackPacket();

      int getTargetEntityId();
   }

   public static class Priority {
      public static final int NORMAL = 0;
      public static final int TOTEM = 5;
      public static final int EATING = 10;
      public static final int SURROUND = 20;
      public static final int PEARL_PHASE = 30;
   }

   public enum SwapMode {
      Normal,
      Silent;
   }

   public enum VelocityMode {
      NORMAL,
      WALLS,
      GRIM,
      GRIM_V3;
   }
}
