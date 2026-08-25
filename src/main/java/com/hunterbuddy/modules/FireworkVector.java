package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.elytraboost.FireworkBoostTracker;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Steers the firework boost vector.
 *
 * <p>A firework pushes the player along {@code getLookAngle()}, converging on
 * {@code look * 1.7}. The server derives that look vector from the rotation in your
 * movement packets, so aiming it well is the whole game — and it is legitimate:
 * the rocket is real, the boost is real, only the aim is chosen for you.
 *
 * <p>Both the physics rotation and the outgoing packets use the steered pitch (they
 * must agree, otherwise the client desyncs from Grim's prediction and gets set back).
 * Only the <em>camera</em> is decoupled, exactly like ElytraBounce's free-pitch, so
 * you keep looking wherever you want while the boost stays optimal.
 */
public class FireworkVector extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAltitude = this.settings.createGroup("Hold Altitude");

   public enum PitchMode {
      Fixed,
      HoldAltitude
   }

   private final Setting<PitchMode> pitchMode = this.sgGeneral.add(
      new EnumSetting.Builder<PitchMode>()
         .name("pitch-mode")
         .description("Fixed: always aim at the set pitch. Hold Altitude: aim as flat as possible while keeping your Y level (max horizontal speed).")
         .defaultValue(PitchMode.HoldAltitude)
         .build()
   );

   private final Setting<Double> fixedPitch = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("pitch")
         .description("The pitch sent to the server while boosting. 0 = flat, which gives the most horizontal speed.")
         .defaultValue(0.0)
         .sliderRange(-45.0, 45.0)
         .visible(() -> this.pitchMode.get() == PitchMode.Fixed)
         .build()
   );

   private final Setting<Boolean> onlyWhileBoosting = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("only-while-boosting")
         .description("Only steer while one of your fireworks is actually pushing you. Off = steer during the whole glide.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Integer> boostWindow = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("boost-window")
         .description("How long after the last boost tick to still count as boosting.")
         .defaultValue(200)
         .min(50)
         .sliderRange(50, 1000)
         .visible(this.onlyWhileBoosting::get)
         .build()
   );

   private final Setting<Boolean> freeCamera = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("free-camera")
         .description("Decouples the camera pitch from the steered pitch so you can look around while boosting.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Double> maxPitchStep = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("max-pitch-step")
         .description("Maximum pitch change per tick. Keeps the rotation you send smooth instead of snapping.")
         .defaultValue(8.0)
         .min(0.5)
         .sliderRange(1.0, 30.0)
         .build()
   );

   private final Setting<Boolean> useManualY = this.sgAltitude.add(
      new BoolSetting.Builder()
         .name("use-manual-y")
         .description("Hold a manually set Y instead of the Y you had when the module activated.")
         .defaultValue(false)
         .visible(() -> this.pitchMode.get() == PitchMode.HoldAltitude)
         .build()
   );

   private final Setting<Integer> manualY = this.sgAltitude.add(
      new IntSetting.Builder()
         .name("manual-y")
         .description("The Y level to hold.")
         .defaultValue(256)
         .sliderRange(-64, 320)
         .visible(() -> this.pitchMode.get() == PitchMode.HoldAltitude && this.useManualY.get())
         .build()
   );

   private final Setting<Double> maxPitch = this.sgAltitude.add(
      new DoubleSetting.Builder()
         .name("max-pitch")
         .description("How far from flat the controller may aim to correct altitude. Lower = faster but slower to correct.")
         .defaultValue(20.0)
         .min(1.0)
         .sliderRange(5.0, 45.0)
         .visible(() -> this.pitchMode.get() == PitchMode.HoldAltitude)
         .build()
   );

   private final Setting<Double> altitudeGain = this.sgAltitude.add(
      new DoubleSetting.Builder()
         .name("altitude-gain")
         .description("How aggressively altitude error is turned into a target climb rate.")
         .defaultValue(0.05)
         .min(0.005)
         .sliderRange(0.01, 0.2)
         .visible(() -> this.pitchMode.get() == PitchMode.HoldAltitude)
         .build()
   );

   private final Setting<Double> climbGain = this.sgAltitude.add(
      new DoubleSetting.Builder()
         .name("climb-gain")
         .description("How aggressively the climb-rate error is turned into pitch. Raise if it drifts, lower if it oscillates.")
         .defaultValue(40.0)
         .min(1.0)
         .sliderRange(5.0, 120.0)
         .visible(() -> this.pitchMode.get() == PitchMode.HoldAltitude)
         .build()
   );

   private final Setting<Boolean> debug = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("debug")
         .description("Show the steered pitch and altitude error in the action bar.")
         .defaultValue(false)
         .build()
   );

   /** Camera pitch while free-camera is on; the steered pitch drives physics instead. */
   public float cameraPitch;
   private double targetY = Double.NaN;
   private float steeredPitch;
   private boolean steering = false;

   public FireworkVector() {
      super(HunterBuddyAddon.UTILITY_CATEGORY, "firework-vector", "Aims the firework boost vector for you while your camera stays free.");
   }

   @Override
   public void onActivate() {
      FireworkBoostTracker.reset();
      this.steering = false;
      if (this.mc.player != null) {
         this.cameraPitch = this.mc.player.getPitch();
         this.steeredPitch = this.mc.player.getPitch();
         this.targetY = this.useManualY.get() ? this.manualY.get() : this.mc.player.getY();
      } else {
         this.targetY = Double.NaN;
      }
   }

   @Override
   public void onDeactivate() {
      // Hand the camera pitch back to the player so the view does not jump.
      if (this.mc.player != null && this.freeCamera.get() && this.steering) {
         this.mc.player.setPitch(this.cameraPitch);
      }

      this.steering = false;
      FireworkBoostTracker.reset();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player == null) {
         this.steering = false;
         return;
      }

      boolean wasSteering = this.steering;
      this.steering = this.shouldSteer();

      if (!this.steering) {
         if (wasSteering && this.freeCamera.get()) {
            // Give the view back exactly where the player was looking.
            this.mc.player.setPitch(this.cameraPitch);
         }

         // Re-arm the altitude target so resuming a boost holds the current height.
         if (!this.useManualY.get()) {
            this.targetY = this.mc.player.getY();
         }

         this.steeredPitch = this.mc.player.getPitch();
         return;
      }

      if (!wasSteering) {
         // Entering steering: start from where the player is actually looking so the
         // rotation we send moves smoothly instead of snapping.
         this.cameraPitch = this.mc.player.getPitch();
         this.steeredPitch = this.mc.player.getPitch();
      }

      float desired = this.computeDesiredPitch();
      float step = this.maxPitchStep.get().floatValue();
      this.steeredPitch = MathHelper.clamp(
         this.steeredPitch + MathHelper.clamp(desired - this.steeredPitch, -step, step),
         -90.0F,
         90.0F
      );

      // Drives both the client physics and the outgoing movement packets, so the
      // client and Grim keep predicting the same thing.
      this.mc.player.setPitch(this.steeredPitch);

      if (this.debug.get()) {
         this.showDebug();
      }
   }

   private boolean shouldSteer() {
      if (!this.mc.player.isGliding() || this.mc.player.isTouchingWater() || this.mc.player.hasVehicle()) {
         return false;
      }

      return !this.onlyWhileBoosting.get() || FireworkBoostTracker.isBoosting(this.boostWindow.get());
   }

   private float computeDesiredPitch() {
      if (this.pitchMode.get() == PitchMode.Fixed) {
         return this.fixedPitch.get().floatValue();
      }

      double target = this.useManualY.get() ? this.manualY.get() : this.targetY;
      if (Double.isNaN(target)) {
         target = this.mc.player.getY();
         this.targetY = target;
      }

      double limit = this.maxPitch.get();

      // Cascade controller: altitude error sets a target climb rate, the climb-rate
      // error sets the pitch. Negative pitch aims up in Minecraft.
      double error = target - this.mc.player.getY();
      double targetClimb = MathHelper.clamp(error * this.altitudeGain.get(), -1.0, 1.0);
      double climbError = targetClimb - this.mc.player.getVelocity().y;

      return (float)MathHelper.clamp(-climbError * this.climbGain.get(), -limit, limit);
   }

   private void showDebug() {
      Vec3d velocity = this.mc.player.getVelocity();
      double bps = velocity.multiply(1.0, 0.0, 1.0).length() * 20.0;
      String msg;

      if (this.pitchMode.get() == PitchMode.Fixed) {
         msg = String.format("§b§l[FV] §r§bpitch %.1f°§7 | %.0f bps", this.steeredPitch, bps);
      } else {
         double target = this.useManualY.get() ? this.manualY.get() : this.targetY;
         double error = target - this.mc.player.getY();
         String col = Math.abs(error) < 3.0 ? "§a" : "§e";
         msg = String.format(
            "§b§l[FV] §r§bpitch %.1f°§7 | %.0f bps%n%sY %.0f§7/%.0f (%+.1f) | vy %+.2f",
            this.steeredPitch, bps, col, this.mc.player.getY(), target, error, velocity.y
         );
      }

      this.mc.player.sendMessage(net.minecraft.text.Text.literal(msg), true);
   }

   /** True while the camera pitch is decoupled from the steered pitch. */
   public boolean isFreeCameraActive() {
      return this.isActive() && this.steering && this.freeCamera.get();
   }
}
