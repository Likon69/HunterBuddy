package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.elytraboost.FireworkBoostTracker;
import com.hunterbuddy.modules.mixin.accessors.EntityVelocityUpdateS2CPacketAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class RocketBoost extends Module {
   private final SettingGroup sgGeneral = settings.getDefaultGroup();

   public final Setting<Integer> boostDuration = sgGeneral.add(new IntSetting.Builder()
      .name("boost-duration")
      .description("Maximum ms the boost can be extended. Test up to the server's Grim max-ping-firework-boost limit.")
      .defaultValue(800)
      .min(100)
      .max(10000)
      .sliderRange(100, 10000)
      .build()
   );

   public final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
      .name("debug")
      .description("Show boost state in the action bar.")
      .defaultValue(false)
      .build()
   );

   public final Setting<Double> speedMultiplier = sgGeneral.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
      .name("speed-multiplier")
      .description("Firework speed multiplier (vanilla 1.5). Applied directly — higher values give more speed but may trigger Grim setback depending on look angle. With auto-speed on, this becomes the hard ceiling instead.")
      .defaultValue(2.0)
      .min(1.0)
      .max(5.0)
      .sliderRange(1.5, 5.0)
      .build()
   );

   public final Setting<Integer> maxTrackTime = sgGeneral.add(new IntSetting.Builder()
      .name("max-track-time")
      .description("Max ms to wait for the destroy packet before giving up tracking. Safety net for out-of-range rockets.")
      .defaultValue(5000)
      .min(1000)
      .max(30000)
      .sliderRange(1000, 15000)
      .build()
   );

   public final Setting<Integer> cooldownAfterFlush = sgGeneral.add(new IntSetting.Builder()
      .name("cooldown-after-flush")
      .description("Ms to ignore new rockets after a flush. Prevents back-to-back boost extension. 0 = disabled.")
      .defaultValue(0)
      .min(0)
      .max(10000)
      .sliderRange(0, 5000)
      .build()
   );

   public final Setting<Boolean> setbackDetector = sgGeneral.add(new BoolSetting.Builder()
      .name("setback-detector")
      .description("Detect Grim setback (PlayerPositionLookS2CPacket) during boost. Increments setbackCount and flushes immediately. Default ON.")
      .defaultValue(true)
      .build()
   );

   public final Setting<Boolean> cancelOpposingKnockback = sgGeneral.add(new BoolSetting.Builder()
      .name("cancel-opposing-knockback")
      .description("While gliding, drop server velocity packets that are near-zero or push against your horizontal motion. Keeps your speed through hits — but Grim expects you to take knockback, so test it before relying on it.")
      .defaultValue(false)
      .build()
   );

   private final SettingGroup sgAuto = settings.createGroup("Auto Speed");

   public final Setting<Boolean> autoSpeed = sgAuto.add(new BoolSetting.Builder()
      .name("auto-speed")
      .description("Compute the highest multiplier Grim still accepts for your current look direction, every tick, instead of using the fixed slider. Grim checks each axis separately, so a fixed multiplier is either too slow at some angles or a setback at others.")
      .defaultValue(false)
      .build()
   );

   public final Setting<Double> autoAxisLimit = sgAuto.add(new DoubleSetting.Builder()
      .name("auto-axis-limit")
      .description("Per-axis velocity ceiling Grim tolerates during a firework boost, in blocks/tick. 1.7 is the constant Grim uses in UncertaintyHandler#tickFireworksBox. Lower it if you get set back.")
      .defaultValue(1.7)
      .min(0.5)
      .max(4.0)
      .sliderRange(1.0, 2.5)
      .visible(autoSpeed::get)
      .build()
   );

   public final Setting<Double> autoYMargin = sgAuto.add(new DoubleSetting.Builder()
      .name("auto-y-margin")
      .description("Safety margin kept on every axis. Grim's Simulation check flags at an offset of 0.001 and its accumulator decays at 0.999 per tick, so it barely forgets — the margin only has to cover our own rounding, not buy slack. Anything above ~0.01 costs top speed for nothing, since the vertical axis is the one that binds in a dive.")
      .defaultValue(0.005)
      .min(0.0)
      .max(0.5)
      .sliderRange(0.0, 0.05)
      .visible(autoSpeed::get)
      .build()
   );

   public final Setting<Boolean> yawJitter = sgAuto.add(new BoolSetting.Builder()
      .name("yaw-jitter")
      .description("Alternate your yaw by +/-0.01 degrees each tick while boosting. Grim builds its tolerance box from this tick's look vector AND the previous one, so the jitter widens the box for free. Invisible in game.")
      .defaultValue(false)
      .build()
   );

   /** Vanilla firework multiplier; auto-speed never returns less than this. */
   private static final double VANILLA_SPEED = 1.5;

   /** Grim's per-axis slack for a skipped tick, mirrored from its firework box. */
   private static final double ANTI_TICK_SKIPPING = 0.05;

   /** Ticks spent climbing back from vanilla speed to the solved one after a setback. */
   private static final int AUTO_RECOVERY_TICKS = 60;

   public int setbackCount = 0;
   public long lastEventTime = Long.MAX_VALUE;

   private float lastYaw;
   private float lastPitch;
   private boolean hasLastRotation = false;
   private boolean jitterUp = false;
   private double lastAutoSpeed = Double.NaN;
   private double autoRecovery = 1.0;

   public double getSpeed() {
      if (!autoSpeed.get()) return speedMultiplier.get();

      double auto = computeAutoSpeed();
      if (Double.isNaN(auto)) return speedMultiplier.get();

      lastAutoSpeed = auto;
      return auto;
   }

   /**
    * Highest firework multiplier that keeps every axis inside Grim's tolerance box.
    *
    * <p>Grim does not check a speed scalar, it checks each axis against a box built by
    * unioning this tick's look vector with the previous one — see its
    * {@code UncertaintyHandler#tickFireworksBox}, which this mirrors down to the 0.05
    * slack and the flat 1.7 cap. The vanilla firework tick leaves velocity at
    * {@code 0.5 * v + look * (0.1 + 0.5 * multiplier)} on every axis, which is affine in
    * the multiplier — so the largest legal value has a closed form instead of a search.
    */
   private double computeAutoSpeed() {
      if (mc.player == null) return Double.NaN;

      Vec3d look = mc.player.getRotationVector();
      Vec3d lastLook = hasLastRotation ? Vec3d.fromPolar(lastPitch, lastYaw) : look;
      double solved = solveMaxMultiplier(mc.player.getVelocity(), look, lastLook, autoAxisLimit.get());
      if (Double.isNaN(solved)) return Double.NaN;

      solved = MathHelper.clamp(solved, VANILLA_SPEED, speedMultiplier.get());

      // Grim's offset accumulator decays at 0.999, so a flag is close to permanent and the
      // next one arrives that much sooner. After a setback, fall back to vanilla and walk
      // the multiplier up again rather than immediately asking for the same speed.
      return VANILLA_SPEED + (solved - VANILLA_SPEED) * autoRecovery;
   }

   private double solveMaxMultiplier(Vec3d velocity, Vec3d look, Vec3d lastLook, double threshold) {
      double margin = autoYMargin.get();
      double bestK = Double.MAX_VALUE;

      for (int axis = 0; axis < 3; axis++) {
         double dir = axisOf(look, axis);
         if (Math.abs(dir) < 1.0E-6) continue;

         double last = axisOf(lastLook, axis);
         double min = Math.max(-threshold, (Math.min(-ANTI_TICK_SKIPPING, dir) + Math.min(-ANTI_TICK_SKIPPING, last)) * threshold);
         double max = Math.min(threshold, (Math.max(ANTI_TICK_SKIPPING, dir) + Math.max(ANTI_TICK_SKIPPING, last)) * threshold);

         min += margin;
         max -= margin;

         double bound = dir > 0.0 ? max : min;
         bestK = Math.min(bestK, (bound - 0.5 * axisOf(velocity, axis)) / dir);
      }

      if (bestK == Double.MAX_VALUE) return Double.NaN;

      // bestK is (0.1 + 0.5 * multiplier).
      return 2.0 * (bestK - 0.1);
   }

   private static double axisOf(Vec3d vec, int axis) {
      return axis == 0 ? vec.x : (axis == 1 ? vec.y : vec.z);
   }

   public int trackedRocketId = -1;
   public long boostStartMs = -1;
   public long trackStartMs = -1;
   public long lastFlushMs = -1;
   public boolean boosting = false;
   public long lastGainedMs = -1;
   public final List<Packet<?>> pongQueue = new ArrayList<>();

   public RocketBoost() {
      super(HunterBuddyAddon.UTILITY_CATEGORY, "rocket-boost", "Approach A: cancel destroy + queue pongs (Grim-aware, 800ms default).");
   }

   @Override
   public void onDeactivate() {
      flushAndStop();
      lastGainedMs = -1;
      hasLastRotation = false;
      lastAutoSpeed = Double.NaN;
      autoRecovery = 1.0;
   }

   public void setTrackedRocket(int entityId) {
      if (trackedRocketId == entityId) return;
      if (cooldownAfterFlush.get() > 0 && lastFlushMs != -1
         && System.currentTimeMillis() - lastFlushMs < cooldownAfterFlush.get()) {
         return;
      }
      if (trackedRocketId != -1) flushAndStop();
      trackedRocketId = entityId;
      boosting = true;
      trackStartMs = System.currentTimeMillis();
      lastEventTime = System.currentTimeMillis();
   }

   public boolean isBoosting() {
      return boosting;
   }

   public void flushAndStop() {
      int idToRemove = trackedRocketId;
      if (boostStartMs != -1) {
         lastGainedMs = System.currentTimeMillis() - boostStartMs;
      }
      boosting = false;
      boostStartMs = -1;
      trackStartMs = -1;
      lastFlushMs = System.currentTimeMillis();
      trackedRocketId = -1;

      int pongsToFlush = pongQueue.size();
      if (mc.getNetworkHandler() != null) {
         for (Packet<?> p : pongQueue) mc.getNetworkHandler().sendPacket(p);
      }
      pongQueue.clear();

      // A setback reaches us as PacketEvent.Receive, which Meteor fires on the netty thread, so
      // this can run while the render thread is walking the entity list. That list is a live
      // fastutil map with no locking behind getEntities(), and removing from under the iterator
      // makes it hand back a null entity. Take the rocket out on the main thread instead.
      if (idToRemove != -1) {
         final int rocketId = idToRemove;
         mc.execute(() -> {
            if (mc.world != null) mc.world.removeEntity(rocketId, net.minecraft.entity.Entity.RemovalReason.KILLED);
         });
      }

      lastEventTime = System.currentTimeMillis();
   }

   /** True while one of our rockets is pushing us, whether or not we are extending it. */
   private boolean isUnderBoost() {
      return mc.player != null && mc.player.isGliding() && (boosting || FireworkBoostTracker.isBoosting(200));
   }

   @EventHandler
   private void onTickJitter(TickEvent.Pre event) {
      if (!yawJitter.get() || !isUnderBoost()) return;

      // Grim unions this tick's look vector with the last one when it builds the box,
      // so alternating the yaw by a hair widens it without moving the camera.
      jitterUp = !jitterUp;
      mc.player.setYaw(mc.player.getYaw() + (jitterUp ? 0.01F : -0.01F));
   }

   @EventHandler
   private void onTickRotation(TickEvent.Post event) {
      if (mc.player == null) {
         hasLastRotation = false;
         return;
      }

      if (autoRecovery < 1.0) {
         autoRecovery = Math.min(1.0, autoRecovery + 1.0 / AUTO_RECOVERY_TICKS);
      }

      // Recorded at the end of the tick, so during the next one this holds the rotation
      // the server actually saw — which is the one Grim widens its box with.
      lastYaw = mc.player.getYaw();
      lastPitch = mc.player.getPitch();
      hasLastRotation = true;
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   private void onReceivePacket(PacketEvent.Receive event) {
      if (mc.getNetworkHandler() == null) return;
      if (mc.player == null) return;

      if (cancelOpposingKnockback.get()
         && event.packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
         && mc.player.isGliding()) {
         EntityVelocityUpdateS2CPacketAccessor accessor = (EntityVelocityUpdateS2CPacketAccessor) velocityPacket;
         if (accessor.getEntityId() == mc.player.getId()) {
            Vec3d knockback = accessor.getVelocity();
            Vec3d current = mc.player.getVelocity();
            double knockbackH = knockback.x * knockback.x + knockback.z * knockback.z;

            if (knockbackH < 1.0E-2) {
               event.cancel();
               return;
            }

            double currentH = current.x * current.x + current.z * current.z;
            if (currentH > 1.0E-2 && current.x * knockback.x + current.z * knockback.z < 0.0) {
               event.cancel();
               return;
            }
         }
      }

      // A setback can land outside a boost window too, and it means the same thing either
      // way: Grim rejected our last movement, so stop asking for the solved multiplier.
      if (autoSpeed.get() && event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket) {
         autoRecovery = 0.0;
      }

      if (!boosting) return;
      if (setbackDetector.get() && event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket) {
         setbackCount++;
         flushAndStop();
         return;
      }
      if (!(event.packet instanceof EntitiesDestroyS2CPacket packet)) return;

      IntList remaining = new IntArrayList();
      boolean found = false;
      for (int id : packet.getEntityIds()) {
         if (id == trackedRocketId) {
            event.cancel();
            boostStartMs = System.currentTimeMillis();
            found = true;
         } else {
            remaining.add(id);
         }
      }
      if (found && !remaining.isEmpty()) {
         // Same thread, same list: onEntitiesDestroy takes entities straight out of it.
         EntitiesDestroyS2CPacket rest = new EntitiesDestroyS2CPacket(remaining);
         mc.execute(() -> {
            if (mc.getNetworkHandler() != null) mc.getNetworkHandler().onEntitiesDestroy(rest);
         });
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (mc.player == null || !mc.player.isGliding()) return;
      if (!boosting || boostStartMs == -1) return;
      if (!(event.packet instanceof CommonPongC2SPacket packet)) return;

      long elapsed = System.currentTimeMillis() - boostStartMs;
      if (elapsed < boostDuration.get()) {
         event.cancel();
         pongQueue.add(event.packet);
      } else {
         flushAndStop();
      }
   }

   @EventHandler
   private void onTickDebug(meteordevelopment.meteorclient.events.world.TickEvent.Post event) {
      if (!debug.get() || mc.player == null) return;

      if (System.currentTimeMillis() - lastEventTime > 5000) {
         return;
      }

      if (boosting && boostStartMs == -1 && trackStartMs != -1) {
         long waiting = System.currentTimeMillis() - trackStartMs;
         if (waiting >= maxTrackTime.get()) {
            flushAndStop();
            return;
         }
      }

      String msg;
      if (!boosting) {
         if (lastGainedMs >= 0) {
            String cooldInfo;
            if (cooldownAfterFlush.get() > 0) {
               if (lastFlushMs == -1) {
                  cooldInfo = "coold 0s / " + (cooldownAfterFlush.get() / 1000) + "s";
               } else {
                  long elapsed = Math.min(System.currentTimeMillis() - lastFlushMs, cooldownAfterFlush.get());
                  cooldInfo = "coold " + (elapsed / 1000) + "s / " + (cooldownAfterFlush.get() / 1000) + "s"
                     + (elapsed >= cooldownAfterFlush.get() ? " §aREADY" : "");
               }
            } else {
               cooldInfo = "coold off";
            }
            msg = "§a§l[RB] §r§a+ " + lastGainedMs + "ms §7last gain"
               + "\n§7ready for next rocket | " + cooldInfo;
         } else {
            msg = "§7§l[RB] §r§7idle — fire a rocket to start"
               + "\n§7cooldown: " + (cooldownAfterFlush.get() > 0 ? (cooldownAfterFlush.get() / 1000) + "s" : "off");
         }
      } else if (boostStartMs == -1) {
         msg = "§e§l[RB] §r§etracking #" + trackedRocketId
               + "\n§7destroy packet pas encore arrive | pongs: " + pongQueue.size();
      } else {
         long elapsed = System.currentTimeMillis() - boostStartMs;
         long remaining = boostDuration.get() - elapsed;
         String col = remaining > 200 ? "§a" : "§c";
         String colBold = remaining > 200 ? "§a§l" : "§c§l";
         long percent = (elapsed * 100) / Math.max(1, boostDuration.get());
         String setbackTag = setbackCount > 0 ? " §4§lFLAGGED x" + setbackCount + "§r" : "";
         msg = colBold + "[RB] §r" + col + elapsed + "ms" + "§7/" + boostDuration.get() + "ms (" + percent + "%)"
            + "\n§7+" + remaining + "ms left | pongs: " + pongQueue.size() + setbackTag;
      }

      if (autoSpeed.get()) {
         msg += Double.isNaN(lastAutoSpeed)
            ? " §8| auto —"
            : String.format(" §8| auto §bx%.2f", lastAutoSpeed);
      }

      mc.player.sendMessage(net.minecraft.text.Text.literal(msg), true);
   }
}
