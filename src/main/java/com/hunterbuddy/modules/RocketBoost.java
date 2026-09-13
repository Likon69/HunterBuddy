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
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
      .description("Firework speed multiplier (vanilla 1.5), applied raw. Higher values give more speed but may trigger a Grim setback depending on your look angle — on a cardinal heading anything above vanilla eventually overshoots, since vanilla already sits on the limit there. Ignored while auto-speed is on, which uses its own ceiling instead.")
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

   public final Setting<Double> autoCeiling = sgAuto.add(new DoubleSetting.Builder()
      .name("auto-ceiling")
      .description("Highest multiplier auto-speed may ask for. Separate from speed-multiplier so that raising the ceiling for the solver cannot make the manual mode dangerous. The solver stops finding anything to use with past 2.5; above that the first boosted tick from a standstill overshoots far enough to be set back on the spot.")
      .defaultValue(2.5)
      .min(1.5)
      .max(5.0)
      .sliderRange(1.5, 3.5)
      .visible(autoSpeed::get)
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
      .description("Alternate your yaw by +/-0.01 degrees each tick while boosting. Measures as worth nothing: Grim sums the two look vectors with a 0.05 floor under each side, and a 0.01 degree yaw moves an axis by 0.0002, which that floor swallows whole. Kept because it is harmless, not because it helps.")
      .defaultValue(false)
      .build()
   );

   private final SettingGroup sgSafety = settings.createGroup("Safety");

   public final Setting<Boolean> wallCheck = sgSafety.add(new BoolSetting.Builder()
      .name("wall-check")
      .description("Stop adding speed when there is something solid in front of you. This module makes a rocket push harder than vanilla, which is exactly what turns a survivable approach to a wall into a crash: it raycasts along your look as far as you would travel in lookahead ticks, and drops the boost back to vanilla 1.5 when that line is blocked. It never cancels the boost, only the extra.")
      .defaultValue(true)
      .build()
   );

   public final Setting<Boolean> chunkCheck = sgSafety.add(new BoolSetting.Builder()
      .name("chunk-check")
      .description("Stop adding speed when the chunks ahead have not loaded yet. Terrain you cannot see is terrain nothing can check, and outrunning the loader at 2x is how you end up inside it. Same effect as wall-check: back to vanilla 1.5, boost kept.")
      .defaultValue(true)
      .build()
   );

   public final Setting<Integer> lookahead = sgSafety.add(new IntSetting.Builder()
      .name("lookahead")
      .description("How many ticks ahead wall-check and chunk-check look. The distance is that many ticks at the speed the boost is taking you to - where the rocket's push settles, or your current speed when that is higher - so a slow moment after a turn or a server correction does not shorten it.")
      .defaultValue(20)
      .min(4)
      .max(80)
      .sliderRange(8, 60)
      .visible(() -> wallCheck.get() || chunkCheck.get())
      .build()
   );

   public final Setting<Boolean> pivotDamping = sgSafety.add(new BoolSetting.Builder()
      .name("pivot-damping")
      .description("Push like a plain rocket on the ticks where the look turns sharply, which is when Grim setbacks cluster.")
      .defaultValue(true)
      .build()
   );

   public final Setting<Integer> pivotAngle = sgSafety.add(new IntSetting.Builder()
      .name("pivot-angle")
      .description("How far, in degrees, the look has to turn over pivot-window ticks for pivot-damping to push like a plain rocket on that tick.")
      .defaultValue(45)
      .min(10)
      .max(180)
      .sliderRange(10, 180)
      .visible(pivotDamping::get)
      .build()
   );

   public final Setting<Integer> pivotWindow = sgSafety.add(new IntSetting.Builder()
      .name("pivot-window")
      .description("How many ticks back pivot-damping looks for the direction the turn is measured from.")
      .defaultValue(5)
      .min(1)
      .max(PIVOT_WINDOW_MAX)
      .sliderRange(1, PIVOT_WINDOW_MAX)
      .visible(pivotDamping::get)
      .build()
   );

   private final SettingGroup sgBaritone = settings.createGroup("Baritone");

   public final Setting<Boolean> baritoneSync = sgBaritone.add(new BoolSetting.Builder()
      .name("baritone-sync")
      .description("Tell Baritone how hard and how long this module really boosts, while it is flying you. Baritone picks its pitch by simulating the boosted trajectory forward and raytracing it against terrain, and it assumes vanilla on both counts: 1.5 per boosted tick, and a boost that ends when the rocket's lifetime does. This module changes both — the multiplier, and cancelling the rocket's removal to hold it open for boost-duration. Left unsaid, the path Baritone cleared is not the path being flown, and it flies into blocks it never checked (its own \"hbonk\"). Turn this off to fly with Baritone the way it was before, which is also how you A/B the difference.")
      .defaultValue(true)
      .build()
   );

   public final Setting<Boolean> logTrace = sgAuto.add(new BoolSetting.Builder()
      .name("log-trace")
      .description("Write one line per boosted tick to hunterbuddy/rocketboost-trace.csv: velocity per axis, look, the multiplier asked for, the bounds we think Grim is holding us to, and how far outside them we landed. Works in manual mode too, which is where it is worth the most - a multiplier you already trust is the cleanest way to measure the model against the server. Costs no speed.")
      .defaultValue(false)
      .build()
   );

   /** Vanilla firework multiplier; auto-speed never returns less than this. */
   private static final double VANILLA_SPEED = 1.5;

   /** Grim's per-axis slack for a skipped tick, mirrored from its firework box. */
   private static final double ANTI_TICK_SKIPPING = 0.05;

   /**
    * The 1.7 Grim writes twice in its firework box - once as the scale, once as the clamp.
    *
    * <p>Deliberately not {@link #autoAxisLimit}: that setting stands in for both, so lowering it
    * shrinks the box twice over. The narrowing pass below has to mirror Grim, not our slider.
    */
   private static final double GRIM_FIREWORK_SCALE = 1.7;

   /** Bisection steps for the narrowing pass. 20 halvings resolve the multiplier past 1e-5. */
   private static final int GRIM_BOUND_STEPS = 20;

   /** How far a teleport has to move us before it counts as a setback rather than a resync. */
   private static final double SETBACK_MIN_DISTANCE_SQ = 0.25;

   /** Ticks spent climbing back from vanilla speed to the solved one after a setback. */
   private static final int AUTO_RECOVERY_TICKS = 60;

   private static final int PIVOT_WINDOW_MAX = 20;

   private static final String TRACE_HEADER =
      "age,vx,vy,vz,lookx,looky,lookz,yaw,pitch,m_closed,m_final,offset,lo_x,hi_x,lo_y,hi_y,lo_z,hi_z,pivot";

   public int setbackCount = 0;
   public long lastEventTime = Long.MAX_VALUE;

   private float lastYaw;
   private float lastPitch;
   private boolean hasLastRotation = false;
   private boolean jitterUp = false;
   private double lastAutoSpeed = Double.NaN;

   /** Written from the netty thread when a setback lands, read from the main thread each tick. */
   private volatile double autoRecovery = 1.0;

   private final double[] traceLo = new double[3];
   private final double[] traceHi = new double[3];
   private final double[] tracePrevLo = new double[3];
   private final double[] tracePrevHi = new double[3];
   private boolean traceValid = false;
   private boolean tracePrevValid = false;
   private double traceClosed = Double.NaN;
   private double traceFinal = Double.NaN;
   private BufferedWriter traceWriter;
   private int tracePending = 0;
   private int lastTraceAge = -1;

   private final Vec3d[] pivotLooks = new Vec3d[PIVOT_WINDOW_MAX + 1];
   private final int[] pivotAges = new int[PIVOT_WINDOW_MAX + 1];
   private int pivotAge = Integer.MIN_VALUE;
   private boolean pivotNow = false;

   public double getSpeed() {
      boolean pivot = pivotDamped();
      lastAppliedSpeed = throttle(rawSpeed());
      return pivot ? Math.min(lastAppliedSpeed, VANILLA_SPEED) : lastAppliedSpeed;
   }

   private boolean pivotDamped() {
      if (mc.player == null) return false;
      int age = mc.player.age;
      if (age == pivotAge) return pivotNow;
      if (age != pivotAge + 1) Arrays.fill(pivotAges, Integer.MIN_VALUE);
      pivotAge = age;
      pivotNow = false;

      Vec3d look = mc.player.getRotationVector();
      int slot = Math.floorMod(age, pivotLooks.length);
      pivotLooks[slot] = look;
      pivotAges[slot] = age;

      if (!pivotDamping.get()) return false;
      int earlierAge = age - pivotWindow.get();
      int earlierSlot = Math.floorMod(earlierAge, pivotLooks.length);
      if (pivotAges[earlierSlot] != earlierAge) return false;

      Vec3d earlier = pivotLooks[earlierSlot];
      double limit = Math.cos(Math.toRadians(pivotAngle.get()));
      pivotNow = look.dotProduct(earlier) < limit || computeNextLook(look).dotProduct(earlier) < limit;
      return pivotNow;
   }

   /** The multiplier the speed settings ask for, before any safety throttle. */
   private double rawSpeed() {
      if (!autoSpeed.get()) {
         double manual = speedMultiplier.get();

         // The trace is worth more here than under the solver: a multiplier already known to fly
         // clean is the cleanest thing to measure the model against, and it changes no speed.
         if (logTrace.get()) {
            traceManual(manual);
         }

         return manual;
      }

      double auto = computeAutoSpeed();
      // The solver found nothing usable and we fall back to the slider. lastAutoSpeed keeps the last
      // value it did solve, because the debug line reports on the solver rather than on the flight -
      // which is exactly why Baritone must not be told from it.
      if (Double.isNaN(auto)) return speedMultiplier.get();

      lastAutoSpeed = auto;
      return auto;
   }

   /** Which safety check, if any, is currently holding the multiplier down. Shown on the debug line. */
   private String limiter = "speed";

   /**
    * Gives back vanilla's 1.5 instead of {@code asked} when there is something in front of us.
    *
    * <p>Unlike a module that sets velocity, this one only scales the firework's acceleration, so there
    * is nothing here to cap to a distance - the meaningful choice is between boosting harder than
    * vanilla and not. Ahead of a wall, or ahead of chunks that have not arrived, the extra speed is
    * the whole problem: it is what turns an approach the game would have survived into a crash, and
    * what outruns the chunk loader. The boost itself is left alone, so a Baritone flight keeps its
    * rocket and simply stops being faster than the trajectory it planned.
    */
   private double throttle(double asked) {
      this.limiter = "speed";
      if (asked <= VANILLA_SPEED) return asked;
      if (mc.player == null || mc.world == null || !mc.player.isGliding()) return asked;
      if (!wallCheck.get() && !chunkCheck.get()) return asked;

      Vec3d eye = mc.player.getEyePos();
      Vec3d look = mc.player.getRotationVec(1.0F);
      // How far the boost carries us in that many ticks: at the speed the rocket's push settles at under this
      // multiplier, or at our own speed when that is higher, floored so it always looks a little way ahead. The
      // firework tick leaves velocity at v + 0.1 look + 0.5 (m look - v), m being the constant the mixin replaces,
      // which settles at (m + 0.2) look. Our own speed alone is what this used to go by, and right after a turn or
      // a server correction it is low: the lookahead shrank to its floor of 8 blocks and the whole extra went
      // through towards a wall a second away. BepBoost looks ahead from its speed cap whatever the moment; flown an
      // hour each on 2026-09-10, it hit walls less than half as often per 1000 blocks, if for more reasons than this.
      double speed = Math.max(mc.player.getVelocity().length(), asked + 0.2);
      double reach = Math.max(8.0, speed * lookahead.get());

      double capped = asked;

      if (wallCheck.get()) {
         Vec3d end = eye.add(look.multiply(reach));
         HitResult hit = mc.world.raycast(new RaycastContext(
            eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
         if (hit.getType() != HitResult.Type.MISS) {
            double limit = scaleToDistance(asked, hit.getPos().distanceTo(eye), reach);
            if (limit < capped) {
               capped = limit;
               this.limiter = "wall ahead";
            }
         }
      }

      if (chunkCheck.get()) {
         for (double d = 16.0; d <= reach; d += 16.0) {
            BlockPos at = BlockPos.ofFloored(eye.add(look.multiply(d)));
            if (!mc.world.getChunkManager().isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) {
               double limit = scaleToDistance(asked, d, reach);
               if (limit < capped) {
                  capped = limit;
                  this.limiter = "unloaded chunks";
               }
               break;
            }
         }
      }

      return capped;
   }

   /**
    * The multiplier that is still reasonable with something {@code distance} away, given that we were
    * looking {@code reach} blocks ahead.
    *
    * <p>Proportional rather than a cliff, which is what the module this borrows the idea from does -
    * though it caps a speed to {@code distance / ticks}, and there is no such arithmetic here, because
    * what this module controls is an acceleration multiplier and not a velocity. So the distance decides
    * how much of the <i>extra</i> over vanilla survives: something at the far edge of the lookahead
    * barely costs anything, something a couple of blocks away leaves plain vanilla 1.5. It never goes
    * below vanilla - slowing the rocket itself is not this check's business, only declining to make it
    * stronger than the game would.
    */
   private double scaleToDistance(double asked, double distance, double reach) {
      double fraction = Math.max(0.0, Math.min(1.0, distance / Math.max(1.0, reach)));
      return VANILLA_SPEED + (asked - VANILLA_SPEED) * fraction;
   }

   /** What {@link #getSpeed} last actually handed the mixin, on every path through it. */
   private double lastAppliedSpeed = Double.NaN;

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

      Vec3d velocity = mc.player.getVelocity();
      Vec3d look = mc.player.getRotationVector();
      Vec3d nextLook = computeNextLook(look);
      double solved = solveMaxMultiplier(velocity, look, nextLook, autoAxisLimit.get());
      if (Double.isNaN(solved)) return Double.NaN;

      solved = MathHelper.clamp(solved, VANILLA_SPEED, autoCeiling.get());
      traceClosed = solved;
      solved = narrowToGrimBound(solved, velocity, look, nextLook);
      traceFinal = solved;

      if (logTrace.get()) {
         recordTrace(velocity, look);
      }

      // Grim's offset accumulator decays at 0.999, so a flag is close to permanent and the
      // next one arrives that much sooner. After a setback, fall back to vanilla and walk
      // the multiplier up again rather than immediately asking for the same speed.
      return VANILLA_SPEED + (solved - VANILLA_SPEED) * autoRecovery;
   }

   private Vec3d computeNextLook(Vec3d look) {
      float[] baritoneNext = nextBaritoneRotation();
      if (baritoneNext != null && baritoneNext.length == 2 && Float.isFinite(baritoneNext[0]) && Float.isFinite(baritoneNext[1])) {
         return Vec3d.fromPolar(baritoneNext[1], baritoneNext[0]);
      }
      if (!hasLastRotation || mc.player == null) return look;

      float currentYaw = mc.player.getYaw();
      float currentPitch = mc.player.getPitch();
      float nextYaw = currentYaw + MathHelper.wrapDegrees(currentYaw - lastYaw);
      float nextPitch = MathHelper.clamp(currentPitch + (currentPitch - lastPitch), -90.0F, 90.0F);
      return Vec3d.fromPolar(nextPitch, nextYaw);
   }

   private float[] nextBaritoneRotation() {
      if (this.baritoneRotationUnavailable) return null;
      try {
         baritone.api.IBaritone primary = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
         if (primary == null) return null;
         Object lookBehavior = primary.getLookBehavior();
         if (lookBehavior == null) return null;
         java.lang.reflect.Method method = this.baritoneNextRotationMethod;
         if (method == null) {
            method = lookBehavior.getClass().getMethod("hunterbuddyNextRotation");
            this.baritoneNextRotationMethod = method;
         }
         return (float[]) method.invoke(lookBehavior);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         this.baritoneRotationUnavailable = true;
         return null;
      }
   }

   private double solveMaxMultiplier(Vec3d velocity, Vec3d look, Vec3d pairLook, double threshold) {
      double margin = autoYMargin.get();
      double bestK = Double.MAX_VALUE;

      for (int axis = 0; axis < 3; axis++) {
         double dir = axisOf(look, axis);
         if (Math.abs(dir) < 1.0E-6) continue;

         double pair = axisOf(pairLook, axis);
         double min = Math.max(-threshold, (Math.min(-ANTI_TICK_SKIPPING, dir) + Math.min(-ANTI_TICK_SKIPPING, pair)) * threshold);
         double max = Math.min(threshold, (Math.max(ANTI_TICK_SKIPPING, dir) + Math.max(ANTI_TICK_SKIPPING, pair)) * threshold);

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

   /**
    * Trims a candidate multiplier down to what Grim will actually take.
    *
    * <p>{@link #solveMaxMultiplier} bounds the boosted velocity <em>before</em> the glide step,
    * against the raw firework box. That is not the comparison being made. Grim runs the same
    * fall-flying step the client does and checks the movement that comes out of it against a box
    * centred on its own unboosted prediction, widened per axis by whatever is left of the firework
    * box above the velocity it already had. The box is an expansion around a prediction, not a
    * ceiling: {@code PredictionEngine:618-628} adds {@code max(0, fireworkMax - previous)}, so an
    * axis already past 1.7 gets nothing from the firework, and one still well under it gets more
    * room than the raw box suggests.
    *
    * <p>Near cruising speed the closed form is the tighter of the two and this changes nothing.
    * Down at low speed it is the looser one, and asks for a multiplier the server will refuse -
    * which is the moment right after a takeoff, where a setback costs the most.
    *
    * <p>The result is never above the value handed in, so this can only slow the boost, never
    * speed it up. Whatever works today keeps working.
    *
    * <p>Grim's own idea of "the velocity we had" is the previous movement after it clamped it into
    * the box. While we stay inside that box the clamp is the identity and its copy matches ours,
    * which is exactly the regime we are trying to stay in; once we are outside it, the numbers here
    * drift from the server's, and that is what {@code log-trace} is for.
    */
   private double narrowToGrimBound(double candidate, Vec3d velocity, Vec3d look, Vec3d pairLook) {
      traceValid = false;
      if (mc.player == null) return candidate;

      double gravity = effectiveGravity(velocity);
      float pitch = mc.player.getPitch();
      computeGrimBounds(velocity, look, pairLook, pitch, gravity);

      // Below vanilla there is nothing left to narrow, but the bounds are computed first and kept
      // anyway. That case is the fast one - an axis already past its share of the box, which is
      // where the closed form gives up - and it is exactly the regime the trace has to record.
      if (candidate <= VANILLA_SPEED) return candidate;

      // Vanilla is the floor by contract. If even that lands outside, no multiplier saves the
      // tick and dropping below vanilla would only make the client and the server disagree more.
      if (!fitsGrimBound(VANILLA_SPEED, velocity, look, pitch, gravity)) return VANILLA_SPEED;
      if (fitsGrimBound(candidate, velocity, look, pitch, gravity)) return candidate;

      double low = VANILLA_SPEED;
      double high = candidate;

      for (int i = 0; i < GRIM_BOUND_STEPS; i++) {
         double mid = (low + high) * 0.5;

         if (fitsGrimBound(mid, velocity, look, pitch, gravity)) {
            low = mid;
         } else {
            high = mid;
         }
      }

      return low;
   }

   /**
    * Fills the per-axis bounds the server should be holding us to this tick.
    *
    * <p>Stored raw, without the safety margin: the margin is ours, not Grim's, and folding it in
    * would make the logged offsets read as violations that never happened.
    */
   private void computeGrimBounds(Vec3d velocity, Vec3d look, Vec3d pairLook, float pitch, double gravity) {
      Vec3d predicted = applyGlide(velocity, look, pitch, gravity);

      for (int axis = 0; axis < 3; axis++) {
         double dir = axisOf(look, axis);
         double pair = axisOf(pairLook, axis);
         double boxMin = Math.max(
            -GRIM_FIREWORK_SCALE,
            (Math.min(-ANTI_TICK_SKIPPING, dir) + Math.min(-ANTI_TICK_SKIPPING, pair)) * GRIM_FIREWORK_SCALE
         );
         double boxMax = Math.min(
            GRIM_FIREWORK_SCALE,
            (Math.max(ANTI_TICK_SKIPPING, dir) + Math.max(ANTI_TICK_SKIPPING, pair)) * GRIM_FIREWORK_SCALE
         );
         double had = axisOf(velocity, axis);
         double centre = axisOf(predicted, axis);

         traceLo[axis] = centre + Math.min(0.0, boxMin - had);
         traceHi[axis] = centre + Math.max(0.0, boxMax - had);
      }

      traceValid = true;
   }

   /** Whether the movement this multiplier produces lands inside the bounds, margin included. */
   private boolean fitsGrimBound(double multiplier, Vec3d velocity, Vec3d look, float pitch, double gravity) {
      double margin = autoYMargin.get();
      Vec3d boosted = velocity.multiply(0.5).add(look.multiply(0.1 + 0.5 * multiplier));
      Vec3d moved = applyGlide(boosted, look, pitch, gravity);

      for (int axis = 0; axis < 3; axis++) {
         double low = traceLo[axis] + margin;
         double high = traceHi[axis] - margin;

         // A margin wider than the box itself would veto every multiplier including vanilla.
         // Leave the axis unjudged rather than let the safety margin become the constraint.
         if (high < low) continue;

         double value = axisOf(moved, axis);

         if (value < low || value > high) {
            return false;
         }
      }

      return true;
   }

   /**
    * Vanilla's effective gravity, which {@code getFinalGravity} is not.
    *
    * <p>{@code LivingEntity#getEffectiveGravity} caps gravity at 0.01 while slow falling is up and
    * you are on the way down, and it is protected, so the condition is mirrored here rather than
    * reached. Grim applies the same cap, so leaving it out would put the two predictions apart by
    * an order of magnitude on the one axis that binds in a dive.
    */
   private double effectiveGravity(Vec3d velocity) {
      double gravity = mc.player.getFinalGravity();

      if (velocity.y <= 0.0 && mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
         return Math.min(gravity, 0.01);
      }

      return gravity;
   }

   /**
    * Writes one line per boosted tick: what we asked for, and what the server made of the last one.
    *
    * <p>The velocity read here is the movement Grim is judging right now - the client has already
    * run its glide step and the rocket has not touched it yet - so measuring it against the bounds
    * computed on the previous tick gives the offset Grim should have seen. If those offsets stay at
    * zero and the server still sets us back, the model is wrong about 2b2t. If they run above 0.001
    * and nothing ever happens, 2b2t is looser than the defaults say.
    */
   private void recordTrace(Vec3d velocity, Vec3d look) {
      if (mc.player == null) return;

      // The constant this module replaces appears three times in the same vanilla expression and
      // the injection has no ordinal, so getSpeed is reached three times a tick. Key on the tick.
      if (mc.player.age == lastTraceAge) return;

      // Bounds are only worth comparing against the tick that immediately follows them. Across a
      // gap - a boost that ended, a rocket that ran out - the stored ones describe a different
      // flight, and measuring a fresh velocity against them invents an offset nobody ever had.
      if (mc.player.age != lastTraceAge + 1) {
         tracePrevValid = false;
      }

      lastTraceAge = mc.player.age;

      double offset = 0.0;

      if (tracePrevValid) {
         for (int axis = 0; axis < 3; axis++) {
            double value = axisOf(velocity, axis);
            double over = value > tracePrevHi[axis]
               ? value - tracePrevHi[axis]
               : (value < tracePrevLo[axis] ? value - tracePrevLo[axis] : 0.0);
            offset += over * over;
         }

         offset = Math.sqrt(offset);
      }

      writeTraceLine(String.format(
         Locale.ROOT,
         "%d,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.2f,%.2f,%.4f,%.4f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%d%n",
         mc.player.age,
         velocity.x, velocity.y, velocity.z,
         look.x, look.y, look.z,
         mc.player.getYaw(), mc.player.getPitch(),
         traceClosed, traceFinal, offset,
         traceLo[0], traceHi[0], traceLo[1], traceHi[1], traceLo[2], traceHi[2],
         pivotNow ? 1 : 0
      ));

      if (traceValid) {
         System.arraycopy(traceLo, 0, tracePrevLo, 0, 3);
         System.arraycopy(traceHi, 0, tracePrevHi, 0, 3);
      }

      tracePrevValid = traceValid;
   }

   /** Records a line for a tick the manual multiplier drove. The solver never runs, the trace does. */
   private void traceManual(double multiplier) {
      if (mc.player == null) return;

      Vec3d velocity = mc.player.getVelocity();
      Vec3d look = mc.player.getRotationVector();
      Vec3d nextLook = computeNextLook(look);

      traceValid = false;
      computeGrimBounds(velocity, look, nextLook, mc.player.getPitch(), effectiveGravity(velocity));
      traceClosed = multiplier;
      traceFinal = multiplier;
      recordTrace(velocity, look);
   }

   private static String firstLine(File file) throws IOException {
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         return reader.readLine();
      }
   }

   private void writeTraceLine(String line) {
      try {
         if (traceWriter == null) {
            File file = new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "rocketboost-trace.csv");
            file.getParentFile().mkdirs();
            if (file.length() > 0L && !TRACE_HEADER.equals(firstLine(file))) {
               File aside = new File(file.getParentFile(), "rocketboost-trace-" + file.lastModified() + ".csv");
               if (!file.renameTo(aside)) throw new IOException("could not move the old trace aside to " + aside);
            }
            boolean fresh = !file.exists() || file.length() == 0L;
            traceWriter = new BufferedWriter(new FileWriter(file, true));

            if (fresh) {
               traceWriter.write(TRACE_HEADER + System.lineSeparator());
            }
         }

         traceWriter.write(line);
         tracePending++;

         if (tracePending >= 20) {
            traceWriter.flush();
            tracePending = 0;
         }
      } catch (IOException e) {
         HunterBuddyAddon.LOG.error("RocketBoost: could not write the auto-speed trace", e);
         closeTrace();
      }
   }

   private void closeTrace() {
      if (traceWriter == null) return;

      try {
         traceWriter.flush();
         traceWriter.close();
      } catch (IOException e) {
         HunterBuddyAddon.LOG.error("RocketBoost: could not close the auto-speed trace", e);
      }

      traceWriter = null;
      tracePending = 0;
      tracePrevValid = false;
      lastTraceAge = -1;
   }

   /**
    * Vanilla's fall-flying step, ported from {@code LivingEntity#updateFallFlyingMovement}.
    *
    * <p>Grim replays this same step to predict where a gliding player ends up, so it is the
    * difference between the velocity we hand the firework and the movement the server judges.
    * Kept in the order the original writes it, down to reading the horizontal speed before
    * gravity is added, because the climb term feeds on that earlier value.
    */
   private static Vec3d applyGlide(Vec3d velocity, Vec3d look, float pitch, double gravity) {
      // Kept as a float, and the sine taken from MathHelper's table rather than Math: vanilla does
      // both, and the table differs from the real sine by enough to matter on the vertical axis.
      float pitchRad = pitch * 0.017453292F;
      double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
      double speedHorizontal = velocity.horizontalLength();
      double cosSquared = MathHelper.square(Math.cos(pitchRad));

      double x = velocity.x;
      double y = velocity.y + gravity * (-1.0 + cosSquared * 0.75);
      double z = velocity.z;

      if (y < 0.0 && lookHorizontal > 0.0) {
         double fall = y * -0.1 * cosSquared;
         x += look.x * fall / lookHorizontal;
         y += fall;
         z += look.z * fall / lookHorizontal;
      }

      if (pitchRad < 0.0F && lookHorizontal > 0.0) {
         double climb = speedHorizontal * -MathHelper.sin(pitchRad) * 0.04;
         x += -look.x * climb / lookHorizontal;
         y += climb * 3.2;
         z += -look.z * climb / lookHorizontal;
      }

      if (lookHorizontal > 0.0) {
         x += (look.x / lookHorizontal * speedHorizontal - x) * 0.1;
         z += (look.z / lookHorizontal * speedHorizontal - z) * 0.1;
      }

      return new Vec3d(x * 0.99, y * 0.98, z * 0.99);
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
   public void onActivate() {
      tellBaritoneOurBoost();
   }

   @Override
   public void onDeactivate() {
      flushAndStop();
      lastGainedMs = -1;
      hasLastRotation = false;
      lastAutoSpeed = Double.NaN;
      lastAppliedSpeed = Double.NaN;
      autoRecovery = 1.0;
      traceValid = false;
      closeTrace();
      restoreBaritoneBoost();
      baritoneSyncAnnounced = false;
   }

   /** Fork-only Baritone settings, and the last value pushed to each, so a push only happens on a change. */
   private final java.util.Map<String, Object> baritoneSettings = new java.util.HashMap<>();
   private final java.util.Map<String, Object> baritonePushed = new java.util.HashMap<>();
   private java.lang.reflect.Field baritoneSettingValue;
   private boolean baritoneUnavailable;
   private boolean baritoneSyncAnnounced;
   private java.lang.reflect.Method baritoneNextRotationMethod;
   private boolean baritoneRotationUnavailable;

   /**
    * Keeps Baritone's flight simulation in step with how the boost actually behaves.
    *
    * <p>Baritone picks its pitch by simulating the boosted trajectory forward and raytracing it
    * against the terrain, and it gets two things from vanilla: the acceleration per boosted tick
    * (1.5) and how many boosted ticks are left (the rocket's age against its lifetime). This module
    * changes both — the mixin replaces the constant, and cancelling the rocket's removal keeps it
    * pushing past the lifetime the server gave it. Left unsaid, Baritone clears a trajectory that
    * is not the one being flown, and its own "hbonk" is the result: something the simulation
    * didn't know about.
    *
    * <p>The multiplier pushed is the one actually in force, not a bound on it. There is no safe side
    * to round to: simulating faster than we fly clears a wall ahead but flies over ground the real,
    * slower trajectory drops into, and simulating slower does the reverse. In manual mode the slider
    * is exact; under auto-speed it is whatever the mixin last handed the rocket. The extra ticks are
    * exact by construction — the extension is held on a wall clock from the moment the rocket would
    * have died, so it is {@code boost-duration} in ticks.
    */
   private void tellBaritoneOurBoost() {
      if (!baritoneSync.get()) {
         restoreBaritoneBoost();
         return;
      }
      // What the mixin last handed the rocket, on every path through getSpeed - the throttle included.
      // Reading the slider instead would be wrong in both modes and for two different reasons: under
      // auto-speed it is not what the solver decided, and in manual mode it is not what wall-check let
      // through. Before the first rocket of a flight there is nothing to report yet, and the slider is
      // the right stand-in, being what getSpeed itself falls back to.
      final double inForce = Double.isNaN(lastAppliedSpeed) ? speedMultiplier.get() : lastAppliedSpeed;
      pushBaritoneSetting("elytraFireworkBoostMultiplier", inForce);
      pushBaritoneSetting("elytraFireworkExtraBoostTicks", boostDuration.get() / 50);

      // Say once, in chat, that it actually took. #set will show these too, but only if you go looking,
      // and only for as long as the module keeps writing them.
      if (!this.baritoneSyncAnnounced && !this.baritoneUnavailable && !this.baritonePushed.isEmpty()) {
         this.baritoneSyncAnnounced = true;
         info("baritone-sync: telling Baritone x%.2f and +%d boost ticks",
            (double) (Double) this.baritonePushed.get("elytraFireworkBoostMultiplier"),
            (Integer) this.baritonePushed.get("elytraFireworkExtraBoostTicks"));
      }
   }

   /**
    * What the debug line says about the sync: what Baritone has been told, or why it has not been. The
    * two settings can be read back with {@code #set} as well, but on the action bar it is in front of you
    * while it matters, and it distinguishes "off" from "the loaded Baritone has no such setting".
    */
   private String baritoneSyncStatus() {
      if (!baritoneSync.get()) return " §8| sync §7off";
      if (baritoneUnavailable) return " §8| sync §cno fork";
      Object mult = baritonePushed.get("elytraFireworkBoostMultiplier");
      Object extra = baritonePushed.get("elytraFireworkExtraBoostTicks");
      if (mult == null || extra == null) return " §8| sync §7—";
      return String.format(" §8| sync §ax%.2f +%dt", (Double) mult, (Integer) extra);
   }

   /**
    * Puts the two Baritone settings back to vanilla on every join, unless this module is on and about to
    * set them itself.
    *
    * <p>They are ordinary, visible settings, so Baritone writes them to its settings file like any other.
    * That is what makes them readable when something looks wrong - but it also means a client that crashed
    * or was killed mid-flight leaves the last value behind, and the next session would plan for a boost
    * nothing is applying. Clearing them at the door costs nothing and closes that, while still letting the
    * value be set by hand afterwards for a deliberate test.
    */
   public static final class Hooks {
      @EventHandler
      private void onGameJoined(meteordevelopment.meteorclient.events.game.GameJoinedEvent event) {
         RocketBoost rb = Modules.get().get(RocketBoost.class);
         if (rb == null || rb.isActive()) return;
         rb.baritonePushed.clear();
         rb.restoreBaritoneBoost();
      }
   }

   /** Hands Baritone back a vanilla boost. */
   private void restoreBaritoneBoost() {
      pushBaritoneSetting("elytraFireworkBoostMultiplier", VANILLA_SPEED);
      pushBaritoneSetting("elytraFireworkExtraBoostTicks", 0);
   }

   /**
    * Writes one fork-only Baritone setting by reflection. The addon compiles against upstream Baritone,
    * which has neither of these, so a direct reference would not build and a player on an upstream jar
    * would fail on the field. Same arrangement as TrailFollower's corridor.
    */
   private void pushBaritoneSetting(String name, Object value) {
      if (this.baritoneUnavailable || value.equals(this.baritonePushed.get(name))) return;
      try {
         Object setting = this.baritoneSettings.get(name);
         if (setting == null) {
            Object settings = baritone.api.BaritoneAPI.getSettings();
            setting = settings.getClass().getField(name).get(settings);
            this.baritoneSettings.put(name, setting);
            if (this.baritoneSettingValue == null) {
               this.baritoneSettingValue = setting.getClass().getField("value");
            }
         }
         this.baritoneSettingValue.set(setting, value);
         this.baritonePushed.put(name, value);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         // Say so, once, and stop trying. Silence here means Baritone keeps raytracing a vanilla
         // trajectory while we fly a stronger one, which looks exactly like Baritone flying into walls
         // for no reason.
         this.baritoneUnavailable = true;
         info("baritone: fork setting " + name + " not found - it will plan for a vanilla boost while "
            + "this module makes yours stronger");
      }
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

   /**
    * Whether a teleport actually moved us, rather than agreeing with where we already are.
    *
    * <p>The server sends this same packet for plain position resyncs, which agree with where you
    * already are, and each one used to cost sixty ticks back at vanilla speed plus a flushed
    * boost. Those are what this filters. A portal or a dimension change moves you far and still
    * reads as a setback - that is not fixed here, and a real setback in flight moves you further
    * than a tick of gliding, so it is never missed. Axes flagged relative carry a delta rather
    * than a destination, so they are read as one.
    */
   private boolean isSetbackTeleport(PlayerPositionLookS2CPacket packet) {
      if (mc.player == null) return true;

      EntityPosition change = packet.change();
      Set<PositionFlag> relatives = packet.relatives();
      Vec3d target = change.position();
      Vec3d current = mc.player.getEntityPos();

      double dx = relatives.contains(PositionFlag.X) ? target.x : target.x - current.x;
      double dy = relatives.contains(PositionFlag.Y) ? target.y : target.y - current.y;
      double dz = relatives.contains(PositionFlag.Z) ? target.z : target.z - current.z;

      return dx * dx + dy * dy + dz * dz > SETBACK_MIN_DISTANCE_SQ;
   }

   /** True while one of our rockets is pushing us, whether or not we are extending it. */
   private boolean isUnderBoost() {
      return mc.player != null && mc.player.isGliding() && (boosting || FireworkBoostTracker.isBoosting(200));
   }

   @EventHandler
   private void onTickJitter(TickEvent.Pre event) {
      if (!yawJitter.get() || !isUnderBoost()) return;

      // Grim unions this tick's look vector with the last one when it builds the box, but each
      // side of that union sits on a 0.05 floor, and a hundredth of a degree of yaw is worth
      // 0.0002 on an axis. The box comes out identical. Left in place, and left off.
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

      boolean setback = event.packet instanceof PlayerPositionLookS2CPacket teleport && isSetbackTeleport(teleport);

      // A setback can land outside a boost window too, and it means the same thing either
      // way: Grim rejected our last movement, so stop asking for the solved multiplier.
      if (autoSpeed.get() && setback) {
         autoRecovery = 0.0;
      }

      if (!boosting) return;
      if (setbackDetector.get() && setback) {
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

   /**
    * Gives up on a rocket whose destroy packet is never coming.
    *
    * <p>This test used to live inside the debug handler, behind the debug toggle and behind a five
    * second gate on the last event - and with max-track-time defaulting to five seconds too, it
    * could not fire. A rocket that leaves render distance is never destroyed for us, and without
    * this the boost stays held open with the pongs queued behind it.
    */
   @EventHandler
   private void onTickWatchdog(TickEvent.Post event) {
      if (mc.player == null) return;

      // the sliders can move while the module is on
      tellBaritoneOurBoost();

      if (boosting && boostStartMs == -1 && trackStartMs != -1
         && System.currentTimeMillis() - trackStartMs >= maxTrackTime.get()) {
         flushAndStop();
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

      if (!"speed".equals(this.limiter)) {
         msg += " §8| §c" + this.limiter;
      }
      msg += baritoneSyncStatus();

      mc.player.sendMessage(net.minecraft.text.Text.literal(msg), true);
   }
}
