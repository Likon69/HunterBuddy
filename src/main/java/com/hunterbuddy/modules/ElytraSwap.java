package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class ElytraSwap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> durabilityThreshold = this.sgGeneral
      .add(
         new Builder()
            .name("Durability Threshold")
            .description("Swap elytra when durability drops below this percentage.")
            .defaultValue(10)
            .min(1)
            .max(100)
            .sliderRange(1, 100)
            .build()
      );
   private final Setting<Boolean> onlyWhileFlying = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("Only While Flying")
                  .description("Only swap elytras while actively flying.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> pauseInInventory = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("Pause In Inventory")
                  .description("Don't swap while inventory is open to prevent desync.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Integer> swapCooldown = this.sgGeneral
      .add(
         new Builder().name("Swap Cooldown").description("Ticks to wait after swapping before checking again.")
               .defaultValue(100)
            .min(20)
            .max(200)
            .sliderRange(20, 200)
            .build()
      );
   private final Setting<Integer> stageDelay = this.sgGeneral
      .add(
         new Builder().name("Stage Delay")
                  .description("Ticks to wait between swap stages. Higher values are safer for anticheat.")
               .defaultValue(8)
            .min(3)
            .max(20)
            .sliderRange(3, 20)
            .build()
      );
   private final Setting<Boolean> notifySwap = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("Notify Swap")
                  .description("Send a chat message when swapping elytras.")
               .defaultValue(true)
            .build()
      );
   private final SettingGroup sgCombat = this.settings.createGroup("Combat Protection");
   private final Setting<Boolean> swapOnHit = this.sgCombat
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("Swap On Hit")
                  .description("Automatically swap elytra to chestplate when hit by an entity.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Integer> hitProtectionDuration = this.sgCombat
      .add(
         new Builder().name("Protection Duration").description("Ticks to keep chestplate equipped after being hit.")
                  .defaultValue(60)
               .min(20)
               .max(200)
               .sliderRange(20, 200)
               .visible(this.swapOnHit::get)
            .build()
      );
   private final Setting<Boolean> autoSwapBack = this.sgCombat
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("Auto Swap Back")
                     .description("Automatically swap back to elytra after protection duration.")
                  .defaultValue(true)
               .visible(this.swapOnHit::get)
            .build()
      );
   private final Setting<Boolean> prioritizeNetherite = this.sgCombat
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("Prioritize Netherite")
                     .description("Prioritize netherite chestplates over diamond.")
                  .defaultValue(true)
               .visible(this.swapOnHit::get)
            .build()
      );
   private int cooldownTimer = 0;
   private boolean needsSwap = false;
   private int swapStage = 0;
   private int stageTimer = 0;
   private int targetSlot = -1;
   private boolean protectionActive = false;
   private int protectionTimer = 0;
   private int lastHurtTime = 0;
   private boolean needsChestplateSwap = false;
   private int chestplateSwapStage = 0;
   private int chestplateStageTimer = 0;
   private int chestplateSwapFailures = 0;
   private int chestplateSlot = -1;
   private ItemStack storedElytra = ItemStack.EMPTY;

   public ElytraSwap() {
      super(HunterBuddyAddon.LOGISTICS_CATEGORY, "ElytraSwap", "Automatically swaps elytras when they reach low durability.");
   }

   public void onActivate() {
      this.resetSwapState();
   }

   public void onDeactivate() {
      this.resetSwapState();
   }

   private void resetSwapState() {
      this.cooldownTimer = 0;
      this.needsSwap = false;
      this.swapStage = 0;
      this.stageTimer = 0;
      this.targetSlot = -1;
      this.protectionActive = false;
      this.protectionTimer = 0;
      this.lastHurtTime = 0;
      this.needsChestplateSwap = false;
      this.chestplateSwapStage = 0;
      this.chestplateStageTimer = 0;
      this.chestplateSwapFailures = 0;
      this.chestplateSlot = -1;
      this.storedElytra = ItemStack.EMPTY;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if ((Boolean)this.swapOnHit.get()) {
            this.handleCombatProtection();
         }

         if (this.cooldownTimer > 0) {
            this.cooldownTimer--;
         } else if ((Boolean)this.pauseInInventory.get() && this.mc.player.currentScreenHandler != this.mc.player.playerScreenHandler) {
            this.resetSwapState();
         } else {
            ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (!this.protectionActive) {
               if (chestItem.getItem().equals(Items.ELYTRA)) {
                  if (!(Boolean)this.onlyWhileFlying.get() || this.mc.player.isGliding()) {
                     if (this.needsSwap) {
                        this.processSwapStages();
                     } else {
                        double currentPercent = this.getDurabilityPercent(chestItem);
                        if (currentPercent <= (Integer)this.durabilityThreshold.get()) {
                           this.initiateSwap(chestItem);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void initiateSwap(ItemStack equipped) {
      int bestSlot = -1;
      double bestPercent = this.getDurabilityPercent(equipped);
      double minPercent = (Integer)this.durabilityThreshold.get();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (!this.isElytra(stack)) {
            continue;
         }

         double percent = this.getDurabilityPercent(stack);
         if (percent > bestPercent && percent > minPercent) {
            bestPercent = percent;
            bestSlot = i;
         }
      }

      if (bestSlot != -1) {
         this.targetSlot = bestSlot;
         this.needsSwap = true;
         this.swapStage = 1;
         this.stageTimer = 0;
      } else if ((Boolean)this.notifySwap.get()) {
         this.warning(
            "No better elytra found (equipped: %.1f%%, threshold: %.0f%%)",
            this.getDurabilityPercent(equipped),
            minPercent
         );
         this.cooldownTimer = (Integer)this.swapCooldown.get();
      }
   }

   /**
    * Two stages. The first is one three-click move of the fresh elytra straight into the chest
    * slot, the same call the regear uses to swap its chestplate in: the fresh one goes on and the
    * worn one lands in the very slot the fresh one came from. Nothing passes through the hotbar,
    * so nothing there is displaced. The old way parked the fresh elytra in the first hotbar slot
    * without a totem or an apple in it, right-clicked it, and only then tried to sort the hotbar
    * out again; any interruption in between (a hit, a container opening, a toggle) left the worn
    * elytra sitting where the sword had been and the sword buried in the inventory. The second
    * stage only reads back what the chest slot holds.
    */
   private void processSwapStages() {
      this.stageTimer++;
      if (this.stageTimer >= (Integer)this.stageDelay.get()) {
         switch (this.swapStage) {
            case 1:
               ItemStack toEquip = this.mc.player.getInventory().getStack(this.targetSlot);
               if (!this.isElytra(toEquip)) {
                  if ((Boolean)this.notifySwap.get()) {
                     this.warning("Elytra swap failed - the fresh elytra is no longer in slot %d", this.targetSlot);
                  }

                  this.resetSwapState();
                  return;
               }

               InvUtils.move().from(this.targetSlot).toArmor(2);
               this.swapStage = 2;
               this.stageTimer = 0;
               break;
            case 2:
               ItemStack newChest = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
               if ((Boolean)this.notifySwap.get()) {
                  if (this.isElytra(newChest) && this.getDurabilityPercent(newChest) > (Integer)this.durabilityThreshold.get()) {
                     this.info(
                        "Swapped to elytra at %.1f%% durability, worn one left in slot %d",
                        this.getDurabilityPercent(newChest),
                        this.targetSlot
                     );
                  } else {
                     this.warning("Elytra swap failed - the chest slot did not take the fresh elytra", new Object[0]);
                  }
               }

               this.finishSwap();
         }
      }
   }

   private void finishSwap() {
      this.needsSwap = false;
      this.swapStage = 0;
      this.stageTimer = 0;
      this.targetSlot = -1;
      this.cooldownTimer = (Integer)this.swapCooldown.get();
   }

   private boolean isEssentialItem(ItemStack stack) {
      return stack.getItem().equals(Items.TOTEM_OF_UNDYING)
         || stack.getItem().equals(Items.GOLDEN_APPLE)
         || stack.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE)
         || stack.getItem().equals(Items.ENDER_PEARL)
         || stack.getItem().equals(Items.CHORUS_FRUIT);
   }

   private void handleCombatProtection() {
      if (this.mc.player != null) {
         if (this.mc.player.hurtTime > 0 && this.mc.player.hurtTime > this.lastHurtTime) {
            this.lastHurtTime = this.mc.player.hurtTime;
            ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestItem.getItem().equals(Items.ELYTRA) && !this.protectionActive) {
               int bestChestplate = this.findBestChestplate();
               if (bestChestplate != -1) {
                  this.storedElytra = chestItem.copy();
                  this.chestplateSlot = bestChestplate;
                  this.needsChestplateSwap = true;
                  this.chestplateSwapStage = 1;
                  this.chestplateStageTimer = 0;
                  this.chestplateSwapFailures = 0;
                  this.protectionActive = true;
                  this.protectionTimer = (Integer)this.hitProtectionDuration.get();
                  if ((Boolean)this.notifySwap.get()) {
                     this.info("Swapping to chestplate for protection!", new Object[0]);
                  }
               }
            } else if (this.protectionActive) {
               this.protectionTimer = (Integer)this.hitProtectionDuration.get();
            }
         }

         if (this.mc.player.hurtTime < this.lastHurtTime) {
            this.lastHurtTime = this.mc.player.hurtTime;
         }

         if (this.needsChestplateSwap) {
            this.processChestplateSwap();
         } else {
            if (this.protectionActive && !this.needsChestplateSwap) {
               this.protectionTimer--;
               if (this.protectionTimer <= 0 && (Boolean)this.autoSwapBack.get()) {
                  ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
                  if (!chestItem.getItem().equals(Items.ELYTRA) && !this.storedElytra.isEmpty()) {
                     int elytraSlot = this.findStoredElytra();
                     if (elytraSlot != -1) {
                        this.chestplateSlot = elytraSlot;
                        this.needsChestplateSwap = true;
                        this.chestplateSwapStage = 1;
                        this.chestplateStageTimer = 0;
                        this.chestplateSwapFailures = 0;
                        if ((Boolean)this.notifySwap.get()) {
                           this.info("Protection period ended, swapping back to elytra.", new Object[0]);
                        }
                     } else {
                        this.protectionActive = false;
                        this.storedElytra = ItemStack.EMPTY;
                     }
                  } else {
                     this.protectionActive = false;
                     this.storedElytra = ItemStack.EMPTY;
                  }
               }
            }
         }
      }
   }

   private void processChestplateSwap() {
      this.chestplateStageTimer++;
      if (this.chestplateStageTimer >= (Integer)this.stageDelay.get()) {
         switch (this.chestplateSwapStage) {
            case 1:
               if (this.chestplateSlot >= 9) {
                  int hotbarSlot = 0;

                  for (int i = 0; i < 9; i++) {
                     ItemStack stack = this.mc.player.getInventory().getStack(i);
                     if (stack.isEmpty() || !this.isEssentialItem(stack)) {
                        hotbarSlot = i;
                        break;
                     }
                  }

                  InvUtils.move().from(this.chestplateSlot).toHotbar(hotbarSlot);
                  this.chestplateSlot = hotbarSlot;
               }

               this.chestplateSwapStage = 2;
               this.chestplateStageTimer = 0;
               break;
            case 2:
               ItemStack toEquip = this.mc.player.getInventory().getStack(this.chestplateSlot);
               if (!this.isChestplateItem(toEquip)) {
                  this.needsChestplateSwap = false;
                  this.chestplateSwapStage = 0;
                  this.chestplateSwapFailures++;
                  if ((Boolean)this.notifySwap.get()) {
                     this.warning("Chestplate swap failed - item not in hotbar slot (attempt %d)", this.chestplateSwapFailures);
                  }
                  // Whatever moved the item out from under this (another module's own hotbar
                  // shuffling, a desync) leaves protectionActive stuck true forever with nothing
                  // retrying it, unless something forces the issue -- handleCombatProtection
                  // re-enters this same attempt every tick since protectionTimer is already at or
                  // past zero, so a few failures in a row means retrying is not the fix. Giving up
                  // and dropping protection is: the player keeps whatever is currently equipped
                  // rather than being stuck mid-swap indefinitely.
                  if (this.chestplateSwapFailures >= 5) {
                     this.protectionActive = false;
                     this.storedElytra = ItemStack.EMPTY;
                     this.chestplateSwapFailures = 0;
                     if ((Boolean)this.notifySwap.get()) {
                        this.warning("Giving up on the chestplate swap after %d failed attempts.", 5);
                     }
                  }
                  return;
               }

               InvUtils.swap(this.chestplateSlot, true);
               this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
               this.mc.player.swingHand(Hand.MAIN_HAND);
               InvUtils.swapBack();
               this.chestplateSwapStage = 3;
               this.chestplateStageTimer = 0;
               break;
            case 3:
               this.needsChestplateSwap = false;
               this.chestplateSwapStage = 0;
               this.chestplateStageTimer = 0;
               this.chestplateSwapFailures = 0;
               this.chestplateSlot = -1;
               ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
               if (chestItem.getItem().equals(Items.ELYTRA)) {
                  this.protectionActive = false;
                  this.storedElytra = ItemStack.EMPTY;
               }
         }
      }
   }

   private int findBestChestplate() {
      int bestSlot = -1;
      int bestValue = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         int value = this.getChestplateValue(stack);
         if (value > bestValue) {
            bestValue = value;
            bestSlot = i;
         }
      }

      return bestSlot;
   }

   private int getChestplateValue(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0;
      } else if (stack.getItem().equals(Items.NETHERITE_CHESTPLATE)) {
         return this.prioritizeNetherite.get() ? 1000 + (stack.getMaxDamage() - stack.getDamage()) : 400 + (stack.getMaxDamage() - stack.getDamage());
      } else if (stack.getItem().equals(Items.DIAMOND_CHESTPLATE)) {
         return 300 + (stack.getMaxDamage() - stack.getDamage());
      } else if (stack.getItem().equals(Items.IRON_CHESTPLATE)) {
         return 200 + (stack.getMaxDamage() - stack.getDamage());
      } else if (stack.getItem().equals(Items.GOLDEN_CHESTPLATE)) {
         return 100 + (stack.getMaxDamage() - stack.getDamage());
      } else if (stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE)) {
         return 150 + (stack.getMaxDamage() - stack.getDamage());
      } else {
         return stack.getItem().equals(Items.LEATHER_CHESTPLATE) ? 50 + (stack.getMaxDamage() - stack.getDamage()) : 0;
      }
   }

   private boolean isChestplateItem(ItemStack stack) {
      return stack.isEmpty()
         ? false
         : stack.getItem().equals(Items.ELYTRA)
            || stack.getItem().equals(Items.NETHERITE_CHESTPLATE)
            || stack.getItem().equals(Items.DIAMOND_CHESTPLATE)
            || stack.getItem().equals(Items.IRON_CHESTPLATE)
            || stack.getItem().equals(Items.GOLDEN_CHESTPLATE)
            || stack.getItem().equals(Items.CHAINMAIL_CHESTPLATE)
            || stack.getItem().equals(Items.LEATHER_CHESTPLATE);
   }

   private boolean isElytra(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem().equals(Items.ELYTRA);
   }

   private double getDurabilityPercent(ItemStack stack) {
      if (stack.isEmpty() || !stack.isDamageable()) {
         return 100.0;
      }

      int maxDamage = stack.getMaxDamage();
      if (maxDamage <= 0) {
         return 100.0;
      }

      return (double)(maxDamage - stack.getDamage()) / maxDamage * 100.0;
   }

   private int findStoredElytra() {
      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem().equals(Items.ELYTRA) && Math.abs(stack.getDamage() - this.storedElytra.getDamage()) <= 5) {
            return i;
         }
      }

      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem().equals(Items.ELYTRA)) {
            return i;
         }
      }

      return -1;
   }
}
