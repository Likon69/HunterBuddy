package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.util.BounceProbe;
import com.hunterbuddy.util.BounceSolver;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import com.hunterbuddy.modules.regear.util.Utils;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
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
   private final Setting<Boolean> autoPitch = this.sgGeneral
      .add(
         new Builder().name("auto-pitch")
                     .description(
                        "Solves for the pitch that actually bounces fastest instead of using a fixed one. Speed comes from how often you land (each sprint-jump adds 0.2 while the elytra keeps 0.99 friction), so the best pitch depends on how much headroom is above you — worth up to +25% under a low ceiling."
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
               .visible(() -> (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get() && !(Boolean)this.autoPitch.get())
               .build()
      );
   private final Setting<Integer> setbackPause = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("setback-pause")
                  .description("Ticks to stop bouncing for after the server teleports you back, so the module doesn't fight a setback loop. 0 disables the pause (the client-side gliding reset still happens either way).")
               .defaultValue(5)
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
   private boolean startSprinting;
   private BlockPos portalTrap = null;
   private boolean paused = false;
   private int rubberBandCooldown = 0;
   /** Best pitch {@link BounceSolver} has found for the headroom last measured. */
   private float solvedPitch = 90.0F;
   /** Headroom the current {@link #solvedPitch} was solved for; -1 = not measured yet. */
   private double solvedHeadroom = -1.0;
   /** What {@link BounceSolver} predicts for {@link #solvedPitch}, shown in {@link #getInfoString}. */
   private double predictedSpeed = 0.0;
   /** Ticks left before {@link #updateAutoPitch} will re-measure headroom on the ground. */
   private int solveCooldown = 0;
   // hold-glide-clear: pings answered on our own schedule instead of vanilla's,
   // separate from pingQueue below (which belongs to the tunnel-bounce fake-lag
   // illusion and answers on its own timer, not this one).
   private final java.util.Queue<net.minecraft.network.packet.s2c.common.CommonPingS2CPacket> glideClearPingQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
   /** Glide-clear metadata packets held until the tick the re-deploy is free. */
   private final java.util.Queue<net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket> heldClears = new java.util.concurrent.ConcurrentLinkedQueue<>();
   /** player.age the current batch of held clears started at. Netty thread writes it, the main thread reads it. */
   private volatile long heldSince = 0L;
   /** G: the tick the player last actually left the ground under its own physics. */
   private int jumpTick = 0;
   private boolean wasOnGroundLastTick = true;
   /** Whether the jump key was down as of the end of the previous tick. */
   private boolean jumpKeyDownLastTick = false;
   /** Set by serviceGlideClear on the tick a hold releases, so updateJumpKey presses that same tick. */
   private boolean forcePressThisTick = false;
   // chestplate-fakefly (v0.5.3 rewrite): which hand a burst just swapped the
   // glider into, waiting for the return swap on SendMovementPacketsEvent —
   // non-null only within the one tick between the two swaps.
   private Hand fakeFlyBurstHand = null;
   /** Sequence of a fakefly swap awaiting the server's own answer; -1 = none pending. */
   private int fakeFlyPendingSequence = -1;
   /** Age the pending sequence's ack (PlayerActionResponseS2CPacket) landed at; -1 = not yet. */
   private long fakeFlyAckedAt = -1L;
   /** Set once an acked swap goes a full tick with no inventory echo — a confirmed refusal, not a guess. */
   private boolean fakeFlyDesynced = false;
   /** Set for the one send that follows a fakefly swap call, so onSendPacket knows which PlayerInteractItemC2SPacket is ours. */
   private boolean fakeFlyArmedForSend = false;
   /** Last refusal cause reported, so the message prints once rather than every tick it holds. */
   private String fakeFlyBlockedReason = null;
   /** Whether fakefly's pair was present as of the last check — what {@link #shouldAutoJump} gates the ground jump on. */
   private boolean fakeFlyPairReady = true;
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

      // The server's own answer to a fakefly swap: a full inventory echo
      // (F9) is only ever sent for one that actually applied. Not cancelled
      // — it confirms the state the client already predicted, same as it
      // would for a swap this module never touched. Any sync-0 echo counts,
      // not just one traceable to a specific pending sequence: it's also
      // what resumes the module on its own after a confirmed refusal,
      // exactly the "reprise sans réactivation" a fresh inventory view gives
      // by hand.
      if (event.packet instanceof InventoryS2CPacket inventory && inventory.syncId() == 0) {
         this.fakeFlyPendingSequence = -1;
         this.fakeFlyAckedAt = -1L;
         this.fakeFlyDesynced = false;
      }

      // The ack for a fakefly swap (F9 — sent before the swap itself is even
      // attempted server-side, so it always arrives before the inventory
      // echo a successful one gets). Recorded, not judged here: judged in
      // serviceFakeFlyBurst, one full tick later with still no echo.
      if (this.fakeFlyPendingSequence >= 0
         && this.mc.player != null
         && event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket ack
         && ack.sequence() >= this.fakeFlyPendingSequence) {
         this.fakeFlyAckedAt = this.mc.player.age;
      }

      // hold-glide-clear's own intake takes every ping and every glide clear
      // before anything else gets a look — including the tunnel-bounce
      // fake-lag ping queue below, which loses its own hold on a ping to
      // this one if both happen to be active at once. Rare in practice
      // (tunnel bounce is off by default, this is on), and correctness here
      // matters more than the illusion there.
      if (this.handleGlideClearPacket(event)) {
         return;
      }

      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         // A setback makes Grim's own knockback "possible" on the pong of its
         // transaction (the same mechanism a held clear rides); a held clear
         // still sitting there when the client applies a teleport it hasn't
         // pinged for yet is exactly the kind of offset this exists to avoid.
         this.releaseHeldGlideState();

         // Rubberband (Meteor-style): stop gliding client side, release the latch and
         // pause for a few ticks before restarting the takeoff cycle cleanly. The
         // gliding reset happens either way; only the pause length is the setting,
         // and 0 skips it — the loop most people would want off is the pause, not
         // the reset that keeps the client and server flags from fighting.
         if ((Integer)this.setbackPause.get() > 0) {
            this.rubberBandCooldown = (Integer)this.setbackPause.get();
         }

         this.prevGliding = false;
         this.realGliding = false;
         if (this.mc.player != null) {
            this.mc.player.stopGliding();
         }
      } else if (event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
         || event.packet instanceof net.minecraft.network.packet.s2c.common.DisconnectS2CPacket) {
         // Respawn also covers a dimension change in modern Minecraft — there
         // is no separate packet for it any more — and either way, whatever
         // was held belonged to a world that is no longer this one.
         this.releaseHeldGlideState();
      } else if (event.packet instanceof net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket velocity
         && this.mc.player != null
         && velocity.getEntityId() == this.mc.player.getId()) {
         this.releaseHeldGlideState();
      } else if (event.packet instanceof net.minecraft.network.packet.s2c.play.ExplosionS2CPacket explosion
         && explosion.playerKnockback().isPresent()) {
         this.releaseHeldGlideState();
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

      // The one PlayerInteractItemC2SPacket a fakefly swap just sent,
      // captured here rather than returned from interactItem (which hands
      // back an ActionResult, not the sequence) — judged by the server's own
      // answer to it in onReceivePacket, not by a timer alone.
      if (this.fakeFlyArmedForSend && event.packet instanceof PlayerInteractItemC2SPacket interact) {
         this.fakeFlyPendingSequence = interact.getSequence();
         this.fakeFlyAckedAt = -1L;
      }

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
      if (this.mc.player != null && !this.mc.player.getAbilities().allowFlying) {
         this.startSprinting = this.mc.player.isSprinting();
         this.tempPath = null;
         this.portalTrap = null;
         this.paused = false;
         this.rubberBandCooldown = 0;
         this.solvedPitch = ((Double)this.pitch.get()).floatValue();
         this.solvedHeadroom = -1.0;
         this.predictedSpeed = 0.0;
         this.solveCooldown = 0;
         this.heldClears.clear();
         this.glideClearPingQueue.clear();
         this.heldSince = 0L;
         this.jumpTick = this.mc.player.age;
         this.wasOnGroundLastTick = this.mc.player.isOnGround();
         this.jumpKeyDownLastTick = false;
         this.forcePressThisTick = false;
         this.fakeFlyBurstHand = null;
         this.fakeFlyPendingSequence = -1;
         this.fakeFlyBlockedReason = null;
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
      // Applied, not dropped: a held clear the module never gets to release
      // would leave the client believing it's still gliding after the server
      // already cleared it, worse than any tick the clear might have cost.
      this.releaseHeldGlideState();
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
      this.updateAutoPitch();
      this.tickFakeLag();
      // Before updateJumpKey: a release here can flip realGliding false this
      // same tick (the isGliding hook runs well before this point in a tick),
      // and updateJumpKey's own stale-flag branch is what presses off that.
      // serviceFakeFlyBurst follows the same rule for the same reason: its
      // swap-in has to have already happened for the key to press into it.
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
               ((ChestSwap)Modules.get().get(ChestSwap.class)).swap();
            } else {
               this.elytraToggled = true;
            }
         }

         if (this.enabled()) {
            // Held on, gliding or not, which is what the reference does. Dropping it
            // the instant the glide started -- Meteor's pattern, kept until now --
            // put a STOP_SPRINTING inside every one of the two-tick windows the
            // server granted, and the grant died at the end of all of them. That is
            // not proof, because the module cut the sprint on the grant itself and
            // the two cannot be told apart from the outside; it is the only thing
            // this client said in that window, and the reference never says it.
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
                  this.mc.player.setPitch(this.effectivePitch());
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

            // Fakefly's own burst (swap in, press, swap out) runs earlier in
            // this tick, from serviceFakeFlyBurst — before updateJumpKey, same
            // as serviceGlideClear, and for the same reason: it has to have
            // happened before the key decides whether to press.

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

   /**
    * Re-measures headroom on the ground, at most every 10 ticks, and only
    * re-solves the pitch when it actually changed — a ceiling doesn't move
    * mid-hop, so there is nothing to gain re-solving in the air, only cycles
    * to spend.
    */
   private void updateAutoPitch() {
      if ((Boolean)this.autoPitch.get() && (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get() && this.mc.world != null && this.mc.player != null) {
         if (this.solveCooldown > 0) {
            this.solveCooldown--;
         } else if (this.mc.player.isOnGround()) {
            this.solveCooldown = 10;
            double headroom = BounceSolver.measureHeadroom(this.mc.player, this.mc.world);
            if (!(this.solvedHeadroom >= 0.0) || !(Math.abs(headroom - this.solvedHeadroom) < 0.05)) {
               boolean clearHeld = (Boolean)this.holdGlideClear.get();
               this.solvedHeadroom = headroom;
               this.solvedPitch = BounceSolver.solvePitch(headroom, clearHeld);
               this.predictedSpeed = BounceSolver.simulateSpeed(this.solvedPitch, headroom, clearHeld);
            }
         }
      }
   }

   /** The pitch actually applied: the solver's answer, or the fixed setting when auto-pitch is off. */
   private float effectivePitch() {
      return (Boolean)this.autoPitch.get() ? this.solvedPitch : ((Double)this.pitch.get()).floatValue();
   }

   @Override
   public String getInfoString() {
      return (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get() && (Boolean)this.autoPitch.get() && !(this.predictedSpeed <= 0.0)
         ? String.format("%.0f b/s @ %.0f°", this.predictedSpeed, this.solvedPitch)
         : null;
   }

   /**
    * Whether this tick is a legal moment for a re-deploy to depart: airborne,
    * at least two ticks clear of the ground (F4 — Grim refuses a START while
    * onGround or lastOnGround), and the jump key was released the tick
    * before — the shape of a fresh press, not one still following the last
    * one. Shared between {@link #serviceGlideClear} (whether to release a
    * held clear) and {@link #serviceFakeFlyBurst} (whether to swap the
    * glider in) — the same tick means the same thing to both.
    *
    * <p>{@link #jumpTick} is recorded the first {@code Pre} that already
    * reads airborne — one tick after the ground impulse itself, in the
    * spec's own G notation. Two full ticks past *that* is G+3, one more than
    * Grim actually requires (G+2: positions G and G+1 already airborne); the
    * "+1" below, not "+2", is what keeps this at G+2 given where jumpTick
    * actually lands.
    */
   private boolean isFreeTick() {
      if (this.mc.player == null) return false;
      return !this.mc.player.isOnGround()
         && this.mc.player.age >= this.jumpTick + 1
         && !this.jumpKeyDownLastTick;
   }

   /**
    * v0.5.3's core mechanism, reconstructed from Grim rather than ported —
    * BepHax is closed-source and no code for this rebuild exists anywhere we
    * have it; this is a clean-room reading of the changelog against Grim's
    * own behaviour, not a copy of anything (see project memory for the
    * packet-level facts this is built on).
    *
    * <p>A glide clear is only ever applied, together with the pong of the
    * transaction that brackets it (Grim only flips its own copy of the flag
    * on that pong), on a tick where the re-deploy departs in that same tick
    * — never earlier. Landing anywhere else costs a tick of speed one way or
    * another: full ground friction if it lands on the jump tick, air
    * friction if it lands mid-hop. Held until the free tick it costs
    * nothing, because both sides are still agreeing on stale elytra physics
    * in the meantime — the real START only ever renews a server-side flag
    * that was never actually wrong yet.
    */
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
            // The mixin hook is what actually keeps realGliding current, and
            // nothing between the release above and updateJumpKey calls
            // isGliding() to run it — without this line realGliding still
            // reads last tick's "true" here, one tick stale by definition of
            // what a release is. forcePressThisTick covers updateJumpKey's
            // own tap; this is what serviceFakeFlyBurst reads to know a
            // START can actually depart this tick.
            this.realGliding = this.mc.player.isGliding();
            this.forcePressThisTick = true;
         } else {
            BounceProbe.get().line("HOLD-CLEAR", "since=" + (age - this.heldSince));
         }
      } else {
         // Nothing held: still answer whatever pings are waiting, from the
         // head of the tick, the way vanilla would have — a clear's own
         // transaction always lands on the wire before the clear itself
         // (Grim pings ahead of the metadata it brackets), so by the time a
         // clear shows up here as held, its ping may already have arrived
         // and been queued below with nothing (yet) to hold it for.
         net.minecraft.client.network.ClientPlayNetworkHandler handler = this.mc.getNetworkHandler();
         if (handler != null) {
            net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping;
            while ((ping = this.glideClearPingQueue.poll()) != null) {
               this.applyPacket(handler, ping);
            }
         }
      }
   }

   /**
    * hold-glide-clear's packet intake. Claims every ping outright — not just
    * ones that follow a clear, since a clear's own transaction ping can
    * arrive before we've seen the clear it belongs to — and holds a glide
    * clear until {@link #serviceGlideClear} finds a free tick to release it
    * on. Returns true if it consumed the packet.
    */
   private boolean handleGlideClearPacket(Receive event) {
      if (this.mc.player == null || !(Boolean)this.bounce.get() || !(Boolean)this.holdGlideClear.get()) {
         return false;
      }

      if (event.packet instanceof net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping) {
         this.glideClearPingQueue.add(ping);
         event.cancel();
         return true;
      }

      if (event.packet instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket tracker
         && tracker.id() == this.mc.player.getId()
         && this.mc.player.isGliding()) {
         // Index zero is the shared flag byte; 0x80 clear in it, while we're
         // still gliding, is the server turning the flag back off — the only
         // update from this packet worth holding. A glide-start confirmation
         // (0x80 set) or an unrelated flag change passes through untouched.
         for (net.minecraft.entity.data.DataTracker.SerializedEntry<?> entry : tracker.trackedValues()) {
            if (entry.id() == 0 && entry.value() instanceof Byte flags && (flags & 0x80) == 0) {
               if (this.heldClears.isEmpty()) {
                  this.heldSince = this.mc.player.age;
               }

               this.heldClears.add(tracker);
               event.cancel();
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Applies every held clear, in order, then every held ping's pong.
    * Clears first: Grim's transaction task only flips its copy of the flag
    * on the pong, so by the time our pong is on the wire our own client-side
    * flag has already been cleared to match what that pong is about to
    * confirm.
    */
   private void releaseHeldGlideState() {
      net.minecraft.client.network.ClientPlayNetworkHandler handler = this.mc.getNetworkHandler();
      if (handler == null) {
         this.heldClears.clear();
         this.glideClearPingQueue.clear();
         return;
      }

      net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket clear;
      while ((clear = this.heldClears.poll()) != null) {
         this.applyPacket(handler, clear);
      }

      net.minecraft.network.packet.s2c.common.CommonPingS2CPacket ping;
      while ((ping = this.glideClearPingQueue.poll()) != null) {
         this.applyPacket(handler, ping);
      }
   }

   /**
    * Applies a held packet as if it had just arrived. On the main thread
    * that's a direct {@code apply} — same as {@link #flushPackets}'s pings.
    * Off it (this class also releases from {@code onReceivePacket}'s netty
    * thread, for the setback/knockback/respawn/disconnect exceptions in
    * §6.3), a direct {@code apply} would run headfirst into
    * {@code NetworkThreadUtils.forceMainThread}: off the main thread it
    * queues the call in {@code PacketApplyBatcher} and throws
    * {@code OffThreadException} to unwind back out — which our own trigger
    * packet's handler is still on the stack above, so that exception would
    * have silently swallowed it, and the teleport, knockback or respawn that
    * triggered this release would never have run at all. Queuing onto the
    * same batcher instead is exactly what the packet that triggered us is
    * about to do anyway (`ClientConnection.channelRead0` batches every
    * packet that isn't already on the render thread) — ours simply join the
    * queue first, so ordering (clears and pongs before the trigger packet)
    * still holds once the batcher drains in {@code MinecraftClient.render()},
    * ahead of both {@code runTasks} and the next {@code tick()}.
    */
   private <T extends net.minecraft.network.listener.PacketListener> void applyPacket(T listener, net.minecraft.network.packet.Packet<T> packet) {
      if (this.mc.isOnThread()) {
         packet.apply(listener);
      } else {
         this.mc.getPacketApplyBatcher().add(listener, packet);
      }
   }

   /** A glider: the item this chest-slot equip actually flies with. */
   private static boolean isGlider(ItemStack stack) {
      return !stack.isEmpty() && LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST);
   }

   /** A chest piece that isn't itself a glider — the other half of fakefly's pair. */
   private static boolean isChestPiece(ItemStack stack) {
      if (stack.isEmpty()) return false;
      EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !isGlider(stack);
   }

   /** First hotbar slot holding a glider, or -1. Used only right after ChestSwap moves one there. */
   private int findHotbarGliderSlot() {
      for (int i = 0; i < 9; i++) {
         if (isGlider(this.mc.player.getInventory().getStack(i))) return i;
      }

      return -1;
   }

   /**
    * The hand holding the glider, when the resting pair fakefly needs is
    * actually there: a chest piece worn and a glider in a hand (the transient
    * mid-burst state — glider briefly worn, chest piece briefly in hand — is
    * handled separately, by {@link #serviceFakeFlyBurst} itself). Never falls
    * back to bouncing the glider alone when the pair isn't there: the
    * changelog is explicit that a missing pair stops the module rather than
    * degrading it.
    */
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

   /** Prints a refusal once per distinct cause rather than every tick it holds. */
   private void reportFakeFlyBlocked(String reason) {
      if (reason.equals(this.fakeFlyBlockedReason)) return;
      this.fakeFlyBlockedReason = reason;
      this.info(reason, new Object[0]);
   }

   /**
    * v0.5.3's fakefly rewrite: one right-click burst per re-deploy instead of
    * the mlep-era ground jump this file used to send here (see the class
    * doc for why that path was dead code). Swaps the glider in at the head
    * of a free tick, so {@code updateJumpKey}'s own tap presses into a
    * takeoff the server sees a glider actually worn for, then swaps it back
    * out for the chest piece on {@link #onSendMovementPackets} — before
    * that tick's own position packet — so the server is never on the far
    * side of a tick with the glide flag set and no glider on the torso to
    * justify it, nor on the near side with a glider still worn once the
    * chest piece was due back.
    */
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

      // Judged without blocking the next swap on it: the client's own
      // prediction (F8) is deterministic, so a swap still awaiting its echo
      // doesn't hold anything up. Only a confirmed refusal does — the ack
      // for our sequence landing, then a full tick with still no echo. Not
      // a timer from the send: on 2b2t's own lag a fixed delay would call a
      // perfectly applied swap "refused" for no reason.
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

      // Recovery: a glider found worn on any tick that isn't this burst's
      // own doing (a swap-out the server refused, a pause caught with it
      // still on) gets the chest piece back at once — retried every other
      // tick rather than every tick, since a swap just refused a moment ago
      // isn't going to succeed the very next one either. Normal operation,
      // not a failure: the pair is still there, just the other way around.
      if (isGlider(worn)) {
         this.fakeFlyPairReady = true;
         this.fakeFlyBlockedReason = null;

         if (age % 2L != 0L) return;

         ItemStack main = this.mc.player.getStackInHand(Hand.MAIN_HAND);
         ItemStack off = this.mc.player.getStackInHand(Hand.OFF_HAND);
         Hand chestHand = isChestPiece(main) ? Hand.MAIN_HAND : (isChestPiece(off) ? Hand.OFF_HAND : null);

         if (chestHand != null) {
            this.swapFakeFlyHand(chestHand);
         } else if (this.mc.player.isOnGround()) {
            // Glider worn, no chest piece in either hand: Meteor's ChestSwap,
            // "from a quiet tick" — on the ground, nothing else pending. It
            // puts a chest piece from the hotbar onto the torso and the
            // glider wherever that chest piece came from; selecting the slot
            // it lands in is what gets it into a hand, ready for the next
            // free tick's burst. [hypothesis, per the spec: ChestSwap goes
            // through inventory clicks, which Grim compensates outside the
            // movement window — watch the probe's IN-TP lines around this if
            // setbacks show up here specifically.]
            Modules.get().get(ChestSwap.class).swap();
            int slot = this.findHotbarGliderSlot();
            if (slot >= 0) {
               this.mc.player.getInventory().setSelectedSlot(slot);
            } else {
               this.reportFakeFlyBlocked("chest piece must be in the hotbar");
            }
         }

         return;
      }

      // Checked every tick, not just a free one: waiting for a free tick to
      // notice a missing pair would mean never noticing it at all while
      // still on the ground, which is exactly where the pair usually goes
      // missing (shouldAutoJump reads fakeFlyPairReady to stop the ground
      // jump entirely once it's false).
      Hand gliderHand = this.fakeFlyReadyHand();
      this.fakeFlyPairReady = gliderHand != null;
      if (gliderHand == null) return;

      // Free isn't enough on its own: it's true through most of an ordinary
      // hop too, glide flag and all, where checkGliding() would refuse a
      // START outright (F7) and this would just churn the swap for nothing
      // every few ticks whenever fakeFlyPendingSequence happens to be idle.
      // Only a tick where the client's own glide flag just went (or already
      // was) false is one where a re-deploy can actually depart.
      if (!this.isFreeTick() || this.mc.player.isGliding()) return;

      this.swapFakeFlyHand(gliderHand);
      this.fakeFlyBurstHand = gliderHand;
      this.forcePressThisTick = true;
      BounceProbe.get().line("FAKEFLY-SWAP-IN", "hand=" + gliderHand);
   }

   /** One right-click equip swap, its sequence armed for capture in onSendPacket. */
   private void swapFakeFlyHand(Hand hand) {
      this.fakeFlyArmedForSend = true;

      try {
         this.mc.interactionManager.interactItem(this.mc.player, hand);
      } finally {
         this.fakeFlyArmedForSend = false;
      }
   }

   /**
    * The second half of a burst: the chest piece back on before this tick's
    * own movement packet (F12 — this event fires at the head of
    * sendMovementPackets, after tickMovement already sent the START).
    *
    * <p>{@code SendMovementPacketsEvent} is only ever a namespace for its two
    * nested marker classes ({@code Pre}/{@code Post}, each a reused
    * singleton) — Meteor never posts the bare outer class, and Orbit
    * dispatches on the exact runtime class, so a handler typed for it would
    * simply never fire. {@code Pre} is what {@code AntiHunger} and
    * {@code Rotations} both listen for at this same point.
    */
   @EventHandler
   private void onSendMovementPackets(SendMovementPacketsEvent.Pre event) {
      if (this.fakeFlyBurstHand == null) return;

      Hand hand = this.fakeFlyBurstHand;
      this.fakeFlyBurstHand = null;
      this.swapFakeFlyHand(hand);
      BounceProbe.get().line("FAKEFLY-SWAP-OUT", "hand=" + hand);
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

      if (this.mc.player != null) {
         boolean onGround = this.mc.player.isOnGround();
         // G: the tick physics actually leaves the ground, whatever sent it
         // there — not necessarily our own jump, and that's the point: Grim's
         // onGround||lastOnGround check (F4) doesn't care why either.
         if (!onGround && this.wasOnGroundLastTick) {
            this.jumpTick = this.mc.player.age;
         }

         this.wasOnGroundLastTick = onGround;
      }

      if (this.mc.player == null || !this.enabled() || !this.shouldAutoJump()) {
         this.jumpKeyDown = false;
      } else if (this.mc.player.isOnGround()) {
         // The speed gate motion-y-boost used to carry on the module's own jump,
         // moved to where the jump is decided now: stop taking off once the run is
         // already fast enough, and let the speed carry.
         this.jumpKeyDown = !(Boolean)this.motionYBoost.get()
            || meteordevelopment.meteorclient.utils.Utils.getPlayerSpeed().multiply(1.0, 0.0, 1.0).length() < (Double)this.speed.get();
      } else if (this.forcePressThisTick) {
         // Ahead of realGliding on purpose: this is the tick a release just
         // ran, and nothing between that release and here calls isGliding()
         // to refresh the mirror through its own hook — serviceGlideClear
         // does that explicitly right after releasing, but relying on
         // realGliding here regardless would still read this same tick's
         // release as the *previous* tick's stale "still gliding", pressing
         // one tick late and losing exactly the tick hold-glide-clear exists
         // to save.
         this.jumpKeyDown = true;
      } else if (this.realGliding) {
         this.jumpKeyDown = false;
      } else {
         this.jumpKeyDown = !previous;
      }

      this.forcePressThisTick = false;
      this.jumpKeyDownLastTick = this.jumpKeyDown;
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
      // Fakefly uses the same tapped key as a plain bounce now — the burst
      // that swaps the glider in rides the same press, it doesn't jump on
      // its own any more. But it stops jumping outright, same as the
      // changelog says, once serviceFakeFlyBurst finds the pair actually
      // missing — jumping toward a takeoff fakefly can't complete is worse
      // than standing still and saying why.
      return (Boolean)this.bounce.get() && (!(Boolean)this.fakeFly.get() || this.fakeFlyPairReady);
   }

   public boolean isFakeFlyEnabled() {
      return (Boolean)this.fakeFly.get();
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

   /**
    * The button that starts and stops the probe.
    *
    * <p>Off unless asked for, and it used to start itself every time the module came
    * on, which wrote a file nobody had asked for on every flight. What it records is
    * only worth having when something is being chased: a line per tick and one per
    * digging, movement, input, metadata and teleport packet, in the order they
    * crossed the wire, which is the only way a two-tick disagreement with the server
    * can be read at all.
    *
    * <p>The file is bounce-probe.log in the game folder, emptied at every start so a
    * run stands on its own, and it keeps recording until it is stopped here or the
    * module is switched off.
    */
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
}
