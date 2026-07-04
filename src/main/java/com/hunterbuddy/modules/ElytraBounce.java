package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.mixin.LivingEntityAccessor;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
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
      super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "ElytraBounce", "Elytra fly with some more features.");
   }

   @EventHandler
   private void onReceivePacket(Receive event) {
      if (!(event.packet instanceof PlayerPositionLookS2CPacket packet) && event.packet instanceof CloseScreenS2CPacket) {
         event.cancel();
      }
   }

   public void onActivate() {
      if (this.mc.player != null && !this.mc.player.getAbilities().allowFlying) {
         this.startSprinting = this.mc.player.isSprinting();
         this.tempPath = null;
         this.portalTrap = null;
         this.paused = false;
         this.waitingForChunksToLoad = false;
         this.elytraToggled = false;
         this.lastPos = this.mc.player.getPos();
         this.lastUnstuckPos = this.mc.player.getPos();
         this.stuckTimer = 0;
         this.cameraPitch = this.mc.player.getPitch();
         if ((Boolean)this.bounce.get() && this.mc.player.getPos().multiply(1.0, 0.0, 1.0).length() >= 100.0) {
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
                  double speedBps = this.mc.player.getPos().subtract(this.lastPos).multiply(20.0, 0.0, 20.0).length();
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

               this.lastPos = this.mc.player.getPos();
            }
         }
      }
   }

   public void onDeactivate() {
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
      if (this.mc.player != null && !this.mc.player.getAbilities().allowFlying) {
         if ((Boolean)this.toggleElytra.get() && !(Boolean)this.fakeFly.get() && !this.elytraToggled) {
            if (!this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
               ((ChestSwap)Modules.get().get(ChestSwap.class)).swap();
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
               this.lastUnstuckPos = this.mc.player.getPos();
            }

            if ((Boolean)this.highwayObstaclePasser.get()
               && this.mc.player.getPos().length() > 100.0
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
                  currDistance += this.mc.player.getPos().distanceTo(this.portalTrap.toCenterPos());
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
                  Vec3d travelVec = this.mc.player.getPos().subtract(this.startPos.toCenterPos());
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

               if (!(Boolean)this.fakeFly.get()
                  && this.mc.player.isOnGround()
                  && (
                     !(Boolean)this.motionYBoost.get()
                        || meteordevelopment.meteorclient.utils.Utils.getPlayerSpeed().multiply(1.0, 0.0, 1.0).length() < (Double)this.speed.get()
                  )) {
                  this.mc.player.jump();
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
            if ((Boolean)this.fakeFly.get()) {
               this.doGrimEflyStuff();
            } else {
               this.sendStartFlyingPacket();
            }
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
         && this.mc.player != null
         && ((Boolean)this.fakeFly.get() || this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA));
   }

   public boolean isFreePitchEnabled() {
      return this.enabled() && (Boolean)this.bounce.get() && (Boolean)this.lockPitch.get() && (Boolean)this.freePitch.get();
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
            if (!this.mc.player.isFallFlying()) {
               boolean swapBack = false;
               if (!elytraEquipped) {
                  this.swapArmor(2, slot);
                  swapBack = true;
               }

               this.sendStartFlyingPacket();
               try { this.mc.player.getClass().getMethod("startGliding").invoke(this.mc.player); } catch (ReflectiveOperationException e) { /* Yarn 1.21.1 has no startGliding(); the server flag set via the C2S packet already triggers elytra deploy */ }
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
         double distanceToHighway = Utils.distancePointToDirection(Vec3d.of(centerPos), moveDir, this.mc.player.getPos());
         if (!(distanceToHighway > 21.0)) {
            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  for (int y = this.targetY; y < this.targetY + 3; y++) {
                     BlockPos position = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);
                     if (!(
                           Utils.distancePointToDirection(Vec3d.of(position), moveDir, this.mc.player.getPos())
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
