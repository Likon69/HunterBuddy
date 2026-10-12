package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.util.PlanSlot;
import com.hunterbuddy.util.BounceProbe;
import com.hunterbuddy.util.HopPlanner;
import com.hunterbuddy.util.SwapWindow;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

public class ElytraBounce extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgObstaclePasser = this.settings.createGroup("Obstacle Passer");
   private final Setting<Boolean> bounce = this.sgGeneral
      .add(new Builder().name("bounce").description("Automatically does bounce efly.").defaultValue(true).build());
   private final Setting<Boolean> motionYBoost = this.sgGeneral
      .add(
         new Builder().name("motion-y-boost").description("Greatly increases speed by cancelling Y momentum.")
                  .defaultValue(false)
               .visible(this.bounce::get)
            .build()
      );
   private final Setting<Boolean> onlyWhileColliding = this.sgGeneral
      .add(
         new Builder().name("only-while-colliding")
                     .description("Only enables motion y boost if colliding with a wall.")
                  .defaultValue(true)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.motionYBoost.get())
               .build()
      );
   private final Setting<Boolean> tunnelBounce = this.sgGeneral
      .add(
         new Builder().name("tunnel-bounce")
                     .description("Allows you to bounce in 1x2 tunnels. This should not be on if you are not in a tunnel.")
                  .defaultValue(false)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.motionYBoost.get())
               .build()
      );
   private final Setting<Boolean> fakeLag = this.sgGeneral
      .add(
         new Builder().name("fake-lag")
                     .description(
                        "Emulates lag by holding packets while skimming the tunnel floor, then releasing them in bursts (Lambda-style). Required for 1x2 tunnel bounce."
                     ).defaultValue(true)
               .onChanged(v -> {
                  if (!v) {
                     this.flushPackets();
                  }
               })
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.motionYBoost.get() && (Boolean)this.tunnelBounce.get())
               .build()
      );
   private final Setting<Integer> fakeLagFlushTicks = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("fake-lag-flush-ticks")
                     .description("How many ticks to hold packets before releasing them in a burst.")
                  .defaultValue(10)
               .min(2)
               .sliderRange(2, 40)
               .visible(
                  () -> (Boolean)this.bounce.get() && (Boolean)this.motionYBoost.get() && (Boolean)this.tunnelBounce.get() && (Boolean)this.fakeLag.get()
               )
               .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("speed")
                  .description("The speed in blocks per second to keep you at.")
               .defaultValue(100.0)
               .sliderRange(20.0, 250.0)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.motionYBoost.get())
               .build()
      );
   private final Setting<Boolean> onlyOnDiagonal = this.sgGeneral
      .add(
         new Builder().name("only-on-diagonal")
                     .description(
                        "Only cancel Y momentum when flying on a non-axial (diagonal) heading. Cancelling Y on axis-aligned headings scrubs speed instead of adding it."
                     ).defaultValue(true)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.motionYBoost.get())
               .build()
      );
   private final Setting<Double> minDiagonalAngle = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("min-diagonal-angle")
                  .description("Minimum angle away from an axis (0/90/180/270) before a heading counts as diagonal for motion-y-boost.")
               .defaultValue(15.0)
               .min(0.0)
               .sliderRange(0.0, 45.0)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.motionYBoost.get() && (Boolean)this.onlyOnDiagonal.get())
               .build()
      );
   private final Setting<Boolean> lockPitch = this.sgGeneral
      .add(
         new Builder().name("lock-pitch").description("Whether to lock your pitch when bounce is enabled.")
                  .defaultValue(true)
               .visible(this.bounce::get)
            .build()
      );
   private final Setting<Boolean> solvePitch = this.sgGeneral
      .add(
         new Builder().name("solve-pitch")
                     .description(
                        "Computes the pitch that gives the highest bounce speed rather than holding a fixed angle. The result depends on the room above your head, and gains are largest under a low ceiling."
                     ).defaultValue(true)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get())
               .build()
      );
   private final Setting<Double> pitch = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("pitch")
                  .description("The pitch to set when bounce is enabled.")
               .defaultValue(90.0)
               .sliderRange(-90.0, 90.0)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get() && !(Boolean)this.solvePitch.get())
               .build()
      );
   private final Setting<Boolean> pitchPerTick = this.sgGeneral
      .add(new Builder().name("pitch-per-tick")
         .description("Allow solve-pitch to use a different angle on each tick of a hop instead of one angle for the whole hop.")
         .defaultValue(true)
         .visible(() -> this.solvePitch.get() && this.lockPitch.get())
         .build());
   private final Setting<Integer> pauseAfterSetback = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("pause-after-setback")
                  .description("How many ticks bouncing stays off once the server moves you back. With 1, a single tick is skipped and the jump and re-deploy come back on the following one. 0 turns the pause off; the client-side gliding reset happens regardless.")
               .defaultValue(1)
               .min(0)
               .sliderMax(40)
               .visible(this.bounce::get)
               .build()
      );
   private final Setting<Boolean> holdGlideClear = this.sgGeneral
      .add(
         new Builder().name("hold-glide-clear")
                     .description(
                        "Answers the server's ping packets itself and holds a glide clear until the first tick the re-deploy is free, instead of letting vanilla apply it (and answer its ping) the moment it arrives. The tick a clear lands on decides how much of the bounce it costs: full ground friction if it lands on the jump tick, air friction if it lands mid-hop, nothing if it lands exactly on the tick the next takeoff departs anyway. Off falls back to letting clears and pings through as they arrive, same as before this setting existed."
                     ).defaultValue(true)
               .visible(this.bounce::get)
               .build()
      );
   private final Setting<Integer> clearHoldCap = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("clear-hold-cap")
                  .description("Ticks a held clear waits for a free re-deploy tick before being applied anyway, whether or not one has come up. Bounded so a clear held behind an obstacle pass, a pause, or getting stuck doesn't sit in a frozen transaction indefinitely.")
               .defaultValue(10)
               .min(2)
               .sliderMax(20)
               .visible(this.holdGlideClear::get)
               .build()
      );
   private final Setting<Boolean> lockYaw = this.sgGeneral
      .add(
         new Builder().name("lock-yaw").description("Whether to lock your yaw when bounce is enabled.")
                  .defaultValue(false)
               .visible(this.bounce::get)
            .build()
      );
   private final Setting<Boolean> freePitch = this.sgGeneral
      .add(
         new Builder().name("free-pitch")
                     .description(
                        "Allows you to freely move your camera pitch without affecting bounce. Server pitch stays locked for bouncing while camera pitch is independent, letting you look ahead."
                     ).defaultValue(true)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get())
               .build()
      );
   private final Setting<Boolean> useCustomYaw = this.sgGeneral
      .add(
         new Builder().name("use-custom-yaw")
                     .description(
                        "Enable this if you want to use a yaw that isn't a factor of 45. WARNING: This effects the baritone goal for obstacle passer, use the default Rotations module if you only want a different yawlock."
                     ).defaultValue(false)
               .visible(this.bounce::get)
            .build()
      );
   private final Setting<Double> yaw = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("yaw")
                  .description(
                     "The yaw to set when bounce is enabled. This is auto set to the closest 45 deg angle to you unless Use Custom Yaw is enabled. WARNING: This effects the baritone goal for obstacle passer, use the default Rotations module if you only want a different yawlock."
                  ).defaultValue(270.0)
               .sliderRange(0.0, 359.0)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.useCustomYaw.get())
               .build()
      );
   private final Setting<Boolean> highwayObstaclePasser = this.sgObstaclePasser
      .add(
         new Builder().name("highway-obstacle-passer").description("Uses baritone to pass obstacles.")
                  .defaultValue(true)
               .visible(this.bounce::get)
            .build()
      );
   private final Setting<Boolean> awayFromStartPos = this.sgObstaclePasser
      .add(
         new Builder().name("away-from-start-position")
                     .description(
                        "If true, will go away from the start position instead of towards it. The start position is automatically set to your position when the module is activated."
                     ).defaultValue(true)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.highwayObstaclePasser.get())
               .build()
      );
   private final Setting<Double> distance = this.sgObstaclePasser
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("distance")
                  .description("The distance to set the baritone goal for path realignment.")
               .defaultValue(10.0)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.highwayObstaclePasser.get())
               .build()
      );
   private final Setting<Boolean> avoidPortalTraps = this.sgObstaclePasser
      .add(
         new Builder().name("avoid-portal-traps")
                     .description("Will attempt to detect portal traps on chunk load and avoid them.")
                  .defaultValue(false)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.highwayObstaclePasser.get())
               .build()
      );
   private final Setting<Double> portalAvoidDistance = this.sgObstaclePasser
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("portal-avoid-distance")
                  .description("The distance to a portal trap where the obstacle passer will takeover and go around it.")
               .defaultValue(20.0)
               .min(0.0)
               .sliderMax(50.0)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.highwayObstaclePasser.get() && (Boolean)this.avoidPortalTraps.get())
               .build()
      );
   private final Setting<Integer> portalScanWidth = this.sgObstaclePasser
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("portal-scan-width")
                     .description("The width on the axis of the highway that will be scanned for portal traps.")
                  .defaultValue(5)
               .min(3)
               .sliderMax(10)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.highwayObstaclePasser.get() && (Boolean)this.avoidPortalTraps.get())
               .build()
      );
   private final Setting<Boolean> logDurability = this.sgGeneral
      .add(
         new Builder().name("log-flight")
                  .description("Writes to the game log: one line every time the elytra's durability actually moves, and one every five seconds of gliding with the fastest speed reached against what the solver predicted. Nothing new to open, it goes into latest.log with the rest.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> fakeFly = this.sgGeneral
      .add(
         new Builder().name("chestplate-fakefly")
                  .description("Bounces with a chest piece worn and the glider in a hand: each re-deploy swaps the glider in, sends the glide start and swaps the chest piece back inside one tick, so the glider is never worn across a landing and never loses durability. Needs the pair — a glider and a chest piece, one worn and the other in either hand; stops and says why once when it's missing, and resumes on its own once it's back.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> toggleElytra = this.sgGeneral
      .add(
         new Builder().name("toggle-elytra")
                     .description("Equips an elytra on activate, and a chestplate on deactivate.")
                  .defaultValue(false)
               .visible(() -> !(Boolean)this.fakeFly.get())
               .build()
      );
   private int lastDurability = -1;
   private double peakSpeed = 0.0;
   private int speedWindowTicks = 0;
   private boolean startSprinting;
   private BlockPos portalTrap = null;
   private boolean paused = false;
   private volatile int rubberBandCooldown = 0;

   private int airTicks;
   private volatile HopPlanner.Route activeRoute;
   private final PlanSlot<RouteKey, HopPlanner.Route> routeSlot = new PlanSlot<>();
   private volatile RouteKey routeKey;

   private double measuredRoom = -1.0;

   private double expectedSpeed = 0.0;

   private int roomCheckDelay = 0;

   private final java.util.Queue<net.minecraft.network.packet.s2c.common.CommonPingS2CPacket> glideClearPingQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

   private final java.util.Queue<net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket> heldClears = new java.util.concurrent.ConcurrentLinkedQueue<>();

   private volatile long heldSince = 0L;

   private int jumpTick = 0;
   private boolean wasOnGroundLastTick = true;

   private boolean jumpHeldLastTick = false;

   private boolean forcePressThisTick = false;

   private Hand fakeFlyBurstHand = null;
   private int fakeFlyPendingSequence = -1;
   private long fakeFlyAckedAt = -1L;
   private boolean fakeFlyDesynced = false;
   private boolean fakeFlyArmedForSend = false;
   private String fakeFlyBlockedReason = null;
   private boolean fakeFlyPairReady = true;
   private volatile boolean fakeFlyLastSentOnGround = true;
   private volatile boolean fakeFlyPrevSentOnGround = true;
   private volatile long fakeFlyLastStartAge = Long.MIN_VALUE / 2;

   private volatile int preTick;
   private final ArrayDeque<Integer> heldPongs = new ArrayDeque<>();
   private SwapWindow pendingSwap;
   private final AtomicInteger teleports = new AtomicInteger();
   private final AtomicInteger grimSetbacks = new AtomicInteger();
   private final AtomicInteger clearsPlaced = new AtomicInteger();
   private final AtomicInteger clearsLetGo = new AtomicInteger();

   private boolean jumpHeld = false;

   private volatile boolean realGliding = false;

   private final java.util.Queue<net.minecraft.network.packet.Packet<?>> sendQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
   private final java.util.Queue<net.minecraft.network.packet.s2c.common.CommonPingS2CPacket> pingQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

   private volatile boolean flushingPackets = false;
   private int fakeLagTicks = 0;
   private double lastGroundY;
   private boolean elytraToggled = false;
   private Vec3d lastUnstuckPos;
   private int stuckTimer = 0;
   private int targetY = 120;
   private BlockPos startPos = new BlockPos(0, 0, 0);
   public float cameraPitch;
   private Vec3d lastPos;
   private final double maxDistance = 80.0;
   private BlockPos tempPath = null;
   private boolean waitingForChunksToLoad;

   public ElytraBounce() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "ElytraBounce", "Elytra fly with some more features.");
   }

   @EventHandler
   private void onReceivePacket(Receive event) {
      this.probeReceive(event.packet);
      if (event.packet instanceof InventoryS2CPacket inventory && inventory.syncId() == 0) {
         this.fakeFlyPendingSequence = -1;
         this.fakeFlyAckedAt = -1L;
         this.fakeFlyDesynced = false;
      }
      if (this.fakeFlyPendingSequence >= 0
         && this.mc.player != null
         && event.packet instanceof PlayerActionResponseS2CPacket ack
         && ack.sequence() >= this.fakeFlyPendingSequence) {
         this.fakeFlyAckedAt = this.mc.player.age;
      }
      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.teleports.incrementAndGet();
         if (this.enabled() && this.bounce.get() && this.realGliding) {
            this.grimSetbacks.incrementAndGet();
         }
         this.releaseHeldGlideState();
         this.rubberBandCooldown = this.pauseAfterSetback.get();
         this.realGliding = false;
         if (this.mc.player != null) this.mc.player.stopGliding();
      } else if (event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
         || event.packet instanceof net.minecraft.network.packet.s2c.common.DisconnectS2CPacket) {
         this.releaseHeldGlideState();
         this.routeSlot.invalidate();
         this.activeRoute = null;
         this.routeKey = null;
         this.realGliding = false;
      } else if (event.packet instanceof net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket velocity
         && this.mc.player != null && velocity.getEntityId() == this.mc.player.getId()) {
         this.releaseHeldGlideState();
      } else if (event.packet instanceof net.minecraft.network.packet.s2c.play.ExplosionS2CPacket explosion
         && explosion.playerKnockback().isPresent()) {
         this.releaseHeldGlideState();
      } else if (this.handleGlideClearPacket(event)) {
         return;
      } else if (event.packet instanceof net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping && this.shouldQueuePackets()) {
         this.pingQueue.add(ping);
         event.cancel();
      } else if (event.packet instanceof CloseScreenS2CPacket) {
         event.cancel();
      }
   }

   @EventHandler
   private void onSendPacket(meteordevelopment.meteorclient.events.packets.PacketEvent.Send event) {
      this.probeSend(event.packet);
      if (this.fakeFlyArmedForSend && event.packet instanceof PlayerInteractItemC2SPacket interact) {
         this.fakeFlyPendingSequence = interact.getSequence();
         this.fakeFlyAckedAt = -1L;
      }
      if (event.packet instanceof net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket move) {
         this.fakeFlyPrevSentOnGround = this.fakeFlyLastSentOnGround;
         this.fakeFlyLastSentOnGround = move.isOnGround();
      }
      if (this.mc.player != null
         && event.packet instanceof ClientCommandC2SPacket command
         && command.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
         this.fakeFlyLastStartAge = this.mc.player.age;
      }
      if (this.flushingPackets) return;
      if (this.shouldQueuePackets()) {
         this.sendQueue.add(event.packet);
         event.cancel();
      } else {
         this.flushPackets();
      }
   }

   private boolean shouldQueuePackets() {
      return (Boolean)this.bounce.get()
         && (Boolean)this.motionYBoost.get()
         && (Boolean)this.tunnelBounce.get()
         && (Boolean)this.fakeLag.get()
         && !(Boolean)this.fakeFly.get()
         && this.enabled()
         && this.mc.player != null
         && this.mc.player.isGliding()
         && this.mc.player.getY() - this.lastGroundY < 0.163;
   }

   private void tickFakeLag() {
      if (this.mc.player != null && this.mc.player.isOnGround()) {
         this.lastGroundY = this.mc.player.getY();
      }

      if (!this.shouldQueuePackets()) {
         this.fakeLagTicks = 0;
         this.flushPackets();
      } else if (++this.fakeLagTicks >= (Integer)this.fakeLagFlushTicks.get()) {
         this.fakeLagTicks = 0;
         this.flushPackets();
      }
   }

   private void flushPackets() {
      if (!this.sendQueue.isEmpty() || !this.pingQueue.isEmpty()) {
         if (this.mc.getNetworkHandler() == null) {
            this.sendQueue.clear();
            this.pingQueue.clear();
            return;
         }

         this.flushingPackets = true;

         try {
            net.minecraft.network.ClientConnection connection = this.mc.getNetworkHandler().getConnection();
            net.minecraft.network.packet.Packet<?> packet;
            while ((packet = this.sendQueue.poll()) != null) {
               connection.send(packet, null, true);
            }

            net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping;
            while ((ping = this.pingQueue.poll()) != null) {
               net.minecraft.network.packet.s2c.common.CommonPingS2CPacket finalPing = ping;
               if (this.mc.isOnThread()) {
                  finalPing.apply(this.mc.getNetworkHandler());
               } else {
                  this.mc.execute(() -> finalPing.apply(this.mc.getNetworkHandler()));
               }
            }
         } finally {
            this.flushingPackets = false;
         }
      }
   }

   private void watchSpeed() {
      if (!(Boolean)this.logDurability.get() || this.mc.player == null) {
         return;
      }

      if (!this.mc.player.isGliding()) {
         return;
      }

      double speed = this.mc.player.getVelocity().horizontalLength() * 20.0;
      if (speed > this.peakSpeed) {
         this.peakSpeed = speed;
      }

      if (++this.speedWindowTicks >= 100) {
         this.speedWindowTicks = 0;
         HunterBuddyAddon.LOG
            .info(
               String.format(
                  java.util.Locale.ROOT,
                  "[ElytraBounce speed] peak=%.1f b/s (%.0f km/h) predicted=%.1f b/s headroom=%.2f pitch=%.1f",
                  this.peakSpeed,
                  this.peakSpeed * 3.6,
                  this.solvePitch.get() && this.activeRoute != null && !(this.measuredRoom >= 0.0 && this.measuredRoom < 1.2)
                     ? this.expectedSpeed : 0.0,
                  this.measuredRoom,
                  this.effectivePitch()
               )
            );
         this.peakSpeed = 0.0;
      }
   }

   private void watchDurability() {
      if (!(Boolean)this.logDurability.get() || this.mc.player == null) {
         return;
      }

      ItemStack glider = ItemStack.EMPTY;
      if (isGlider(this.mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
         glider = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
      } else if (isGlider(this.mc.player.getStackInHand(Hand.MAIN_HAND))) {
         glider = this.mc.player.getStackInHand(Hand.MAIN_HAND);
      } else if (isGlider(this.mc.player.getStackInHand(Hand.OFF_HAND))) {
         glider = this.mc.player.getStackInHand(Hand.OFF_HAND);
      }

      if (glider.isEmpty()) {
         return;
      }

      int left = glider.getMaxDamage() - glider.getDamage();
      if (left == this.lastDurability) {
         return;
      }

      int lost = this.lastDurability < 0 ? 0 : this.lastDurability - left;
      this.lastDurability = left;
      HunterBuddyAddon.LOG
         .info(
            String.format(
               java.util.Locale.ROOT,
               "[ElytraBounce durability] %d/%d lost=%d worn=%b fakefly=%b gliding=%b tick=%d",
               left,
               glider.getMaxDamage(),
               lost,
               isGlider(this.mc.player.getEquippedStack(EquipmentSlot.CHEST)),
               (Boolean)this.fakeFly.get(),
               this.mc.player.isGliding(),
               this.mc.player.age
            )
         );
   }

   public void onActivate() {
      this.lastDurability = -1;
      this.peakSpeed = 0.0;
      this.speedWindowTicks = 0;
      if (this.pendingSwap != null) this.pendingSwap.cancel();
      this.pendingSwap = null;
      this.routeSlot.invalidate();
      this.activeRoute = null;
      this.routeKey = null;
      this.airTicks = 0;
      this.preTick = 0;
      synchronized (this.heldPongs) {
         this.heldPongs.clear();
      }
      this.teleports.set(0);
      this.grimSetbacks.set(0);
      this.clearsPlaced.set(0);
      this.clearsLetGo.set(0);
      if (this.mc.player != null && this.mc.world != null && !this.mc.player.getAbilities().allowFlying) {
         this.startSprinting = this.mc.player.isSprinting();
         this.tempPath = null;
         this.portalTrap = null;
         this.paused = false;
         this.rubberBandCooldown = 0;
         this.measuredRoom = -1.0;
         this.expectedSpeed = 0.0;
         this.roomCheckDelay = 0;
         this.heldClears.clear();
         this.glideClearPingQueue.clear();
         this.heldSince = 0L;
         this.jumpTick = this.mc.player.age;
         this.wasOnGroundLastTick = this.mc.player.isOnGround();
         this.jumpHeldLastTick = false;
         this.forcePressThisTick = false;
         this.fakeFlyBurstHand = null;
         this.fakeFlyPendingSequence = -1;
         this.fakeFlyAckedAt = -1L;
         this.fakeFlyDesynced = false;
         this.fakeFlyBlockedReason = null;
         this.fakeFlyPairReady = true;
         this.fakeFlyLastStartAge = Long.MIN_VALUE / 2;
         this.fakeFlyLastSentOnGround = true;
         this.fakeFlyPrevSentOnGround = true;
         this.realGliding = false;
         this.sendQueue.clear();
         this.pingQueue.clear();
         this.fakeLagTicks = 0;
         this.lastGroundY = this.mc.player.getY();
         this.waitingForChunksToLoad = false;
         this.elytraToggled = false;
         this.lastPos = this.mc.player.getEntityPos();
         this.lastUnstuckPos = this.mc.player.getEntityPos();
         this.stuckTimer = 0;
         this.cameraPitch = this.mc.player.getPitch();
         if ((Boolean)this.bounce.get() && this.mc.player.getEntityPos().multiply(1.0, 0.0, 1.0).length() >= 100.0) {
            if (!BaritoneHelper.hasElytraProcess() || BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null) {
               BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
            }

            if ((Boolean)this.highwayObstaclePasser.get()) {
               this.startPos = this.mc.player.getBlockPos();
               this.targetY = this.mc.player.getBlockY();
            } else {
               this.startPos = new BlockPos(0, 0, 0);
            }

            if (!(Boolean)this.useCustomYaw.get()) {
               if (!(this.mc.player.getBlockPos().getSquaredDistance(this.startPos) < 10000.0) && (Boolean)this.highwayObstaclePasser.get()) {
                  BlockPos directionVec = this.mc.player.getBlockPos().subtract(this.startPos);
                  double angle = Math.toDegrees(Math.atan2(-directionVec.getX(), directionVec.getZ()));
                  double angleNormalized = Utils.angleOnAxis(angle);
                  if (!(Boolean)this.awayFromStartPos.get()) {
                     angleNormalized += 180.0;
                  }

                  this.yaw.set(angleNormalized);
               } else {
                  double playerAngleNormalized = Utils.angleOnAxis(this.mc.player.getYaw());
                  this.yaw.set(playerAngleNormalized);
               }
            }
         }
      }
   }

   @EventHandler
   private void onPlayerMove(PlayerMoveEvent event) {
      if (this.mc.player != null && event.type == MovementType.SELF && this.enabled() && (Boolean)this.motionYBoost.get() && (Boolean)this.bounce.get()
         )
       {
         if (!(Boolean)this.onlyWhileColliding.get() || this.mc.player.horizontalCollision) {
            if (!(Boolean)this.onlyOnDiagonal.get() || (Boolean)this.tunnelBounce.get() || this.isDiagonalHeading()) {
               if (this.lastPos != null) {
                  double speedBps = this.mc.player.getEntityPos().subtract(this.lastPos).multiply(20.0, 0.0, 20.0).length();
                  Timer timer = (Timer)Modules.get().get(Timer.class);
                  if (timer.isActive()) {
                     speedBps *= timer.getMultiplier();
                  }

                  if (this.mc.player.isOnGround() && this.mc.player.isSprinting() && speedBps < (Double)this.speed.get()) {
                     if (speedBps > 20.0 || (Boolean)this.tunnelBounce.get()) {
                        event.movement = new Vec3d(event.movement.x, 0.0, event.movement.z);
                     }

                     this.mc.player.setVelocity(this.mc.player.getVelocity().x, 0.0, this.mc.player.getVelocity().z);
                  }
               }

               this.lastPos = this.mc.player.getEntityPos();
            }
         }
      }
   }

   public void onDeactivate() {
      BounceProbe.get().stop();
      if (this.pendingSwap != null) this.pendingSwap.cancel();
      this.pendingSwap = null;
      this.routeSlot.invalidate();
      this.activeRoute = null;
      this.routeKey = null;
      this.realGliding = false;
      this.jumpHeld = false;
      this.jumpHeldLastTick = false;
      this.fakeLagTicks = 0;
      this.releaseHeldGlideState();
      this.flushPackets();
      if (this.mc.player != null) {
         if (this.freePitch.get() && this.lockPitch.get() && this.bounce.get()) this.mc.player.setPitch(this.cameraPitch);
         if (this.bounce.get()
            && (!BaritoneHelper.hasElytraProcess() || BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null)) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
         }
         this.mc.player.setSprinting(this.startSprinting);
         if (this.toggleElytra.get()
            && !(Boolean)this.fakeFly.get()
            && !this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().toString().contains("chestplate")) {
            this.deferSwap(() -> Modules.get().get(ChestSwap.class).swap());
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      this.preTick++;
      if (this.mc.player == null || this.mc.world == null) {
         this.jumpHeld = false;
         this.routeSlot.invalidate();
         this.activeRoute = null;
         this.routeKey = null;
         this.releaseHeldGlideState();
         this.flushPackets();
         return;
      }
      this.airTicks = this.mc.player.isOnGround() ? 0 : this.airTicks + 1;
      this.watchDurability();
      this.watchSpeed();
      this.watchStats();
      this.tickFakeLag();
      if (SwapWindow.silent()) {
         this.mc.player.setSprinting(false);
         this.releaseHeldGlideState();
         return;
      }
      this.updateAutoPitch();
      this.serviceGlideClear();
      this.serviceFakeFlyBurst();
      this.updateJumpKey();
      this.probeTick();
      if (this.rubberBandCooldown > 0) {
         this.rubberBandCooldown--;
         return;
      }
      if (this.mc.player != null && !this.mc.player.getAbilities().allowFlying) {
         if ((Boolean)this.toggleElytra.get() && !(Boolean)this.fakeFly.get() && !this.elytraToggled) {
            if (!this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
               this.deferSwap(() -> Modules.get().get(ChestSwap.class).swap());
               return;
            } else {
               this.elytraToggled = true;
            }
         }

         if (this.enabled()) {

            this.mc.player.setSprinting(true);
         }

         if ((Boolean)this.bounce.get()) {
            if (this.tempPath != null && this.mc.player.getBlockPos().getSquaredDistance(this.tempPath) < 500.0) {
               this.tempPath = null;
               BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
            } else if (this.tempPath != null) {
               BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.tempPath));
               return;
            }

            if ((Boolean)this.highwayObstaclePasser.get() && BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal() != null) {
               return;
            }

            if (this.mc.player.squaredDistanceTo(this.lastUnstuckPos) < 25.0) {
               this.stuckTimer++;
            } else {
               this.stuckTimer = 0;
               this.lastUnstuckPos = this.mc.player.getEntityPos();
            }

            if ((Boolean)this.highwayObstaclePasser.get()
               && this.mc.player.getEntityPos().length() > 100.0
               && (
                  this.mc.player.getY() < this.targetY
                     || this.mc.player.getY() > this.targetY + 2
                     || this.mc.player.horizontalCollision && !this.mc.player.collidedSoftly
                     || this.portalTrap != null
                        && this.portalTrap.getSquaredDistance(this.mc.player.getBlockPos())
                           < (Double)this.portalAvoidDistance.get() * (Double)this.portalAvoidDistance.get()
                     || this.waitingForChunksToLoad
                     || this.stuckTimer > 50
               )) {
               this.waitingForChunksToLoad = false;
               this.paused = true;
               BlockPos goal = this.mc.player.getBlockPos();
               double currDistance = (Double)this.distance.get();
               if (this.portalTrap != null) {
                  currDistance += this.mc.player.getEntityPos().distanceTo(this.portalTrap.toCenterPos());
                  this.portalTrap = null;
                  this.info("Pathing around portal.", new Object[0]);
               }

               do {
                  if (currDistance > 80.0) {
                     this.tempPath = goal;
                     BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                     return;
                  }

                  Vec3d unitYawVec = Utils.yawToDirection((Double)this.yaw.get());
                  Vec3d travelVec = this.mc.player.getEntityPos().subtract(this.startPos.toCenterPos());
                  double parallelCurrPosDot = travelVec.multiply(new Vec3d(1.0, 0.0, 1.0)).dotProduct(unitYawVec);
                  Vec3d parallelCurrPosComponent = unitYawVec.multiply(parallelCurrPosDot);
                  Vec3d pos = this.startPos.toCenterPos().add(parallelCurrPosComponent);
                  pos = Utils.positionInDirection(pos, (Double)this.yaw.get(), currDistance);
                  goal = new BlockPos((int)Math.floor(pos.x), this.targetY, (int)Math.floor(pos.z));
                  currDistance++;
                  if (this.mc.world.getBlockState(goal).getBlock() == Blocks.VOID_AIR) {
                     this.waitingForChunksToLoad = true;
                     return;
                  }
               } while (
                  !this.mc.world.getBlockState(goal.down()).isSolidBlock(this.mc.world, goal.down())
                     || this.mc.world.getBlockState(goal).getBlock() == Blocks.NETHER_PORTAL
                     || !this.mc.world.getBlockState(goal).isAir()
               );

               BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
            } else {
               this.paused = false;
               if (!this.enabled()) {
                  return;
               }

               if ((Boolean)this.lockYaw.get()) {
                  this.mc.player.setYaw(((Double)this.yaw.get()).floatValue());
               }

               if ((Boolean)this.lockPitch.get()) {
                  this.mc.player.setPitch(this.effectivePitch());
               }
            }
         }

         if (this.enabled()) {
            if (this.mc.player.isOnGround()) {

               this.realGliding = false;
            }

         }
      }
   }

   private boolean isDiagonalHeading() {
      double m = Math.abs(this.mc.player.getYaw() % 90.0);
      double distToAxis = Math.min(m, 90.0 - m);
      return distToAxis > (Double)this.minDiagonalAngle.get();
   }

   private void updateAutoPitch() {
      if (SwapWindow.silent()) return;
      var player = this.mc.player;
      var world = this.mc.world;
      boolean wanted = this.solvePitch.get() && this.bounce.get() && this.lockPitch.get();
      if (!wanted || player == null || world == null) {
         this.routeSlot.invalidate();
         this.activeRoute = null;
         this.routeKey = null;
         return;
      }
      if (this.roomCheckDelay == 0 && player.isOnGround()) {
         this.roomCheckDelay = 10;
         this.measuredRoom = HopPlanner.headroomAbove(player, world);
         this.routeKey = new RouteKey(HopPlanner.Body.DEFAULT, this.measuredRoom, this.pitchPerTick.get());
      } else if (this.roomCheckDelay > 0) {
         this.roomCheckDelay--;
      }
      RouteKey previousKey = this.routeKey;
      if (previousKey == null) return;
      RouteKey key = new RouteKey(previousKey.body(), previousKey.room(), this.pitchPerTick.get());
      this.routeKey = key;
      this.routeSlot.update(player, world, key);
      HopPlanner.Route stored = this.routeSlot.plan(player, world, key);
      HopPlanner.Route current = this.activeRoute;
      if (stored != null) {
         this.activeRoute = stored;
      } else if (current != null && current.headroom < 1.2) {
         this.activeRoute = null;
      }
      if (this.activeRoute != null) this.expectedSpeed = this.activeRoute.speed;
      PlanSlot.Ticket<RouteKey> ticket = this.routeSlot.request();
      if (ticket == null) return;
      MeteorExecutor.execute(() -> {
         try {
            RouteKey wantedKey = ticket.key();
            HopPlanner.Route solved = HopPlanner.plan(wantedKey.room(), wantedKey.perTick(), wantedKey.body());
            this.mc.execute(() -> {
               boolean accepted = this.routeSlot.complete(ticket, solved);
               if (accepted && this.isActive() && this.mc.player == player && this.mc.world == world
                  && key.equals(this.routeKey) && !SwapWindow.silent()) {
                  this.activeRoute = solved;
                  this.expectedSpeed = solved.speed;
               }
            });
         } catch (RuntimeException e) {
            this.mc.execute(() -> {
               if (this.routeSlot.fail(ticket) && this.isActive()) this.warning("Could not solve bounce pitch: %s", e.getMessage());
            });
         }
      });
   }

   private boolean routeUsable() {
      boolean tightRoom = this.measuredRoom >= 0.0 && this.measuredRoom < 1.2;
      return this.solvePitch.get() && this.activeRoute != null && !tightRoom;
   }

   private float effectivePitch() {
      HopPlanner.Route route = this.activeRoute;
      if (route != null && this.routeUsable()) return route.pitchAt(this.airTicks);
      return this.pitch.get().floatValue();
   }

   @Override
   public String getInfoString() {
      if (!this.bounce.get() || !this.lockPitch.get()) return null;
      HopPlanner.Route route = this.activeRoute;
      return route != null && this.routeUsable()
         ? String.format(java.util.Locale.ROOT, "%.0f b/s @ %.0f-%.0f°", route.speed, route.lowestPitch(), route.highestPitch())
         : String.format(java.util.Locale.ROOT, "%.0f° fixed", this.pitch.get());
   }

   private boolean isFreeTick() {
      if (this.mc.player == null) return false;
      return !this.mc.player.isOnGround()
         && this.mc.player.age >= this.jumpTick + 1
         && !this.jumpHeldLastTick;
   }

   private void serviceGlideClear() {
      if (this.mc.player == null || !(Boolean)this.bounce.get() || !(Boolean)this.holdGlideClear.get() || !this.enabled()) {
         this.releaseHeldGlideState();
         return;
      }

      long age = this.mc.player.age;
      boolean free = this.isFreeTick();

      if (!this.heldClears.isEmpty()) {
         if (free || age - this.heldSince >= (Integer)this.clearHoldCap.get()) {
            BounceProbe.get().line("RELEASE-CLEAR", "held=" + (age - this.heldSince) + " free=" + free);
            this.releaseHeldGlideState();

            this.realGliding = this.mc.player.isGliding();
            this.forcePressThisTick = true;
         } else {
            BounceProbe.get().line("HOLD-CLEAR", "since=" + (age - this.heldSince));
         }
      } else {

         net.minecraft.client.network.ClientPlayNetworkHandler handler = this.mc.getNetworkHandler();
         if (handler != null) {
            net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping;
            while ((ping = this.glideClearPingQueue.poll()) != null) {
               this.applyPacket(handler, ping);
            }
         }
      }
   }

   private boolean handleGlideClearPacket(Receive event) {
      if (this.mc.player == null || !this.bounce.get() || !this.holdGlideClear.get()) return false;
      if (event.packet instanceof net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping) {
         synchronized (this.heldPongs) {
            if (!this.heldClears.isEmpty()) {
               this.heldPongs.addLast(ping.getParameter());
            } else {
               this.glideClearPingQueue.add(ping);
            }
         }
         event.cancel();
         return true;
      }
      if (event.packet instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket tracker
         && tracker.id() == this.mc.player.getId() && this.mc.player.isGliding()) {
         for (net.minecraft.entity.data.DataTracker.SerializedEntry<?> entry : tracker.trackedValues()) {
            if (entry.id() == 0 && entry.value() instanceof Byte flags && (flags & 0x80) == 0) {
               synchronized (this.heldPongs) {
                  if (this.heldClears.isEmpty()) this.heldSince = this.mc.player.age;
                  this.heldClears.add(tracker);
                  net.minecraft.network.packet.s2c.common.CommonPingS2CPacket queued;
                  while ((queued = this.glideClearPingQueue.poll()) != null) this.heldPongs.addLast(queued.getParameter());
               }
               this.clearsPlaced.incrementAndGet();
               event.cancel();
               return true;
            }
         }
      }
      return false;
   }

   private void releaseHeldGlideState() {
      net.minecraft.client.network.ClientPlayNetworkHandler handler = this.mc.getNetworkHandler();
      synchronized (this.heldPongs) {
         if (handler == null) {
            this.heldPongs.clear();
            this.heldClears.clear();
            this.glideClearPingQueue.clear();
            this.heldSince = 0L;
            return;
         }
         boolean wasFlushing = this.flushingPackets;
         this.flushingPackets = true;
         try {
            while (!this.heldPongs.isEmpty()) {
               int id = this.heldPongs.removeFirst();
               if (this.mc.isOnThread()) {
                  handler.sendPacket(new net.minecraft.network.packet.c2s.common.CommonPongC2SPacket(id));
               } else {
                  this.applyPacket(handler, new net.minecraft.network.packet.s2c.common.CommonPingS2CPacket(id));
               }
            }
         } finally {
            this.flushingPackets = wasFlushing;
         }
         net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping;
         while ((ping = this.glideClearPingQueue.poll()) != null) this.applyPacket(handler, ping);
         net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket clear;
         while ((clear = this.heldClears.poll()) != null) {
            this.applyPacket(handler, clear);
            this.clearsLetGo.incrementAndGet();
         }
         this.heldSince = 0L;
      }
   }

   private <T extends net.minecraft.network.listener.PacketListener> void applyPacket(T listener, net.minecraft.network.packet.Packet<T> packet) {
      if (this.mc.isOnThread()) {
         packet.apply(listener);
      } else {
         this.mc.getPacketApplyBatcher().add(listener, packet);
      }
   }

   private static boolean isGlider(ItemStack stack) {
      return !stack.isEmpty() && LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST);
   }

   private static boolean isChestPiece(ItemStack stack) {
      if (stack.isEmpty()) return false;
      EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !isGlider(stack);
   }

   private Hand fakeFlyReadyHand() {
      ItemStack worn = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
      ItemStack main = this.mc.player.getStackInHand(Hand.MAIN_HAND);
      ItemStack off = this.mc.player.getStackInHand(Hand.OFF_HAND);
      Hand handGlider = isGlider(main) ? Hand.MAIN_HAND : (isGlider(off) ? Hand.OFF_HAND : null);

      if (!isChestPiece(worn) || handGlider == null) {
         this.reportFakeFlyBlocked("chestplate-fakefly needs a glider and a chest piece, one worn and the other in a hand");
         return null;
      }

      if (EnchantmentHelper.hasAnyEnchantmentsWith(worn, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE) && !this.mc.player.isCreative()) {
         this.reportFakeFlyBlocked("chest piece has Curse of Binding");
         return null;
      }

      ItemStack glider = handGlider == Hand.MAIN_HAND ? main : off;
      if (glider.willBreakNextUse()) {
         this.reportFakeFlyBlocked("elytra is spent");
         return null;
      }

      this.fakeFlyBlockedReason = null;
      return handGlider;
   }

   private void reportFakeFlyBlocked(String reason) {
      if (reason.equals(this.fakeFlyBlockedReason)) return;
      this.fakeFlyBlockedReason = reason;
      this.info(reason, new Object[0]);
   }

   private void serviceFakeFlyBurst() {
      if (this.mc.player == null || !(Boolean)this.bounce.get() || !(Boolean)this.fakeFly.get() || !this.enabled()) {
         this.fakeFlyPendingSequence = -1;
         this.fakeFlyAckedAt = -1L;
         this.fakeFlyDesynced = false;
         this.fakeFlyBurstHand = null;
         this.fakeFlyPairReady = true;
         return;
      }

      long age = this.mc.player.age;
      boolean screenOpen = this.mc.currentScreen instanceof HandledScreen<?>;

      if (this.fakeFlyPendingSequence >= 0 && this.fakeFlyAckedAt >= 0 && age > this.fakeFlyAckedAt) {
         this.fakeFlyPendingSequence = -1;
         this.fakeFlyAckedAt = -1L;
         this.fakeFlyDesynced = true;
      }

      if (this.fakeFlyDesynced) {
         this.fakeFlyPairReady = false;
         this.reportFakeFlyBlocked("the server refused the chest swap, open your inventory to resync");
         return;
      }

      ItemStack worn = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);

      if (isGlider(worn)) {
         this.fakeFlyPairReady = true;
         this.fakeFlyBlockedReason = null;

         if (age % 2L != 0L || screenOpen) return;

         ItemStack main = this.mc.player.getStackInHand(Hand.MAIN_HAND);
         ItemStack off = this.mc.player.getStackInHand(Hand.OFF_HAND);
         Hand chestHand = isChestPiece(main) ? Hand.MAIN_HAND : (isChestPiece(off) ? Hand.OFF_HAND : null);

         if (chestHand != null) {
            this.swapFakeFlyHand(chestHand);
         } else if (this.mc.player.isOnGround()) {
            int slot = -1;
            for (int i = 0; i < 9; i++) {
               if (isChestPiece(this.mc.player.getInventory().getStack(i))) {
                  slot = i;
                  break;
               }
            }

            if (slot >= 0) {
               this.mc.player.getInventory().setSelectedSlot(slot);
               this.swapFakeFlyHand(Hand.MAIN_HAND);
            } else {
               this.reportFakeFlyBlocked("chest piece must be in the hotbar");
            }
         }

         return;
      }

      Hand gliderHand = this.fakeFlyReadyHand();
      this.fakeFlyPairReady = gliderHand != null;
      if (gliderHand == null) return;

      if (!this.isFreeTick() || this.mc.player.isGliding()) return;

      if (!this.fakeFlyPrevSentOnGround && !this.fakeFlyLastSentOnGround) {
         if (screenOpen) {
            BounceProbe.get().line("FAKEFLY-WAIT", "reason=screen");
            return;
         }

         if (age - this.fakeFlyLastStartAge < 2L) {
            BounceProbe.get().line("FAKEFLY-WAIT", "reason=start");
            return;
         }

         this.swapFakeFlyHand(gliderHand);
         this.fakeFlyBurstHand = gliderHand;
         this.forcePressThisTick = true;
         BounceProbe.get().line("FAKEFLY-SWAP-IN", "hand=" + gliderHand);
      } else {
         BounceProbe.get().line("FAKEFLY-WAIT", "reason=ground");
      }
   }

   private void swapFakeFlyHand(Hand hand) {
      this.fakeFlyArmedForSend = true;

      try {
         this.mc.interactionManager.interactItem(this.mc.player, hand);
      } finally {
         this.fakeFlyArmedForSend = false;
      }
   }

   private void deferSwap(Runnable action) {
      var player = this.mc.player;
      var world = this.mc.world;
      if (player == null || world == null) return;
      if (this.pendingSwap != null && !this.pendingSwap.done()) return;
      this.jumpHeld = false;
      this.jumpHeldLastTick = false;
      this.forcePressThisTick = false;
      this.pendingSwap = SwapWindow.after(2, true, () -> {
         if (this.mc.player == player && this.mc.world == world) action.run();
      });
   }

   private void watchStats() {
      if (!this.logDurability.get() || this.preTick % 400 != 0) return;
      HunterBuddyAddon.LOG.info(String.format(java.util.Locale.ROOT,
         "[ElytraBounce stats] teleports=%d grimSetbacks=%d clearsPlaced=%d clearsLetGo=%d",
         this.teleports.get(), this.grimSetbacks.get(),
         this.clearsPlaced.get(), this.clearsLetGo.get()));
   }

   @EventHandler
   private void onBeforeSendPosition(SendMovementPacketsEvent.Pre event) {
      if (this.mc.player == null) return;
      if (this.fakeFlyBurstHand != null) {
         Hand hand = this.fakeFlyBurstHand;
         this.fakeFlyBurstHand = null;
         this.swapFakeFlyHand(hand);
         BounceProbe.get().line("FAKEFLY-SWAP-OUT", "hand=" + hand);
      }
      if (SwapWindow.silent()) this.mc.player.setSprinting(false);
   }

   public boolean enabled() {
      return this.isActive() && !this.paused && !SwapWindow.silent() && this.rubberBandCooldown <= 0
         && this.mc.player != null && this.mc.world != null
         && (this.fakeFly.get() || this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA));
   }

   public boolean modifyIsGliding(boolean original) {
      this.realGliding = original;
      return original;
   }

   public boolean isFreePitchEnabled() {
      return this.enabled() && (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get() && (Boolean)this.freePitch.get();
   }

   private void updateJumpKey() {
      if (SwapWindow.silent()) return;
      var player = this.mc.player;
      boolean onGround = player != null && player.isOnGround();
      if (player != null && this.wasOnGroundLastTick && !onGround) this.jumpTick = player.age;
      this.wasOnGroundLastTick = onGround;
      boolean flipped = !this.jumpHeld;
      boolean decision;
      if (!this.enabled() || !this.shouldAutoJump()) {
         decision = false;
      } else if (onGround) {
         boolean fastEnough = this.motionYBoost.get()
            && meteordevelopment.meteorclient.utils.Utils.getPlayerSpeed().multiply(1.0, 0.0, 1.0).length() >= this.speed.get();
         decision = !fastEnough;
      } else if (this.forcePressThisTick) {
         decision = flipped;
      } else if (player.isGliding()) {
         decision = false;
      } else {
         decision = flipped;
      }
      this.forcePressThisTick = false;
      this.jumpHeld = decision;
      this.jumpHeldLastTick = decision;
   }

   private void probeTick() {
      BounceProbe probe = BounceProbe.get();
      if (!probe.isRecording() || this.mc.player == null) return;

      probe.nextTick();
      probe.line(
         "TICK",
         String.format(
            java.util.Locale.ROOT,
            "onGround=%b flag7=%b key=%b sprint=%b pos=%.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f pitch=%.1f yaw=%.1f",
            this.mc.player.isOnGround(),
            this.mc.player.isGliding(),
            this.jumpHeld,
            this.mc.player.isSprinting(),
            this.mc.player.getX(),
            this.mc.player.getY(),
            this.mc.player.getZ(),
            this.mc.player.getVelocity().x,
            this.mc.player.getVelocity().y,
            this.mc.player.getVelocity().z,
            this.mc.player.getPitch(),
            this.mc.player.getYaw()
         )
      );
   }

   private void probeSend(net.minecraft.network.packet.Packet<?> packet) {
      BounceProbe probe = BounceProbe.get();
      if (!probe.isRecording()) return;

      if (packet instanceof ClientCommandC2SPacket command) {
         probe.line("OUT-CMD", command.getMode().toString());
      } else if (packet instanceof net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket move) {
         probe.line(
            "OUT-MOVE",
            String.format(
               java.util.Locale.ROOT,
               "onGround=%b y=%.3f",
               move.isOnGround(),
               move.getY(this.mc.player == null ? 0.0 : this.mc.player.getY())
            )
         );
      } else if (packet instanceof net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket input) {
         probe.line("OUT-INPUT", "jump=" + input.input().jump());
      } else if (packet instanceof net.minecraft.network.packet.c2s.common.CommonPongC2SPacket pong) {
         probe.line("OUT-PONG", Integer.toString(pong.getParameter()));
      }
   }

   private void probeReceive(net.minecraft.network.packet.Packet<?> packet) {
      BounceProbe probe = BounceProbe.get();
      if (!probe.isRecording()) return;

      if (packet instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket tracker) {
         if (this.mc.player == null || tracker.id() != this.mc.player.getId()) return;

         for (net.minecraft.entity.data.DataTracker.SerializedEntry<?> entry : tracker.trackedValues()) {
            if (entry.id() == 0 && entry.value() instanceof Byte flags) {
               probe.line("IN-META", "glide=" + ((flags & 0x80) != 0));
            }
         }
      } else if (packet instanceof PlayerPositionLookS2CPacket look) {
         probe.line(
            "IN-TP",
            String.format(
               java.util.Locale.ROOT,
               "x=%.2f y=%.2f z=%.2f",
               look.change().position().x,
               look.change().position().y,
               look.change().position().z
            )
         );
      } else if (packet instanceof net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping) {
         probe.line("IN-PING", Integer.toString(ping.getParameter()));
      }
   }

   public Boolean keyOverride(String keyId) {
      if ("key.forward".equals(keyId)) return Boolean.TRUE;
      if ("key.jump".equals(keyId) && this.shouldAutoJump()) return this.jumpHeld;
      return null;
   }

   public boolean shouldAutoJump() {
      return this.bounce.get() && (!this.fakeFly.get() || this.fakeFlyPairReady);
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      if ((Boolean)this.avoidPortalTraps.get() && (Boolean)this.highwayObstaclePasser.get()) {
         ChunkPos pos = event.chunk().getPos();
         BlockPos centerPos = pos.getCenterAtY(this.targetY);
         Vec3d moveDir = Utils.yawToDirection((Double)this.yaw.get());
         double distanceToHighway = Utils.distancePointToDirection(Vec3d.of(centerPos), moveDir, this.mc.player.getEntityPos());
         if (!(distanceToHighway > 21.0)) {
            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  for (int y = this.targetY; y < this.targetY + 3; y++) {
                     BlockPos position = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);
                     if (!(
                           Utils.distancePointToDirection(Vec3d.of(position), moveDir, this.mc.player.getEntityPos())
                              > ((Integer)this.portalScanWidth.get()).intValue()
                        )
                        && this.mc.world.getBlockState(position).getBlock().equals(Blocks.NETHER_PORTAL)) {
                        BlockPos posBehind = new BlockPos(
                           (int)Math.floor(position.getX() + moveDir.x),
                           position.getY(),
                           (int)Math.floor(position.getZ() + moveDir.z)
                        );
                        if ((
                              this.mc.world.getBlockState(posBehind).isSolidBlock(this.mc.world, posBehind)
                                 || this.mc.world.getBlockState(posBehind).getBlock() == Blocks.NETHER_PORTAL
                           )
                           && (
                              this.portalTrap == null
                                 || this.portalTrap.getSquaredDistance(posBehind) > 100.0
                                    && this.mc.player.getBlockPos().getSquaredDistance(posBehind)
                                       < this.mc.player.getBlockPos().getSquaredDistance(this.portalTrap)
                           )) {
                           this.portalTrap = posBehind;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public WWidget getWidget(GuiTheme theme) {
      WVerticalList list = theme.verticalList();

      BounceProbe probe = BounceProbe.get();
      list.add(theme.label(probe.isRecording() ? "Recording to bounce-probe.log." : "Probe idle."));

      WButton toggle = list.add(theme.button(probe.isRecording() ? "Stop the probe log" : "Start the probe log")).widget();
      toggle.action = () -> {
         if (probe.isRecording()) {
            probe.stop();
            toggle.set("Start the probe log");
         } else {
            probe.start();
            toggle.set(probe.isRecording() ? "Stop the probe log" : "Could not open the file");
         }
      };

      return list;
   }
   private record RouteKey(HopPlanner.Body body, double room, boolean perTick) {
   }
}
