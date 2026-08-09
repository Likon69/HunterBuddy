package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class RocketFly extends Module {
   private long lastRocketUse = 0L;
   private boolean launched = false;
   private double yTarget = -1.0;
   private float targetPitch = 0.0F;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> fireworkDelay = this.sgGeneral
      .add(
         new Builder().name("timed-delay").description("The delay between firework usages in milliseconds.").defaultValue(4000)
            .sliderRange(0, 10000)
            .build()
      );
   private final Setting<Boolean> useManualY = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("use-manual-y-level")
                  .description("Use a manually set Y level instead of the Y level when activated.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Integer> manualYLevel = this.sgGeneral
      .add(
         new Builder().name("manual-y-level")
                        .description("The Y level to maintain when using manual Y level.")
                     .defaultValue(256)
                  .sliderRange(-64, 320)
                  .visible(this.useManualY::get)
               .onChanged(val -> this.yTarget = val.intValue()).build()
      );
   private final Setting<Boolean> takeoffFromGround = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("takeoff-from-ground")
                  .description("Jump, open the elytra and boost into flight when enabled on the ground, instead of requiring you to already be gliding.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> takeoffDebug = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("takeoff-messages")
                  .description("Narrate every phase of the takeoff. The only way to see which step fails when a takeoff does not get you off the ground.")
               .defaultValue(false)
               .visible(this.takeoffFromGround::get)
            .build()
      );

   /**
    * Own instance, deliberately not shared.
    *
    * <p>{@link ElytraTakeoff} carries the whole state of one attempt — phase,
    * tick counters, retry budget. AutoFlyingRegear and ElytraRecast each hold
    * their own for that reason, and this follows them: a shared machine would let
    * two modules cancel each other's takeoff mid-jump.
    */
   private final ElytraTakeoff takeoff = new ElytraTakeoff();

   /**
    * Set when the machine gives up, cleared as soon as flight is regained.
    *
    * <p>Without it a failed takeoff would be retried on the very next tick,
    * forever: five attempts, fail, five more, and a jump key hammered on a player
    * who is evidently stuck under a ceiling or out of rockets.
    */
   private boolean takeoffGaveUp = false;

   /**
    * Ticks the current takeoff has been running, against {@link #TAKEOFF_TIMEOUT_TICKS}.
    *
    * <p>{@link ElytraTakeoff} normally ends by itself — five attempts, then it
    * gives up. One path does not: in the boost phase, a glide that never reaches
    * flight speed fires a rocket every fourteen ticks and resets its own counter
    * each time, so with rockets in the bag it can circle forever. Gliding into a
    * wall or inside a one-block shaft is enough to sit there. The outer ceiling is
    * what stops that from quietly emptying the stack.
    */
   private int takeoffTicks = 0;
   private static final int TAKEOFF_TIMEOUT_TICKS = 280;

   public RocketFly() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "RocketFly", "Maintains a level Y-flight with fireworks and smooth pitch control.");
   }

   public void onActivate() {
      this.launched = false;
      this.yTarget = -1.0;
      this.takeoffGaveUp = false;
      this.takeoff.cancel();
      if (this.mc.player == null) {
         return;
      }

      if (this.mc.player.isGliding()) {
         return;
      }

      if (!(Boolean)this.takeoffFromGround.get()) {
         this.info("You must be flying before enabling RocketFly.", new Object[0]);
      } else if (!this.hasElytraEquipped()) {
         this.error("No elytra equipped!", new Object[0]);
      } else {
         this.startTakeoff();
      }
   }

   public void onDeactivate() {
      this.takeoff.cancel();
      Utils.setPressed(this.mc.options.jumpKey, false);
   }

   private boolean hasElytraEquipped() {
      return this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
   }

   private void startTakeoff() {
      this.takeoffTicks = 0;

      // The same preparation AutoFlyingRegear does before handing over to the
      // machine. Eight degrees down is what lets the elytra bite the moment it
      // opens; from a flat or upward look you stall and drop back onto the block
      // you jumped off. And the boost phase only searches the hotbar, so a stack
      // sitting in the backpack has to be brought forward before the jump rather
      // than discovered missing three phases later.
      this.mc.player.setPitch(8.0F);
      this.ensureRocketsInHotbar();

      this.takeoff.start(-1, new ElytraTakeoff.Listener() {
         @Override
         public void onTakeoffSuccess(double speedBps) {
            // Nothing to hand over: tickFlyLogic sees isGliding on the next tick
            // and takes the Y lock from there, exactly as if the flight had been
            // started by hand.
            RocketFly.this.takeoffGaveUp = false;
            if ((Boolean)RocketFly.this.takeoffDebug.get()) {
               RocketFly.this.info("Takeoff complete at " + String.format("%.1f", speedBps) + " b/s", new Object[0]);
            }
         }

         @Override
         public void onTakeoffFailed(String reason) {
            RocketFly.this.takeoffGaveUp = true;
            RocketFly.this.warning("Takeoff failed: " + reason, new Object[0]);
         }

         @Override
         public void onTakeoffDebug(String message) {
            if ((Boolean)RocketFly.this.takeoffDebug.get()) {
               RocketFly.this.info(message, new Object[0]);
            }
         }
      });
   }

   public void tickFlyLogic() {
      if (this.mc.player != null) {
         double currentY = this.mc.player.getY();
         if (this.mc.player.isGliding()) {
            if (this.yTarget == -1.0 || !this.launched) {
               if ((Boolean)this.useManualY.get()) {
                  this.yTarget = ((Integer)this.manualYLevel.get()).intValue();
               } else {
                  this.yTarget = currentY;
               }

               this.launched = true;
            }

            if (!(Boolean)this.useManualY.get()) {
               double yDiffFromLock = currentY - this.yTarget;
               if (Math.abs(yDiffFromLock) > 10.0) {
                  this.yTarget = currentY;
                  this.info("Y-lock reset due to altitude deviation.", new Object[0]);
               }
            }

            double yDiff = currentY - this.yTarget;
            if (Math.abs(yDiff) > 10.0) {
               this.targetPitch = (float)(Math.atan2(yDiff, 100.0) * (180.0 / Math.PI));
            } else if (yDiff > 2.0) {
               this.targetPitch = 10.0F;
            } else if (yDiff < -2.0) {
               this.targetPitch = -10.0F;
            } else {
               this.targetPitch = 0.0F;
            }

            float currentPitch = this.mc.player.getPitch();
            float pitchDiff = this.targetPitch - currentPitch;
            this.mc.player.setPitch(currentPitch + pitchDiff * 0.1F);
            if (System.currentTimeMillis() - this.lastRocketUse > ((Integer)this.fireworkDelay.get()).intValue()) {
               this.tryUseFirework();
            }
         } else {
            // Not gliding. The old path here jumped once and then fired rockets
            // on the ground — a firework only propels a player who is already
            // gliding, so it burned the stack and went nowhere. The missing step
            // was the deploy between the two: jump, open the elytra in the air,
            // then boost. ElytraTakeoff is that sequence, already written and
            // already relied on by AutoFlyingRegear and ElytraRecast.
            if ((Boolean)this.takeoffFromGround.get() && !this.takeoffGaveUp && this.hasElytraEquipped()) {
               this.startTakeoff();
            } else if (!this.launched) {
               this.mc.player.jump();
               this.launched = true;
            } else if (System.currentTimeMillis() - this.lastRocketUse > 1000L) {
               this.tryUseFirework();
            }

            this.yTarget = -1.0;
         }
      }
   }

   public void resetYLock() {
      this.yTarget = -1.0;
      this.launched = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      // The machine owns the jump key while it runs, so the fly logic must stay
      // out of its way — pitch steering during the jump is what drops you back
      // onto the ground before the elytra ever opens.
      if (this.takeoff.isActive()) {
         this.takeoff.tickPre();
         return;
      }

      // Flight regained by any means clears the give-up latch, so losing it again
      // later gets a fresh set of attempts rather than silence.
      if (this.takeoffGaveUp && this.mc.player != null && this.mc.player.isGliding()) {
         this.takeoffGaveUp = false;
      }

      this.tickFlyLogic();
   }

   @EventHandler
   private void onTickPost(Post event) {
      if (this.mc.player != null && this.mc.world != null && this.takeoff.isActive()) {
         this.takeoff.tickPost();

         if (++this.takeoffTicks > TAKEOFF_TIMEOUT_TICKS) {
            this.takeoff.cancel();
            this.takeoffGaveUp = true;
            this.warning("Takeoff timed out.", new Object[0]);
         }
      }
   }

   private void tryUseFirework() {
      if (!this.ensureRocketsInHotbar()) {
         return;
      }

      Utils.firework(this.mc, false);
      this.lastRocketUse = System.currentTimeMillis();
   }

   /** Brings a firework stack into the hotbar if there is none there yet. */
   private boolean ensureRocketsInHotbar() {
      FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
      if (hotbar.found()) {
         return true;
      }

      FindItemResult inv = InvUtils.find(new Item[]{Items.FIREWORK_ROCKET});
      if (!inv.found()) {
         this.info("No fireworks found in hotbar or inventory.", new Object[0]);
         return false;
      }

      int hotbarSlot = this.findEmptyHotbarSlot();
      if (hotbarSlot == -1) {
         this.info("No empty hotbar slot available to move fireworks.", new Object[0]);
         return false;
      }

      InvUtils.move().from(inv.slot()).to(hotbarSlot);
      return true;
   }

   private int findEmptyHotbarSlot() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }
}
