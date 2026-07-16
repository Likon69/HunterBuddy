package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mixin.accessors.EntityVelocityUpdateS2CPacketAccessor;
import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.modules.regear.util.InventoryManager;
import com.hunterbuddy.modules.regear.util.PlacementUtils;
import com.hunterbuddy.modules.regear.util.PushOutOfBlocksEvent;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.CollisionShapeEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;

public class Phase extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPearl = this.settings.createGroup("Pearl");
   private final SettingGroup sgClipping = this.settings.createGroup("Clipping");
   private final Setting<Phase.PhaseMode> mode = sgGeneral
      .add(
         new Builder<Phase.PhaseMode>().name("mode").description("The phase mode for clipping into blocks.").defaultValue(Phase.PhaseMode.Pearl)
            .build()
      );
   private final Setting<Integer> pitch = this.sgPearl
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("pitch")
                     .description("The pitch angle to throw pearls.")
                  .defaultValue(83)
               .range(70, 90)
               .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
            .build()
      );
   private final Setting<Boolean> swapAlternative = this.sgPearl
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("swap-alternative")
                     .description("Uses inventory swap for swapping to pearls.")
                  .defaultValue(true)
               .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
            .build()
      );
   private final Setting<Boolean> attack = this.sgPearl
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("attack")
                     .description("Attacks entities in the way of the pearl phase.")
                  .defaultValue(false)
               .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
            .build()
      );
   private final Setting<Boolean> swing = this.sgPearl
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("swing")
                     .description("Swings the hand when throwing pearls.")
                  .defaultValue(true)
               .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
            .build()
      );
   private final Setting<Boolean> selfFill = this.sgPearl
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("self-fill")
                     .description("Automatically fills blocks you are phasing on.")
                  .defaultValue(false)
               .visible(() -> this.mode.get() == Phase.PhaseMode.Pearl)
            .build()
      );
   private final Setting<Double> blocks = this.sgClipping
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("blocks")
                  .description("The block distance to phase clip.")
               .defaultValue(0.003)
               .range(0.001, 10.0)
               .sliderMax(1.0)
               .visible(() -> this.mode.get() != Phase.PhaseMode.Pearl && this.mode.get() != Phase.PhaseMode.Clip)
            .build()
      );
   private final Setting<Double> distance = this.sgClipping
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("distance")
                  .description("The distance to phase.")
               .defaultValue(0.2)
               .range(0.0, 10.0)
               .sliderMax(1.0)
               .visible(() -> this.mode.get() != Phase.PhaseMode.Pearl && this.mode.get() != Phase.PhaseMode.Clip)
            .build()
      );
   private final Setting<Boolean> autoClip = this.sgClipping
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("auto-clip")
                     .description("Automatically clips into the block.")
                  .defaultValue(true)
               .visible(() -> this.mode.get() != Phase.PhaseMode.Pearl && this.mode.get() != Phase.PhaseMode.Clip)
            .build()
      );
   private boolean wasPhasing = false;
   private int tickCounter = 0;
   private InventoryManager inventoryManager = InventoryManager.getInstance();

   public Phase() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "phase", "Allows player to phase through solid blocks.");
   }

   public void onActivate() {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.mode.get() == Phase.PhaseMode.Pearl) {
            this.performPearlPhase();
            this.toggle();
         } else if (this.mode.get() == Phase.PhaseMode.Clip) {
            this.performClipPhase();
            this.toggle();
         } else {
            if ((Boolean)this.autoClip.get() && this.mode.get() == Phase.PhaseMode.Normal) {
               this.performAutoClip();
            }

            this.wasPhasing = false;
            this.tickCounter = 0;
         }
      } else {
         this.toggle();
      }
   }

   public void onDeactivate() {
      if (this.mc.player != null) {
         this.mc.player.noClip = false;
      }
   }

   @EventHandler(priority = 100)
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.tickCounter++;
         if (this.mode.get() == Phase.PhaseMode.Clip && this.mc.player.isOnGround() && !this.mc.player.hasVehicle()) {
            this.performClipTick();
            this.toggle();
         }
      }
   }

   @EventHandler
   private void onPlayerMove(PlayerMoveEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         switch ((Phase.PhaseMode)this.mode.get()) {
            case Normal:
               this.handleNormalMovement(event);
               break;
            case Sand:
               this.handleSandMovement(event);
               break;
            case Climb:
               this.handleClimbMovement(event);
         }
      }
   }

   @EventHandler
   private void onPacketReceive(Receive event) {
      if (event.packet instanceof EntityVelocityUpdateS2CPacket packet) {
         EntityVelocityUpdateS2CPacketAccessor accessor = (EntityVelocityUpdateS2CPacketAccessor)packet;
         if (accessor.getEntityId() == this.mc.player.getId() && this.isActive()) {
            Vec3d velocity = accessor.getVelocity();
            if (velocity.lengthSquared() < 0.1) {
               event.cancel();
            }
         }
      }
   }

   @EventHandler
   private void onCollisionShape(CollisionShapeEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         switch ((Phase.PhaseMode)this.mode.get()) {
            case Normal:
               if (event.shape != VoxelShapes.empty()
                  && event.shape.getBoundingBox().maxY > this.mc.player.getBoundingBox().minY
                  && this.mc.player.isSneaking()) {
                  event.cancel();
                  event.shape = VoxelShapes.empty();
               }
               break;
            case Sand:
               event.cancel();
               event.shape = VoxelShapes.empty();
               this.mc.player.noClip = true;
               break;
            case Climb:
               if (this.mc.player.horizontalCollision) {
                  event.cancel();
                  event.shape = VoxelShapes.empty();
               }

               if (this.mc.options.sneakKey.isPressed()
                  || this.mc.options.jumpKey.isPressed() && event.pos.getY() > this.mc.player.getY()) {
                  event.cancel();
               }
         }
      }
   }

   @EventHandler
   private void onPushOutOfBlocks(PushOutOfBlocksEvent event) {
      if (this.isActive()) {
         event.cancel();
      }
   }

   private void performPearlPhase() {
      int pearlSlot = PlacementUtils.getEnderPearlSlot();
      if (pearlSlot != -1 && !this.mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
         Vec3d pearlTargetVec = new Vec3d(Math.floor(this.mc.player.getX()) + 0.5, 0.0, Math.floor(this.mc.player.getZ()) + 0.5);
         float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), pearlTargetVec);
         float yaw = rotations[0] + 180.0F;
         if ((Boolean)this.attack.get()) {
            this.handlePearlAttacks(yaw);
         }

         if ((Boolean)this.selfFill.get()) {
            this.handleSelfFill(yaw);
         }

         RotationUtils rotationManager = RotationUtils.getInstance();
         int targetSlot;
         if ((Boolean)this.swapAlternative.get()) {
            targetSlot = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
            this.performInventorySwapPVP(pearlSlot);
         } else {
            if (pearlSlot >= 9) {
               return;
            }

            targetSlot = pearlSlot;
         }

         this.inventoryManager.setSlot(targetSlot, 30);
         rotationManager.setRotationSilent(yaw, ((Integer)this.pitch.get()).intValue());
         this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, yaw, ((Integer)this.pitch.get()).intValue()));
         if ((Boolean)this.swing.get()) {
            this.mc.player.swingHand(Hand.MAIN_HAND);
         } else {
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         }

         if ((Boolean)this.swapAlternative.get()) {
            this.performInventorySwapPVP(pearlSlot);
         }

         this.inventoryManager.syncToClient();
         rotationManager.setRotationSilentSync();
      }
   }

   private void handlePearlAttacks(float yaw) {
      BlockHitResult hitResult = (BlockHitResult)this.mc.player.raycast(3.0, 0.0F, false);
      Box searchBox = Box.from(Vec3d.ofCenter(hitResult.getBlockPos())).expand(0.2);

      for (Entity entity : this.mc.world.getOtherEntities(null, searchBox)) {
         if (entity instanceof ItemFrameEntity itemFrame) {
            this.mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, this.mc.player.isSneaking()));
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         }
      }

      BlockState state = this.mc.world.getBlockState(this.mc.player.getBlockPos());
      if (state.getBlock() instanceof ScaffoldingBlock) {
         BlockPos pos = this.mc.player.getBlockPos();
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP));
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
      }
   }

   private void handleSelfFill(float yaw) {
      float yaw1 = yaw % 360.0F;
      if (yaw1 < 0.0F) {
         yaw1 += 360.0F;
      }

      BlockPos blockPos = this.mc.player.getBlockPos();
      if (yaw1 >= 22.5 && yaw1 < 67.5) {
         blockPos = blockPos.south().west();
      } else if (yaw1 >= 67.5 && yaw1 < 112.5) {
         blockPos = blockPos.west();
      } else if (yaw1 >= 112.5 && yaw1 < 157.5) {
         blockPos = blockPos.north().west();
      } else if (yaw1 >= 157.5 && yaw1 < 202.5) {
         blockPos = blockPos.north();
      } else if (yaw1 >= 202.5 && yaw1 < 247.5) {
         blockPos = blockPos.north().east();
      } else if (yaw1 >= 247.5 && yaw1 < 292.5) {
         blockPos = blockPos.east();
      } else if (yaw1 >= 292.5 && yaw1 < 337.5) {
         blockPos = blockPos.south().east();
      } else {
         blockPos = blockPos.south();
      }

      FindItemResult resistantBlock = PlacementUtils.findResistantBlock();
      if (resistantBlock.found() && blockPos != null && !this.mc.world.getBlockState(blockPos.down()).isReplaceable()) {
         RotationUtils rotationManager = RotationUtils.getInstance();
         PlacementUtils.placeBlock(blockPos, true, true, true);
      }
   }

   private void performInventorySwapPVP(int pearlSlot) {
      this.mc.interactionManager.clickSlot(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, SlotActionType.PICKUP, this.mc.player);
      this.mc
         .interactionManager
         .clickSlot(0, ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() + 36, 0, SlotActionType.PICKUP, this.mc.player);
      this.mc.interactionManager.clickSlot(0, pearlSlot < 9 ? pearlSlot + 36 : pearlSlot, 0, SlotActionType.PICKUP, this.mc.player);
   }

   private void performAutoClip() {
      if (this.mc.player != null && this.mc.world != null) {
         double cos = Math.cos(Math.toRadians(this.mc.player.getYaw() + 90.0F));
         double sin = Math.sin(Math.toRadians(this.mc.player.getYaw() + 90.0F));
         double newX = this.mc.player.getX() + (Double)this.blocks.get() * cos;
         double newZ = this.mc.player.getZ() + (Double)this.blocks.get() * sin;
         this.mc.player.setPosition(newX, this.mc.player.getY(), newZ);
      }
   }

   private void performClipTick() {
      Vec3d center = this.mc.player.getBlockPos().toCenterPos();
      boolean flagX = center.x - this.mc.player.getX() > 0.0;
      boolean flagZ = center.z - this.mc.player.getZ() > 0.0;
      double x = center.x + 0.2 * (flagX ? -1 : 1);
      double z = center.z + 0.2 * (flagZ ? -1 : 1);
      this.mc.player.setPosition(x, this.mc.player.getY(), z);
   }

   private void performClipPhase() {
      this.performClipTick();
   }

   private void handleNormalMovement(PlayerMoveEvent event) {
      if (this.mc.player.isSneaking() && PlacementUtils.isPhasing()) {
         float yaw = this.mc.player.getYaw();
         double offsetX = (Double)this.distance.get() * Math.cos(Math.toRadians(yaw + 90.0F));
         double offsetZ = (Double)this.distance.get() * Math.sin(Math.toRadians(yaw + 90.0F));
         Box newBB = this.mc.player.getBoundingBox().offset(offsetX, 0.0, offsetZ);
         this.mc.player.setBoundingBox(newBB);
      }
   }

   private void handleSandMovement(PlayerMoveEvent event) {
      this.mc.player.noClip = true;
      double yMotion = 0.0;
      if (this.mc.options.jumpKey.isPressed()) {
         yMotion = 0.3;
      } else if (this.mc.options.sneakKey.isPressed()) {
         yMotion = -0.3;
      }

      event.movement = new Vec3d(event.movement.x, yMotion, event.movement.z);
   }

   private void handleClimbMovement(PlayerMoveEvent event) {
      if (this.mc.player.horizontalCollision) {
         double yMotion = event.movement.y;
         if (this.mc.options.jumpKey.isPressed()) {
            yMotion = 0.3;
         } else if (this.mc.options.sneakKey.isPressed()) {
            yMotion = -0.3;
         }

         event.movement = new Vec3d(event.movement.x, yMotion, event.movement.z);
      }
   }

   public String getInfoString() {
      return ((Phase.PhaseMode)this.mode.get()).toString();
   }

   public enum PhaseMode {
      Normal("Normal"),
      Sand("Sand"),
      Climb("Climb"),
      Pearl("Pearl"),
      Clip("Clip");

      private final String title;

      PhaseMode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
