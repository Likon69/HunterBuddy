package com.hunterbuddy.modules;

import com.hunterbuddy.util.QuietClicks;
import com.hunterbuddy.util.Rockets;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.util.GlideWindow;
import com.hunterbuddy.util.GliderSwap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayDeque;
import java.util.Locale;

public class Fly300km extends Module {
   public enum Mode {
      LEGS,
      LEVEL
   }

   public enum BoostMode {
      WINDOW,
      ROCKET_BOOST
   }

   private static final int TAKEOFF_TIMEOUT_TICKS = 280;
   private static final int OBSTACLE_MARGIN = 8;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSpeed = this.settings.createGroup("Speed");
   private final SettingGroup sgBoost = this.settings.createGroup("Rocket Boost");

   private final Setting<Mode> mode = this.sgGeneral.add(new EnumSetting.Builder<Mode>()
      .name("mode").description("LEGS alternates dive and climb legs, LEVEL holds the altitude smoothly.")
      .defaultValue(Mode.LEGS).build());
   private final Setting<Boolean> useCurrentY = this.sgGeneral.add(new BoolSetting.Builder()
      .name("use-current-y").description("Centre the band on the Y level at activation.")
      .defaultValue(true).build());
   private final Setting<Integer> targetY = this.sgGeneral.add(new IntSetting.Builder()
      .name("target-y").description("Centre of the altitude band.")
      .defaultValue(120).sliderRange(-64, 320).visible(() -> !this.useCurrentY.get()).build());
   private final Setting<Integer> band = this.sgGeneral.add(new IntSetting.Builder()
      .name("band").description("Half height of the altitude band.")
      .defaultValue(12).min(2).max(30).sliderRange(2, 30).build());
   private final Setting<Double> divePitch = this.sgGeneral.add(new DoubleSetting.Builder()
      .name("dive-pitch").description("Degrees downwards during the dive with rocket-boost, and the steepest dive when gliding without rockets.")
      .defaultValue(20.0).min(0.0).max(45.0).sliderRange(0.0, 45.0).build());
   private final Setting<Double> climbPitch = this.sgGeneral.add(new DoubleSetting.Builder()
      .name("climb-pitch").description("Degrees upwards during the climb with rocket-boost, and the steepest climb when gliding without rockets.")
      .defaultValue(20.0).min(0.0).max(45.0).sliderRange(0.0, 45.0).build());
   private final Setting<Double> pitchRate = this.sgGeneral.add(new DoubleSetting.Builder()
      .name("pitch-rate").description("Maximum pitch change per tick.")
      .defaultValue(4.0).min(0.5).max(20.0).sliderRange(0.5, 20.0).build());
   private final Setting<Boolean> lockYaw = this.sgGeneral.add(new BoolSetting.Builder()
      .name("lock-yaw").description("Keep the yaw taken at activation, otherwise follow the player yaw.")
      .defaultValue(true).build());
   private final Setting<Integer> refireLead = this.sgGeneral.add(new IntSetting.Builder()
      .name("refire-lead").description("Milliseconds of lead before the end of the boost to light the next rocket.")
      .defaultValue(250).min(0).max(600).sliderRange(0, 600).build());
   private final Setting<Integer> minRockets = this.sgGeneral.add(new IntSetting.Builder()
      .name("min-rockets").description("Below this total stock no rocket is lit any more.")
      .defaultValue(16).min(0).sliderRange(0, 128).build());
   private final Setting<Boolean> obstacleLookahead = this.sgGeneral.add(new BoolSetting.Builder()
      .name("obstacle-lookahead").description("Raise the band above terrain ahead.")
      .defaultValue(true).build());
   private final Setting<Boolean> takeoffFromGround = this.sgGeneral.add(new BoolSetting.Builder()
      .name("takeoff-from-ground").description("Take off when enabled on the ground.")
      .defaultValue(true).build());
   private final Setting<Boolean> log = this.sgGeneral.add(new BoolSetting.Builder()
      .name("log").description("One log line per second.")
      .defaultValue(true).build());

   private final Setting<BoostMode> boostMode = this.sgSpeed.add(new EnumSetting.Builder<BoostMode>()
      .name("boost-mode").description("WINDOW sets each gliding tick with a rocket attached to the fastest point of the movement window Grim accepts, keeping your heading exactly. ROCKET_BOOST uses the rocket-boost module instead.")
      .defaultValue(BoostMode.WINDOW).build());
   private final Setting<Double> windowSize = this.sgSpeed.add(new DoubleSetting.Builder()
      .name("window-size").description("Per-axis size of the firework window, in blocks per tick. Grim uses 1.7; lower it if you get set back.")
      .defaultValue(1.68).min(1.5).max(1.7).sliderRange(1.5, 1.7).visible(() -> this.boostMode.get() == BoostMode.WINDOW).build());
   private final Setting<Double> pitchTolerance = this.sgSpeed.add(new DoubleSetting.Builder()
      .name("pitch-tolerance").description("Most degrees the flown slope may differ from where you look.")
      .defaultValue(15.0).min(0.0).max(45.0).sliderRange(0.0, 45.0).visible(() -> this.boostMode.get() == BoostMode.WINDOW).build());
   private final Setting<Double> speedCap = this.sgSpeed.add(new DoubleSetting.Builder()
      .name("speed-cap").description("Highest speed in blocks per tick (1 b/t is 72 km/h).")
      .defaultValue(9.5).min(1.0).max(12.0).sliderRange(1.0, 12.0).visible(() -> this.boostMode.get() == BoostMode.WINDOW).build());
   private final Setting<Integer> brakeLookahead = this.sgSpeed.add(new IntSetting.Builder()
      .name("brake-lookahead").description("The speed is capped so terrain and unloaded chunks stay at least this many ticks ahead.")
      .defaultValue(10).min(2).max(40).sliderRange(2, 40).visible(() -> this.boostMode.get() == BoostMode.WINDOW).build());
   private final Setting<Double> windowDivePitch = this.sgSpeed.add(new DoubleSetting.Builder()
      .name("window-dive-pitch").description("Degrees downwards during the dive in WINDOW mode.")
      .defaultValue(28.0).min(0.0).max(45.0).sliderRange(0.0, 45.0).visible(() -> this.boostMode.get() == BoostMode.WINDOW).build());
   private final Setting<Double> windowClimbPitch = this.sgSpeed.add(new DoubleSetting.Builder()
      .name("window-climb-pitch").description("Degrees upwards during the climb in WINDOW mode.")
      .defaultValue(66.0).min(0.0).max(80.0).sliderRange(0.0, 80.0).visible(() -> this.boostMode.get() == BoostMode.WINDOW).build());

   private final Setting<Boolean> logTicks = this.sgSpeed.add(new BoolSetting.Builder()
      .name("log-ticks").description("One log line per gliding tick with the window, the brake limits and the result, for diagnosis.")
      .defaultValue(false).visible(() -> this.boostMode.get() == BoostMode.WINDOW).build());

   private final Setting<Boolean> useRocketBoost =this.sgBoost.add(new BoolSetting.Builder()
      .name("use-rocket-boost").description("Enable RocketBoost on activation if it is off.")
      .defaultValue(true).visible(() -> this.boostMode.get() == BoostMode.ROCKET_BOOST).build());
   private final Setting<Boolean> autoSpeed = this.sgBoost.add(new BoolSetting.Builder()
      .name("auto-speed").description("Turn on rocket-boost's auto-speed while 300kmfly flies, and put it back after. Off leaves rocket-boost exactly as you set it.")
      .defaultValue(true).visible(() -> this.boostMode.get() == BoostMode.ROCKET_BOOST && this.useRocketBoost.get()).build());

   private final ElytraTakeoff takeoff = new ElytraTakeoff();
   private boolean takeoffGaveUp;
   private int takeoffTicks;

   private float lockedYaw;
   private double center;
   private double obstacleCenter;
   private int obstacleHold;
   private boolean diving;
   private boolean centerSet;
   private int tickCount;
   private int lastFireTick;
   private boolean lowStockWarned;
   private boolean boostEnabledByUs;
   private boolean rocketSeenSinceFire;
   private int legLock;
   private boolean autoSpeedSetByUs;
   private boolean rocketBoostPausedByUs;
   private Vec3d windowLastPos;
   private Vec3d windowLastLook;
   private float windowLastPitch;
   private volatile boolean windowResync;
   private int windowRidden;
   private int windowGlideTicks;
   private String windowReason = "none";
   private boolean tookOff;
   private final ArrayDeque<Integer> fireTicks = new ArrayDeque<>();

   public Fly300km() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "300kmfly",
         "Fast firework flight on a fixed heading: keeps a rocket lit at all times and holds an altitude band with alternating dive and climb legs, going fast either by following the movement window Grim accepts (WINDOW) or with rocket-boost.");
   }

   @Override
   public void onActivate() {
      this.takeoff.cancel();
      this.takeoffGaveUp = false;
      this.centerSet = false;
      this.obstacleCenter = -1.0;
      this.obstacleHold = 0;
      this.diving = true;
      this.tickCount = 0;
      this.lastFireTick = -1000;
      this.lowStockWarned = false;
      this.boostEnabledByUs = false;
      this.autoSpeedSetByUs = false;
      this.tookOff = false;
      this.rocketSeenSinceFire = false;
      this.legLock = 0;
      this.rocketBoostPausedByUs = false;
      this.resetWindow();
      this.fireTicks.clear();
      if (this.mc.player == null) {
         return;
      }

      this.lockedYaw = this.mc.player.getYaw();

      if (this.boostMode.get() == BoostMode.ROCKET_BOOST) {
         if (this.useRocketBoost.get()) {
            RocketBoost boost = Modules.get().get(RocketBoost.class);
            if (boost != null && !boost.isActive()) {
               boost.toggle();
               this.boostEnabledByUs = true;
            }

            if (boost != null && this.autoSpeed.get() && !boost.autoSpeed.get()) {
               boost.autoSpeed.set(true);
               this.autoSpeedSetByUs = true;
            }
         }
      } else {
         RocketBoost boost = Modules.get().get(RocketBoost.class);
         if (boost != null && boost.isActive()) {
            boost.toggle();
            this.rocketBoostPausedByUs = true;
         }
      }

      if (this.mc.player.isGliding()) {
         return;
      }

      if (!this.takeoffFromGround.get()) {
         this.info("You must be flying before enabling 300kmfly.");
      } else if (!this.hasElytraEquipped()) {
         this.error("No elytra equipped!");
      } else {
         this.startTakeoff();
      }
   }

   @Override
   public void onDeactivate() {
      this.takeoff.cancel();
      if (this.mc.options != null) {
         Utils.setPressed(this.mc.options.jumpKey, false);
         Utils.setPressed(this.mc.options.forwardKey, false);
      }

      RocketBoost boost = Modules.get().get(RocketBoost.class);
      if (this.autoSpeedSetByUs && boost != null) {
         boost.autoSpeed.set(false);
      }

      if (this.boostEnabledByUs && boost != null && boost.isActive()) {
         boost.toggle();
      }

      if (this.rocketBoostPausedByUs && boost != null && !boost.isActive()) {
         boost.toggle();
      }

      this.rocketBoostPausedByUs = false;
      this.autoSpeedSetByUs = false;
      this.boostEnabledByUs = false;
      this.resetWindow();
   }

   private void resetWindow() {
      this.windowLastPos = null;
      this.windowLastLook = null;
      this.windowLastPitch = 0.0F;
      this.windowResync = false;
      this.windowRidden = 0;
      this.windowGlideTicks = 0;
      this.windowReason = "none";
   }

   private double activeDive() {
      return this.boostMode.get() == BoostMode.WINDOW ? this.windowDivePitch.get() : this.divePitch.get();
   }

   private double activeClimb() {
      return this.boostMode.get() == BoostMode.WINDOW ? this.windowClimbPitch.get() : this.climbPitch.get();
   }

   @Override
   public String getInfoString() {
      if (this.mc.player == null) {
         return null;
      }

      return Math.round(this.speedBps() * 3.6) + "";
   }

   private double speedBps() {
      return this.mc.player.getVelocity().length() * 20.0;
   }

   private boolean hasElytraEquipped() {
      return this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
   }

   private void startTakeoff() {
      this.takeoffTicks = 0;
      this.tookOff = true;
      this.mc.player.setPitch(-75.0F);
      this.ensureRocketsInHotbar();
      this.takeoff.start(-1, new ElytraTakeoff.Listener() {
         @Override
         public void onTakeoffSuccess(double speedBps) {
            Fly300km.this.takeoffGaveUp = false;
         }

         @Override
         public void onTakeoffFailed(String reason) {
            Fly300km.this.takeoffGaveUp = true;
            Fly300km.this.warning("Takeoff failed: " + reason);
         }

         @Override
         public void onTakeoffDebug(String message) {
         }
      });
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player == null || this.mc.world == null) {
         return;
      }

      if (this.takeoff.isActive()) {
         if (!this.mc.player.isGliding()) {
            this.mc.player.setPitch(-75.0F);
         }

         this.takeoff.tickPre();
         return;
      }

      if (this.takeoffGaveUp && this.mc.player.isGliding()) {
         this.takeoffGaveUp = false;
      }

      if (!this.mc.player.isGliding()) {
         this.windowLastPos = null;
         this.windowLastLook = null;
         this.centerSet = false;
         if (this.takeoffFromGround.get() && !this.takeoffGaveUp && this.hasElytraEquipped()) {
            this.startTakeoff();
         }

         return;
      }

      this.tickCount++;
      this.fly();
   }

   @EventHandler
   private void onTickPost(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.world != null && this.takeoff.isActive()) {
         this.takeoff.tickPost();
         if (++this.takeoffTicks > TAKEOFF_TIMEOUT_TICKS) {
            this.takeoff.cancel();
            this.takeoffGaveUp = true;
            this.warning("Takeoff timed out.");
         }
      }
   }

   private void fly() {
      double y = this.mc.player.getY();
      if (!this.centerSet) {
         this.center = this.useCurrentY.get() ? y : this.targetY.get();
         if (this.tookOff && !this.mc.world.getDimension().hasCeiling()) {
            int top = this.mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int)Math.floor(this.mc.player.getX()), (int)Math.floor(this.mc.player.getZ()));
            this.center = Math.max(this.center, top + OBSTACLE_MARGIN + this.band.get());
         }

         this.centerSet = true;
         this.diving = false;
         this.obstacleCenter = -1.0;
         this.obstacleHold = 0;
      }

      if (this.obstacleLookahead.get() && !this.mc.world.getDimension().hasCeiling()) {
         if (this.tickCount % 5 == 0) {
            this.scanAhead();
         }
      } else {
         this.obstacleCenter = -1.0;
         this.obstacleHold = 0;
      }

      double effCenter = this.obstacleCenter > this.center ? this.obstacleCenter : this.center;
      double halfBand = this.band.get();
      float target;
      String leg;
      if (this.noRockets()) {
         double raw = (y - effCenter) * 1.5;
         target = (float)MathHelper.clamp(raw, -this.climbPitch.get(), this.divePitch.get());
         leg = "GLIDE";
      } else if (this.mode.get() == Mode.LEGS) {
         if (this.legLock > 0) {
            this.legLock--;
         } else {
            boolean flip = false;
            if (this.diving && y <= effCenter - halfBand) {
               flip = true;
            } else if (!this.diving && y >= effCenter + halfBand) {
               flip = true;
            } else if (this.legBlocked()) {
               flip = true;
            }

            if (flip) {
               this.diving = !this.diving;
               this.legLock = 10;
            }
         }

         target = this.diving ? (float)this.activeDive() : -(float)this.activeClimb();
         leg = this.diving ? "DIVE" : "CLIMB";
      } else {
         double raw = (y - effCenter) * 1.5;
         target = (float)MathHelper.clamp(raw, -this.activeClimb(), this.activeDive());
         leg = "LEVEL";
      }

      float rate = this.pitchRate.get().floatValue();
      float pitch = this.mc.player.getPitch();
      float next = pitch + MathHelper.clamp(target - pitch, -rate, rate);
      this.mc.player.setPitch(next);
      if (this.lockYaw.get()) {
         this.mc.player.setYaw(this.lockedYaw);
      }

      this.refire();

      if (this.log.get() && this.tickCount % 20 == 0) {
         double bps = this.speedBps();
         while (!this.fireTicks.isEmpty() && this.tickCount - this.fireTicks.peekFirst() > 1200) {
            this.fireTicks.pollFirst();
         }

         boolean window = this.boostMode.get() == BoostMode.WINDOW;
         HunterBuddyAddon.LOG.info(String.format(Locale.ROOT, "[300kmfly] %.1f b/s %.0f km/h y=%.1f pitch=%.1f leg=%s rockets/min=%d mode=%s%s",
            bps, bps * 3.6, y, next, leg, this.fireTicks.size(), this.boostMode.get(),
            window ? String.format(Locale.ROOT, " window=%d/%d reason=%s", this.windowRidden, this.windowGlideTicks, this.windowReason) : ""));
         this.windowRidden = 0;
         this.windowGlideTicks = 0;
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.mc.player == null) {
         return;
      }

      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.windowResync = true;
         this.logEvent("PlayerPositionLookS2CPacket");
      } else if (event.packet instanceof EntityVelocityUpdateS2CPacket velocity) {
         if (velocity.getEntityId() == this.mc.player.getId()) {
            this.windowResync = true;
            this.logEvent("EntityVelocityUpdateS2CPacket");
         }
      } else if (event.packet instanceof ExplosionS2CPacket explosion) {
         if (explosion.playerKnockback().isPresent()) {
            this.windowResync = true;
            this.logEvent("ExplosionS2CPacket");
         }
      }
   }

   private void logEvent(String name) {
      if (this.logTicks.get()) {
         HunterBuddyAddon.LOG.info(String.format(Locale.ROOT, "[300kmfly-tick] event=%s t=%d x=%s z=%s", name, this.tickCount,
            this.mc.player == null ? "-" : num(this.mc.player.getX()), this.mc.player == null ? "-" : num(this.mc.player.getZ())));
      }
   }

   private static String num(double value) {
      return String.format(Locale.ROOT, "%.3f", value);
   }

   private static String num(Double value) {
      return value == null ? "-" : num(value.doubleValue());
   }

   private static String bounds(double[] values) {
      return values == null ? "-" : num(values[0]) + "," + num(values[1]) + "," + num(values[2]);
   }

   private void logTick(Vec3d vanilla, Vec3d look, Vec3d previous, double[] lo, double[] hi, Vec3d ridden, Double terrain, Double unloaded, Double limit, Vec3d result, Boolean rocket) {
      if (!this.logTicks.get()) {
         return;
      }

      HunterBuddyAddon.LOG.info(String.format(Locale.ROOT,
         "[300kmfly-tick] t=%d y=%s x=%s z=%s pitch=%s yaw=%s van=%s vanLook=%s prev=%s prevLook=%s lo=%s hi=%s ride=%s rideLook=%s terrain=%s unloaded=%s limit=%s final=%s finalLook=%s reason=%s rocket=%s",
         this.windowGlideTicks,
         num(this.mc.player.getY()), num(this.mc.player.getX()), num(this.mc.player.getZ()), num(this.mc.player.getPitch()), num(this.mc.player.getYaw()),
         num(vanilla.length()), num(vanilla.dotProduct(look)),
         previous == null ? "-" : num(previous.length()), previous == null ? "-" : num(previous.dotProduct(look)),
         bounds(lo), bounds(hi),
         ridden == null ? "-" : num(ridden.length()), ridden == null ? "-" : num(ridden.dotProduct(look)),
         num(terrain), num(unloaded), num(limit),
         result == null ? "-" : num(result.length()), result == null ? "-" : num(result.dotProduct(look)),
         this.windowReason, rocket == null ? "-" : rocket.toString()));
   }

   public Vec3d windowVelocity(Vec3d vanilla) {
      if (!this.isActive() || this.boostMode.get() != BoostMode.WINDOW || this.mc.player == null || this.mc.world == null) {
         return null;
      }

      this.windowGlideTicks++;
      Vec3d pos = this.mc.player.getEntityPos();
      boolean calibrate = this.windowLastPos == null;
      Vec3d previous = calibrate ? null : pos.subtract(this.windowLastPos);
      boolean resync = this.windowResync || (previous != null && previous.length() > 40.0);
      this.windowLastPos = pos;
      this.windowResync = false;

      Vec3d look = this.mc.player.getRotationVector();
      float pitch = this.mc.player.getPitch();
      float yaw = this.mc.player.getYaw();
      Vec3d lastLook = this.windowLastLook != null ? this.windowLastLook : look;
      float lastPitch = this.windowLastLook != null ? this.windowLastPitch : pitch;
      this.windowLastLook = look;
      this.windowLastPitch = pitch;

      if (calibrate) {
         this.windowReason = "calibrating";
         this.logTick(vanilla, look, previous, null, null, null, null, null, null, null, null);
         return null;
      }
      if (resync) {
         this.windowReason = "resync";
         this.logTick(vanilla, look, previous, null, null, null, null, null, null, null, null);
         return null;
      }
      if (this.takeoff.isActive()) {
         this.windowReason = "takeoff";
         this.logTick(vanilla, look, previous, null, null, null, null, null, null, null, null);
         return null;
      }
      if (!this.mc.player.isGliding()) {
         this.windowReason = "not gliding";
         this.logTick(vanilla, look, previous, null, null, null, null, null, null, null, null);
         return null;
      }
      if (this.mc.player.isTouchingWater() || this.mc.player.isInLava()) {
         this.windowReason = "fluid";
         this.logTick(vanilla, look, previous, null, null, null, null, null, null, null, null);
         return null;
      }
      if (this.mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
         this.windowReason = "levitation";
         this.logTick(vanilla, look, previous, null, null, null, null, null, null, null, null);
         return null;
      }

      RocketBoost boost = Modules.get().get(RocketBoost.class);
      if (boost != null && boost.isActive()) {
         this.windowReason = "rocket-boost on";
         this.logTick(vanilla, look, previous, null, null, null, null, null, null, null, null);
         return null;
      }
      if (!GliderSwap.rocketAttached(this.mc.player)) {
         this.windowReason = "no rocket";
         this.logTick(vanilla, look, previous, null, null, null, null, null, null, null, false);
         return null;
      }

      double gravity = this.mc.player.getFinalGravity();
      if (previous.y <= 0.0 && this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
         gravity = Math.min(gravity, 0.01);
      }

      Vec3d nextLook = Vec3d.fromPolar(MathHelper.clamp(pitch + (pitch - lastPitch), -90.0F, 90.0F), yaw);
      double scale = this.windowSize.get();
      double[] lo = new double[3];
      double[] hi = new double[3];
      double[] lo2 = new double[3];
      double[] hi2 = new double[3];
      GlideWindow.bounds(previous, look, lastLook, pitch, gravity, scale, lo, hi);
      GlideWindow.bounds(previous, look, nextLook, pitch, gravity, scale, lo2, hi2);
      for (int axis = 0; axis < 3; axis++) {
         lo[axis] = Math.max(lo[axis], lo2[axis]);
         hi[axis] = Math.min(hi[axis], hi2[axis]);
         if (lo[axis] > hi[axis]) {
            this.windowReason = "no room";
            this.logTick(vanilla, look, previous, lo, hi, null, null, null, null, null, true);
            return null;
         }
      }

      double tolerance = this.pitchTolerance.get();
      double cap = this.speedCap.get();
      Vec3d v = GlideWindow.ride(lo, hi, look, pitch, tolerance, cap);
      if (v == null) {
         this.windowReason = "no room";
         this.logTick(vanilla, look, previous, lo, hi, null, null, null, null, null, true);
         return null;
      }

      Vec3d ridden = v;
      Double terrainDistance = null;
      Double unloadedDistance = null;
      int lookahead = this.brakeLookahead.get();
      Vec3d dir = v.normalize();
      double reach = Math.max(4.0, cap * lookahead);
      double limit = cap;
      Vec3d eye = this.mc.player.getEyePos();
      HitResult hit = this.mc.world.raycast(new RaycastContext(eye, eye.add(dir.multiply(reach)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player));
      if (hit.getType() != HitResult.Type.MISS) {
         terrainDistance = hit.getPos().distanceTo(eye);
         limit = Math.min(limit, hit.getPos().distanceTo(eye) / lookahead);
      }

      for (double d = 8.0; d <= reach; d += 8.0) {
         Vec3d point = eye.add(dir.multiply(d));
         int x = (int)Math.floor(point.x);
         int z = (int)Math.floor(point.z);
         if (!this.mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
            unloadedDistance = d;
            limit = Math.min(limit, d / lookahead);
            break;
         }
      }

      if (limit < v.length()) {
         v = GlideWindow.ride(lo, hi, look, pitch, tolerance, limit);
         if (v == null) {
            this.windowReason = "brake";
            this.logTick(vanilla, look, previous, lo, hi, ridden, terrainDistance, unloadedDistance, limit, null, true);
            return null;
         }
      }

      if (v.dotProduct(look) <= vanilla.dotProduct(look)) {
         this.windowReason = "vanilla faster";
         this.logTick(vanilla, look, previous, lo, hi, ridden, terrainDistance, unloadedDistance, limit, null, true);
         return null;
      }

      this.windowRidden++;
      this.windowReason = "riding";
      this.logTick(vanilla, look, previous, lo, hi, ridden, terrainDistance, unloadedDistance, limit, v, true);
      return v;
   }

   private boolean noRockets() {
      int total = this.totalRockets();
      return total == 0 || total < this.minRockets.get();
   }

   private boolean legBlocked() {
      double legPitch = Math.toRadians(this.diving ? this.activeDive() : -this.activeClimb());
      double yaw = Math.toRadians(this.mc.player.getYaw());
      Vec3d start = this.mc.player.getEyePos();
      Vec3d dir = new Vec3d(-Math.sin(yaw) * Math.cos(legPitch), -Math.sin(legPitch), Math.cos(yaw) * Math.cos(legPitch));
      Vec3d end = start.add(dir.multiply(32.0));
      HitResult hit = this.mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player));
      return hit.getType() == HitResult.Type.BLOCK;
   }

   private boolean boostTiming(RocketBoost boost) {
      return boost.boosting && boost.boostStartMs != -1;
   }

   private void refire() {
      int since = this.tickCount - this.lastFireTick;
      boolean attached = GliderSwap.rocketAttached(this.mc.player);
      if (attached) {
         this.rocketSeenSinceFire = true;
      }
      if (since < 3) {
         return;
      }

      RocketBoost boost = Modules.get().get(RocketBoost.class);
      boolean due;
      if (boost != null && boost.isActive()) {
         due = this.boostTiming(boost)
            && boost.boostDuration.get() - (System.currentTimeMillis() - boost.boostStartMs) <= this.refireLead.get();
      } else {
         due = since >= this.minimumLife() - (this.refireLead.get() + 49) / 50;
      }

      if (this.mc.player.isTouchingWater() || this.mc.player.isInLava()
         || this.mc.player.hasStatusEffect(StatusEffects.LEVITATION) || this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
         return;
      }

      boolean lost = this.rocketSeenSinceFire ? !attached : since >= 20;
      if (!due && !lost) {
         return;
      }

      if (this.totalRockets() < this.minRockets.get()) {
         if (!this.lowStockWarned) {
            this.lowStockWarned = true;
            this.info("Rocket stock below " + this.minRockets.get() + ", no longer lighting rockets.");
         }

         return;
      }

      if (!this.ensureRocketsInHotbar()) {
         return;
      }

      if (Utils.firework(this.mc, false) == -1) {
         return;
      }

      this.lastFireTick = this.tickCount;
      this.rocketSeenSinceFire = false;
      this.fireTicks.addLast(this.tickCount);
   }

   private int minimumLife() {
      int duration = 1;
      FindItemResult hotbar = InvUtils.findInHotbar(Rockets::isPlain);
      if (hotbar.found()) {
         ItemStack stack = hotbar.isOffhand() ? this.mc.player.getOffHandStack() : this.mc.player.getInventory().getStack(hotbar.slot());
         FireworksComponent component = stack.get(DataComponentTypes.FIREWORKS);
         if (component != null) {
            duration = component.flightDuration();
         }
      }

      return 10 * (1 + duration);
   }

   private int totalRockets() {
      return InvUtils.find(Rockets::isPlain).count();
   }

   private boolean ensureRocketsInHotbar() {
      FindItemResult hotbar = InvUtils.findInHotbar(Rockets::isPlain);
      if (hotbar.found()) {
         return true;
      }

      FindItemResult inv = InvUtils.find(Rockets::isPlain);
      if (!inv.found()) {
         return false;
      }

      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            if (!QuietClicks.readyOrSettle()) {
               return false;
            }

            InvUtils.move().from(inv.slot()).to(i);
            return true;
         }
      }

      return false;
   }

   private void scanAhead() {
      Vec3d velocity = this.mc.player.getVelocity();
      double dirX = velocity.x;
      double dirZ = velocity.z;
      double speed = Math.hypot(dirX, dirZ);
      if (speed >= 0.3) {
         dirX /= speed;
         dirZ /= speed;
      } else {
         double yaw = Math.toRadians(this.mc.player.getYaw());
         dirX = -Math.sin(yaw);
         dirZ = Math.cos(yaw);
      }

      int margin = OBSTACLE_MARGIN;
      double halfBand = this.band.get();
      double currentY = this.mc.player.getY();
      double effCenter = Math.max(this.center, this.obstacleCenter);
      int bottomY = this.mc.world.getBottomY();
      int topY = this.mc.world.getTopYInclusive();
      int low = Math.max(bottomY, Math.min(topY, (int)Math.floor(Math.min(currentY, this.center - halfBand)) - margin));
      int high = Math.max(bottomY, Math.min(topY, (int)Math.ceil(Math.max(currentY, effCenter + halfBand)) + margin));
      double required = this.center;
      for (int distance = 16; distance <= 192; distance += 16) {
         double px = this.mc.player.getX() + dirX * distance;
         double pz = this.mc.player.getZ() + dirZ * distance;
         int x = (int)Math.floor(px);
         int z = (int)Math.floor(pz);
         if (!this.mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
            break;
         }

         int top = this.findObstacleTop(x, z, low, high, margin);
         top = Math.max(top, this.findObstacleTop((int)Math.floor(px + dirZ * 3.0), (int)Math.floor(pz - dirX * 3.0), low, high, margin));
         top = Math.max(top, this.findObstacleTop((int)Math.floor(px - dirZ * 3.0), (int)Math.floor(pz + dirX * 3.0), low, high, margin));
         if (top == Integer.MIN_VALUE) {
            continue;
         }

         double need = top + margin + halfBand;
         if (need > required) {
            required = need;
         }
      }

      if (required > this.center) {
         this.obstacleCenter = Math.min(Math.max(this.obstacleCenter, required), topY - 10);
         this.obstacleHold = 60;
      } else if (this.obstacleHold > 0) {
         this.obstacleHold -= 5;
         if (this.obstacleHold <= 0) {
            this.obstacleCenter = -1.0;
         }
      }
   }

   private int findObstacleTop(int x, int z, int low, int high, int margin) {
      int top = this.mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
      int bottomY = this.mc.world.getBottomY();
      if (top <= bottomY) {
         return Integer.MIN_VALUE;
      }

      if (top + margin <= this.center - this.band.get()) {
         return top;
      }

      final int skyGap = 32;
      int end = Math.min(high, top - 1);
      int firstBlocking = top;
      int air = 0;
      int lowerAir = 0;
      int maxAir = 0;
      boolean lowerRun = true;
      BlockPos.Mutable pos = new BlockPos.Mutable();
      for (int y = low; y <= end; y++) {
         BlockState state = this.mc.world.getBlockState(pos.set(x, y, z));
         if (state.blocksMovement() || !state.getFluidState().isEmpty()) {
            firstBlocking = y;
            break;
         }

         if (state.isAir()) {
            air++;
            if (lowerRun) {
               lowerAir++;
            }
         } else {
            air = 0;
            lowerRun = false;
         }

         maxAir = Math.max(maxAir, air);
      }

      if (firstBlocking == top) {
         if (maxAir >= skyGap) {
            return Integer.MIN_VALUE;
         }

         int downAir = lowerAir;
         for (int y = low - 1; y >= bottomY && downAir < skyGap; y--) {
            if (!this.mc.world.getBlockState(pos.set(x, y, z)).isAir()) {
               break;
            }

            downAir++;
         }

         if (downAir >= skyGap) {
            return Integer.MIN_VALUE;
         }

         if (lowerAir == end - low + 1) {
            air += downAir - lowerAir;
         }

         for (int y = end + 1; y < top && air < skyGap; y++) {
            if (!this.mc.world.getBlockState(pos.set(x, y, z)).isAir()) {
               break;
            }

            air++;
         }

         return air >= skyGap ? Integer.MIN_VALUE : top;
      }

      int lastBlocking = firstBlocking;
      air = 0;
      for (int y = firstBlocking + 1; y < top; y++) {
         BlockState state = this.mc.world.getBlockState(pos.set(x, y, z));
         if (state.blocksMovement() || !state.getFluidState().isEmpty()) {
            lastBlocking = y;
            air = 0;
         } else if (state.isAir()) {
            if (++air >= skyGap) {
               break;
            }
         } else {
            air = 0;
         }
      }

      return lastBlocking + 1;
   }
}
