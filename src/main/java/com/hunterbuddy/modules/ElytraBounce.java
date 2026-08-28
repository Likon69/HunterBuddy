package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mixin.accessors.LivingEntityAccessor;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.util.BounceProbe;
import com.hunterbuddy.modules.regear.util.Utils;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

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
   private final Setting<Double> pitch = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("pitch")
                  .description("The pitch to set when bounce is enabled.")
               .defaultValue(90.0)
               .sliderRange(-90.0, 90.0)
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get())
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
   private final Setting<Boolean> fakeFly = this.sgGeneral
      .add(
         new Builder().name("chestplate-fakefly")
                  .description("Lets you fly using a chestplate to use almost 0 elytra durability. Must have elytra in hotbar.")
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
   private boolean startSprinting;
   private BlockPos portalTrap = null;
   private boolean paused = false;
   private int rubberBandCooldown = 0;
   // Lambda-style smart gliding: only force isGliding() once the real gliding flag
   // has actually been set (server accepted the glide), then latch it through ground
   // touches so gliding physics (low friction) is kept between bounces.
   private boolean prevGliding = false;

   /** True while the module is holding the jump key down for the player. */
   private boolean jumpKeyDown = false;
   // Mirror of the real (unforced) gliding flag, updated by modifyIsGliding().
   private boolean realGliding = false;
   // Lambda-style fake lag for 1x2 tunnel bounce: outgoing packets and incoming pings
   // are held while skimming the tunnel floor, then released in bursts so the server
   // treats the movement like a lag spike instead of flagging it.
   private final java.util.Queue<net.minecraft.network.packet.Packet<?>> sendQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
   private final java.util.Queue<net.minecraft.network.packet.s2c.common.CommonPingS2CPacket> pingQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
   // Read/written from both the client and netty threads.
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

      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         // Rubberband (Meteor-style): stop gliding client side, release the latch and
         // pause for a few ticks before restarting the takeoff cycle cleanly.
         this.rubberBandCooldown = 5;
         this.prevGliding = false;
         this.realGliding = false;
         if (this.mc.player != null) {
            this.mc.player.stopGliding();
         }
      } else if (event.packet instanceof net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping && this.shouldQueuePackets()) {
         // Grim brackets movement with transaction pings; holding them (and answering
         // in the flush burst) is what makes the "lag" believable.
         this.pingQueue.add(ping);
         event.cancel();
      } else if (event.packet instanceof CloseScreenS2CPacket) {
         event.cancel();
      }
   }

   @EventHandler
   private void onSendPacket(meteordevelopment.meteorclient.events.packets.PacketEvent.Send event) {
      this.probeSend(event.packet);

      if (this.flushingPackets) {
         return;
      }

      if (this.shouldQueuePackets()) {
         this.sendQueue.add(event.packet);
         event.cancel();
      } else {
         // Flushing here (before this packet goes out) keeps the packet order intact
         // when the queue condition just turned false mid-tick.
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

   public void onActivate() {
      BounceProbe.get().start();
      if (this.mc.player != null && !this.mc.player.getAbilities().allowFlying) {
         this.startSprinting = this.mc.player.isSprinting();
         this.tempPath = null;
         this.portalTrap = null;
         this.paused = false;
         this.rubberBandCooldown = 0;
         this.prevGliding = false;
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
      this.prevGliding = false;
      this.realGliding = false;
      this.fakeLagTicks = 0;
      this.flushPackets();
      if (this.mc.player != null) {
         if ((Boolean)this.freePitch.get() && (Boolean)this.lockPitch.get() && (Boolean)this.bounce.get()) {
            this.mc.player.setPitch(this.cameraPitch);
         }

         if ((Boolean)this.bounce.get()
            && (!BaritoneHelper.hasElytraProcess() || BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null)) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
         }

         this.mc.player.setSprinting(this.startSprinting);
         if ((Boolean)this.toggleElytra.get()
            && !(Boolean)this.fakeFly.get()
            && !this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().toString().contains("chestplate")) {
            ((ChestSwap)Modules.get().get(ChestSwap.class)).swap();
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      this.tickFakeLag();
      this.updateJumpKey();
      this.probeTick();
      if (this.rubberBandCooldown > 0) {
         this.rubberBandCooldown--;
         return;
      }
      if (this.mc.player != null && !this.mc.player.getAbilities().allowFlying) {
         if ((Boolean)this.toggleElytra.get() && !(Boolean)this.fakeFly.get() && !this.elytraToggled) {
            if (!this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
               ((ChestSwap)Modules.get().get(ChestSwap.class)).swap();
            } else {
               this.elytraToggled = true;
            }
         }

         if (this.enabled()) {
            // Sprinting all the time while gliding makes some anticheats rubberband
            // (Meteor Bounce does the same): sprint only on ground while gliding so
            // the jump keeps its sprint boost, sprint normally otherwise.
            if (this.mc.player.isGliding()) {
               this.mc.player.setSprinting(this.mc.player.isOnGround());
            } else {
               this.mc.player.setSprinting(true);
            }
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
               this.prevGliding = false;
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
                  this.mc.player.setPitch(((Double)this.pitch.get()).floatValue());
               }
            }
         }

         if (this.enabled()) {
            if (this.mc.player.isOnGround()) {
               // The server force-stops gliding on ground contact (canGlide() returns
               // false on ground), so mark the real flag off to re-deploy right after
               // the next jump instead of relying on the flag sync through ViaVersion.
               this.realGliding = false;
            }

            if ((Boolean)this.fakeFly.get()) {
               this.doGrimEflyStuff();
            }

            // Nothing else here on purpose. Sending the takeoff from this tick used
            // to be the whole point, and it is exactly what kept it from working:
            // the packet went out one tick after leaving the ground, and the check
            // that guards this cancels any takeoff that arrives while the last two
            // ground flags are still set -- outright, before the server ever sees
            // it. It also arrived before the input packet that releases the jump,
            // which reads as a takeoff with the key already held. The forced key
            // does it instead: held on the ground, tapped in the air, and vanilla
            // sends the same packet on the rising edge, two ticks up, in the one
            // shape those checks accept.
         }
      }
   }

   private boolean isDiagonalHeading() {
      double m = Math.abs(this.mc.player.getYaw() % 90.0);
      double distToAxis = Math.min(m, 90.0 - m);
      return distToAxis > (Double)this.minDiagonalAngle.get();
   }

   public boolean enabled() {
      return this.isActive()
         && !this.paused
         && this.rubberBandCooldown <= 0
         && this.mc.player != null
         && ((Boolean)this.fakeFly.get() || this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA));
   }

   /**
    * Called by LivingEntityMixin#isGliding at RETURN with the real (unforced) flag.
    * Lambda-style latch: once the gliding flag has genuinely been set, keep returning
    * true through ground touches so gliding physics carry the momentum between
    * bounces. Never forces gliding before an actual takeoff, which would desync the
    * client from the server and get flagged by Grim.
    */
   public boolean modifyIsGliding(boolean original) {
      this.realGliding = this.prevGliding = original;

      // Passed through as it comes. Holding it true across ground contact kept the
      // glide physics running between bounces on the client, but the server drops
      // the flag the instant you touch anything, so the two were flying different
      // players -- and the one the server flies is the one that gets stood back up.
      // The reference does not latch it either; it jumps instead.
      return original;
   }

   public boolean isFreePitchEnabled() {
      return this.enabled() && (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get() && (Boolean)this.freePitch.get();
   }

   /**
    * Holds the jump key for the player, which is how the bounce is actually flown.
    *
    * <p>On the ground the key is held, so the player jumps. In the air, before the
    * elytra is out, it is tapped -- held one tick, released the next -- because it
    * is the press that deploys, and a key left down is not a press. Once gliding it
    * is let go. The takeoff that follows is vanilla's own, sent by the client as any
    * jump would be, which is the difference between a bounce the server agrees with
    * and one it answers by standing you back up.
    */
   private void updateJumpKey() {
      boolean previous = this.jumpKeyDown;
      if (this.mc.player == null || !this.enabled() || !this.shouldAutoJump()) {
         this.jumpKeyDown = false;
      } else if (this.mc.player.isOnGround()) {
         // The speed gate motion-y-boost used to carry on the module's own jump,
         // moved to where the jump is decided now: stop taking off once the run is
         // already fast enough, and let the speed carry.
         this.jumpKeyDown = !(Boolean)this.motionYBoost.get()
            || meteordevelopment.meteorclient.utils.Utils.getPlayerSpeed().multiply(1.0, 0.0, 1.0).length() < (Double)this.speed.get();
      } else if (this.realGliding) {
         this.jumpKeyDown = false;
      } else {
         this.jumpKeyDown = !previous;
      }
   }

   /**
    * The state of the player at the top of a tick, before anything acts on it.
    *
    * <p>Written after the jump key has been decided and before the game moves, so a line and
    * the packets stamped with the same tick read in the order they happened.
    */
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
            this.jumpKeyDown,
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

         // Index zero on Entity is the shared flag byte, and 0x80 in it is the gliding bit:
         // this is the server answering a takeoff, and the only thing that ever turns the
         // client's own flag back off.
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

   /** Read by KeyBindingMixin: whether jump is down as far as the game is concerned. */
   public boolean isJumpKeyForcedDown() {
      return this.jumpKeyDown;
   }

   /** Fake fly does its own jumping, so the key is left alone there. */
   public boolean shouldAutoJump() {
      return (Boolean)this.bounce.get() && !(Boolean)this.fakeFly.get();
   }

   public boolean isFakeFlyEnabled() {
      return (Boolean)this.fakeFly.get();
   }

   public boolean shouldDoChestSwapExploit() {
      return (Boolean)this.fakeFly.get() && !this.paused;
   }

   public void doChestSwapExploit(CallbackInfo ci) {
      if (this.mc.player != null) {
         int slot = this.getInventoryItemSlot(Items.ELYTRA);
         boolean elytraEquipped = this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA);
         if (elytraEquipped || slot != -1) {
            if (!this.mc.player.isGliding()) {
               boolean swapBack = false;
               if (!elytraEquipped) {
                  this.swapArmor(2, slot);
                  swapBack = true;
               }

               this.sendStartFlyingPacket();
               this.mc.player.startGliding();
               if (swapBack) {
                  this.swapArmor(2, slot);
               }
            }

            if (this.mc.player.isOnGround()) {
               this.clientJump();
            }
         }
      }
   }

   private void clientJump() {
      float f = ((LivingEntityAccessor)this.mc.player).invokeGetJumpVelocity();
      if (!(f <= 1.0E-5F)) {
         Vec3d vec3d = this.mc.player.getVelocity();
         this.mc.player.setVelocity(vec3d.x, f, vec3d.z);
         if (this.mc.player.isSprinting()) {
            float g = this.mc.player.getYaw() * (float) (Math.PI / 180.0);
            this.mc
               .player
               .setVelocity(this.mc.player.getVelocity().add(-MathHelper.sin(g) * 0.2, 0.0, MathHelper.cos(g) * 0.2));
         }

         this.mc.player.velocityDirty = true;
      }
   }

   private void doGrimEflyStuff() {
      if ((Boolean)this.bounce.get()
         && this.mc.player.isOnGround()
         && (
            !(Boolean)this.motionYBoost.get()
               || meteordevelopment.meteorclient.utils.Utils.getPlayerSpeed().multiply(1.0, 0.0, 1.0).length() < (Double)this.speed.get()
         )) {
         this.mc.player.jump();
      }
   }

   @EventHandler
   private void onPlaySound(PlaySoundEvent event) {
      if ((Boolean)this.fakeFly.get()) {
         for (Identifier identifier : List.of(
            Identifier.of("minecraft:item.armor.equip_generic"),
            Identifier.of("minecraft:item.armor.equip_netherite"),
            Identifier.of("minecraft:item.armor.equip_elytra"),
            Identifier.of("minecraft:item.armor.equip_diamond"),
            Identifier.of("minecraft:item.armor.equip_gold"),
            Identifier.of("minecraft:item.armor.equip_iron"),
            Identifier.of("minecraft:item.armor.equip_chain"),
            Identifier.of("minecraft:item.armor.equip_leather"),
            Identifier.of("minecraft:item.elytra.flying")
         )) {
            if (identifier.equals(event.sound.getId())) {
               event.cancel();
               break;
            }
         }
      }
   }

   private int getInventoryItemSlot(Item item) {
      for (int i = 36; i >= 0; i--) {
         if (this.mc.player.getInventory().getStack(i).getItem().equals(item)) {
            return i;
         }
      }

      return -1;
   }

   private void pickupSlot(int slot) {
      this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, this.mc.player);
   }

   private void swapArmor(int armorSlot, int inSlot) {
      int slot = inSlot;
      if (slot < 9) {
         slot += 36;
      }

      ItemStack stack = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
      armorSlot = 8 - armorSlot;
      this.pickupSlot(slot);
      boolean rt = !stack.isEmpty();
      this.pickupSlot(armorSlot);
      if (rt) {
         this.pickupSlot(slot);
      }
   }

   private void sendStartFlyingPacket() {
      if (this.mc.player != null) {
         this.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
      }
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
}
