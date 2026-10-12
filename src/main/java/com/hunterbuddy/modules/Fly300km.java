package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.util.GliderSwap;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayDeque;

public class Fly300km extends Module {
   public enum Mode {
      LEGS,
      LEVEL
   }

   private static final int TAKEOFF_TIMEOUT_TICKS = 280;
   private static final int OBSTACLE_MARGIN = 8;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
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
      .name("dive-pitch").description("Degrees downwards during the dive.")
      .defaultValue(20.0).min(0.0).max(45.0).sliderRange(0.0, 45.0).build());
   private final Setting<Double> climbPitch = this.sgGeneral.add(new DoubleSetting.Builder()
      .name("climb-pitch").description("Degrees upwards during the climb.")
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

   private final Setting<Boolean> useRocketBoost = this.sgBoost.add(new BoolSetting.Builder()
      .name("use-rocket-boost").description("Enable RocketBoost on activation if it is off.")
      .defaultValue(true).build());
   private final Setting<Boolean> autoSpeed = this.sgBoost.add(new BoolSetting.Builder()
      .name("auto-speed").description("Turn on rocket-boost's auto-speed while 300kmfly flies, and put it back after. Off leaves rocket-boost exactly as you set it.")
      .defaultValue(true).visible(this.useRocketBoost::get).build());

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
   private boolean tookOff;
   private final ArrayDeque<Integer> fireTicks = new ArrayDeque<>();

   public Fly300km() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "300kmfly",
         "Fast firework flight on a fixed heading: keeps a rocket lit at all times, rides rocket-boost and holds an altitude band with alternating dive and climb legs.");
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
      this.fireTicks.clear();
      if (this.mc.player == null) {
         return;
      }

      this.lockedYaw = this.mc.player.getYaw();

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

      this.autoSpeedSetByUs = false;
      this.boostEnabledByUs = false;
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

         target = this.diving ? this.divePitch.get().floatValue() : -this.climbPitch.get().floatValue();
         leg = this.diving ? "DIVE" : "CLIMB";
      } else {
         double raw = (y - effCenter) * 1.5;
         target = (float)MathHelper.clamp(raw, -this.climbPitch.get(), this.divePitch.get());
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

         HunterBuddyAddon.LOG.info(String.format("[300kmfly] %.1f b/s %.0f km/h y=%.1f pitch=%.1f leg=%s rockets/min=%d",
            bps, bps * 3.6, y, next, leg, this.fireTicks.size()));
      }
   }

   private boolean noRockets() {
      int total = this.totalRockets();
      return total == 0 || total < this.minRockets.get();
   }

   private boolean legBlocked() {
      double legPitch = Math.toRadians(this.diving ? this.divePitch.get() : -this.climbPitch.get());
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
      FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
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
      return InvUtils.find(new Item[]{Items.FIREWORK_ROCKET}).count();
   }

   private boolean ensureRocketsInHotbar() {
      FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
      if (hotbar.found()) {
         return true;
      }

      FindItemResult inv = InvUtils.find(new Item[]{Items.FIREWORK_ROCKET});
      if (!inv.found()) {
         return false;
      }

      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
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
