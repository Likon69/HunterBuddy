package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.PlacementUtils;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BlockListSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class MlepScaffold extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSpeed = this.settings.createGroup("Speed");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(new Builder().name("blocks").description("Selected blocks.").build());
   private final Setting<MlepScaffold.ListMode> blocksFilter = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MlepScaffold.ListMode>()
            .name("blocks-filter")
            .description("How to use the block list setting")
            .defaultValue(MlepScaffold.ListMode.Blacklist)
            .build()
      );
   private final Setting<Boolean> airPlace = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("air-place")
            .description("Allow air place.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> placeRange = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("closest-block-range")
            .description("How far scaffold can place blocks when air-place is off.")
            .defaultValue(4.0)
            .min(0.0)
            .sliderMax(8.0)
            .visible(() -> !this.airPlace.get())
            .build()
      );
   private final Setting<Boolean> tower = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("tower")
            .description("Automatically towers up when holding jump.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> towerSpeed = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("tower-speed")
            .description("The speed at which to tower.")
            .defaultValue(0.42)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .visible(this.tower::get)
            .build()
      );
   private final Setting<Boolean> onlyOnClick = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("only-on-click")
            .description("Only places blocks when holding right click.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> placeDelay = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("place-delay")
            .description("Delay in ticks between placements.")
            .defaultValue(0)
            .min(0)
            .max(10)
            .build()
      );
   private final Setting<Boolean> autoSwitch = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("auto-switch")
            .description("Automatically swaps to a block before placing.")
            .defaultValue(true)
            .build()
      );
   private final Setting<MlepScaffold.RotationMode> rotationMode = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MlepScaffold.RotationMode>()
            .name("rotation-mode")
            .description("Rotates towards blocks being placed in normal mode.")
            .defaultValue(MlepScaffold.RotationMode.Precise)
            .build()
      );
   private final Setting<Double> extendDistance = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("extend-distance")
            .description("How far ahead to place blocks when moving (regear mode).")
            .defaultValue(0.1)
            .min(0.0)
            .max(2.0)
            .sliderMax(2.0)
            .build()
      );
   private final Setting<Boolean> velocityPredict = this.sgSpeed
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("velocity-predict")
            .description("Predicts position based on velocity when falling fast.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> velocityMultiplier = this.sgSpeed
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("velocity-multiplier")
            .description("Multiplier for velocity prediction (regear uses 3.0).")
            .defaultValue(1.0)
            .min(0.5)
            .max(5.0)
            .sliderMax(5.0)
            .visible(this.velocityPredict::get)
            .build()
      );
   private final Setting<Boolean> adaptiveSpeed = this.sgSpeed
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("adaptive-speed")
            .description("Places more blocks per tick when falling fast (regear mode).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> maxBlocksPerTick = this.sgSpeed
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("max-blocks-per-tick")
            .description("Maximum blocks to place per tick when falling fast (regear mode).")
            .defaultValue(1)
            .min(1)
            .max(5)
            .visible(this.adaptiveSpeed::get)
            .build()
      );
   private final Setting<Boolean> safeWalk = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("safewalk")
            .description("Prevents you from walking off edges.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("render")
            .description("Renders blocks being placed.")
            .defaultValue(true)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("side-color")
            .description("Side color of rendered blocks.")
            .defaultValue(new SettingColor(197, 137, 232, 10))
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("line-color")
            .description("Line color of rendered blocks.")
            .defaultValue(new SettingColor(0, 1, 255))
            .visible(this.render::get)
            .build()
      );
   private static final double REACH = 4.5;
   private final Mutable bp = new Mutable();
   private final Mutable prevBp = new Mutable();
   private final List<BlockPos> renderedBlocks = new ArrayList<>();
   private int tickDelay = 0;
   private boolean regearMode = false;
   private int regearHotbarSlot = -1;

   public MlepScaffold() {
      super(HunterBuddyAddon.UTILITY_CATEGORY, "mlep-scaffold", "Places blocks under you using GrimAC bypass.");
   }

   public void onActivate() {
      this.tickDelay = 0;
      this.renderedBlocks.clear();
      this.bp.set(0, 0, 0);
      this.prevBp.set(0, 0, 0);
   }

   public void onDeactivate() {
      this.regearMode = false;
      this.regearHotbarSlot = -1;
      RotationUtils.getInstance().clearRotations();
   }

   public void setRegearMode(boolean enabled) {
      this.regearMode = enabled;
      this.tickDelay = 0;
      if (!enabled) {
         this.regearHotbarSlot = -1;
      }
   }

   public void setRegearHotbarSlot(int slot) {
      this.regearHotbarSlot = slot;
   }

   public boolean isRegearMode() {
      return this.regearMode;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.world == null) {
         return;
      }

      if ((Boolean) this.onlyOnClick.get() && !this.mc.options.useKey.isPressed()) {
         return;
      }

      if (this.regearMode) {
         this.handleRegearTick();
      } else {
         this.handleNormalTick();
      }
   }

   private void handleRegearTick() {
      FindItemResult blockItem = this.findRegearBlock();
      if (!blockItem.found()) {
         return;
      }

      if (!(Boolean) this.autoSwitch.get() && blockItem.getHand() == null) {
         return;
      }

      if ((Boolean) this.autoSwitch.get() && blockItem.getHand() == null) {
         InvUtils.swap(blockItem.slot(), true);
      }

      List<BlockPos> placementPositions = this.getRegearPlacementPositions();
      if (placementPositions.isEmpty()) {
         return;
      }

      int blocksToPlace = this.getRegearBlocksPerTick();
      int placed = 0;

      for (BlockPos pos : placementPositions) {
         if (placed >= blocksToPlace) {
            break;
         }

         BlockHitResult hit = this.getPlaceHit(pos);
         if (hit == null) {
            hit = PlacementUtils.resolvePlaceHit(pos, REACH);
         }

         if (hit != null) {
            float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), hit.getPos());
            RotationUtils.getInstance().setRotationFullInstant(rotations[0], rotations[1]);
            this.placeBlock(pos, hit);
            placed++;
         }
      }
   }

   private void handleNormalTick() {
      this.tickDelay++;
      if (this.tickDelay < (Integer) this.placeDelay.get()) {
         return;
      }

      this.updateNormalTargetPos();
      if (this.bp.getY() < this.mc.world.getBottomY() || this.bp.getY() > this.mc.world.getTopYInclusive()) {
         return;
      }

      FindItemResult blockItem = this.findNormalBlock(this.bp);
      if (!blockItem.found()) {
         return;
      }

      if (!(Boolean) this.autoSwitch.get() && blockItem.getHand() == null) {
         return;
      }

      if ((Boolean) this.autoSwitch.get() && blockItem.getHand() == null) {
         InvUtils.swap(blockItem.slot(), true);
      }

      boolean rotate = this.rotationMode.get() != MlepScaffold.RotationMode.None;
      if (BlockUtils.place(this.bp, blockItem, rotate, 50, true, true)) {
         this.renderedBlocks.add(this.bp.toImmutable());
         this.tickDelay = 0;
      }

      if (!this.mc.world.getBlockState(this.bp).isAir()) {
         this.prevBp.set(this.bp);
      }

      if ((Boolean) this.tower.get() && this.shouldTower()) {
         this.mc.player.setVelocity(
            this.mc.player.getVelocity().x, (Double) this.towerSpeed.get(), this.mc.player.getVelocity().z
         );
      }
   }

   private void updateNormalTargetPos() {
      if ((Boolean) this.airPlace.get()) {
         Vec3d vec = this.mc.player.getEntityPos().add(this.mc.player.getVelocity()).add(0.0, -0.5, 0.0);
         this.bp.set(vec.getX(), vec.getY(), vec.getZ());
         return;
      }

      if (BlockUtils.getPlaceSide(this.mc.player.getBlockPos().down()) != null) {
         this.bp.set(this.mc.player.getBlockPos().down());
         return;
      }

      Vec3d pos = this.mc.player.getEntityPos().add(0.0, -0.98, 0.0).add(this.mc.player.getVelocity());

      if (PlayerUtils.distanceTo(this.prevBp) > (Double) this.placeRange.get()) {
         List<BlockPos> solidBlocks = new ArrayList<>();
         double range = (Double) this.placeRange.get();

         for (int x = (int) (this.mc.player.getX() - range); x < this.mc.player.getX() + range; x++) {
            for (int z = (int) (this.mc.player.getZ() - range); z < this.mc.player.getZ() + range; z++) {
               for (
                  int y = (int) Math.max(this.mc.world.getBottomY(), this.mc.player.getY() - range);
                  y < Math.min(this.mc.world.getTopYInclusive(), this.mc.player.getY() + range);
                  y++
               ) {
                  this.bp.set(x, y, z);
                  if (!this.mc.world.getBlockState(this.bp).isAir()) {
                     solidBlocks.add(this.bp.toImmutable());
                  }
               }
            }
         }

         if (solidBlocks.isEmpty()) {
            return;
         }

         solidBlocks.sort(Comparator.comparingDouble(PlayerUtils::distanceTo));
         this.prevBp.set(solidBlocks.get(0));
      }

      Vec3d prevCenter = Vec3d.ofCenter(this.prevBp);
      Vec3d sub = pos.subtract(prevCenter);
      Direction facing;
      if (sub.getY() < -0.5) {
         facing = Direction.DOWN;
      } else if (sub.getY() > 0.5) {
         facing = Direction.UP;
      } else {
         facing = Direction.getFacing(sub.getX(), 0.0, sub.getZ());
      }

      this.bp.set(this.prevBp.offset(facing));
   }

   private List<BlockPos> getRegearPlacementPositions() {
      List<BlockPos> positions = new ArrayList<>();
      Vec3d playerPos = this.mc.player.getEntityPos();
      Vec3d velocity = this.mc.player.getVelocity();
      double multiplier = 3.0;
      Vec3d predictedPos = playerPos;

      if ((Boolean) this.velocityPredict.get()
         && (Math.abs(velocity.y) > 0.05 || velocity.horizontalLengthSquared() > 0.0025)) {
         predictedPos = playerPos.add(velocity.x * multiplier, velocity.y * multiplier, velocity.z * multiplier);
      }

      if (this.isMovingHorizontally() && (Double) this.extendDistance.get() > 0.0) {
         predictedPos = predictedPos.add(this.getMovementVector().multiply((Double) this.extendDistance.get()));
      }

      int baseY = MathHelper.floor(predictedPos.y) - 1;
      BlockPos base = BlockPos.ofFloored(predictedPos.getX(), baseY, predictedPos.getZ());

      int depth = 1;
      if ((Boolean) this.velocityPredict.get()) {
         double fallDepth = Math.abs(velocity.y) * multiplier * 1.5;
         depth = Math.min(4, Math.max(1, (int) Math.ceil(fallDepth) + 1));
      }

      Vec3d eye = this.mc.player.getEyePos();
      for (int d = 0; d < depth; d++) {
         BlockPos pos = base.down(d);
         if (eye.squaredDistanceTo(Vec3d.ofCenter(pos)) > REACH * REACH) {
            continue;
         }

         if (this.mc.world.getBlockState(pos).isReplaceable()) {
            positions.add(pos.toImmutable());
         }
      }

      return positions;
   }

   private Vec3d getMovementVector() {
      Vec3d velocity = Vec3d.ZERO;
      float yaw = this.mc.player.getYaw();
      if (this.mc.options.forwardKey.isPressed()) {
         velocity = velocity.add(Vec3d.fromPolar(0.0F, yaw));
      }

      if (this.mc.options.backKey.isPressed()) {
         velocity = velocity.add(Vec3d.fromPolar(0.0F, yaw + 180.0F));
      }

      if (this.mc.options.leftKey.isPressed()) {
         velocity = velocity.add(Vec3d.fromPolar(0.0F, yaw - 90.0F));
      }

      if (this.mc.options.rightKey.isPressed()) {
         velocity = velocity.add(Vec3d.fromPolar(0.0F, yaw + 90.0F));
      }

      return velocity.lengthSquared() > 0.0 ? velocity.normalize() : velocity;
   }

   private boolean isMovingHorizontally() {
      return this.mc.options.forwardKey.isPressed()
         || this.mc.options.backKey.isPressed()
         || this.mc.options.leftKey.isPressed()
         || this.mc.options.rightKey.isPressed();
   }

   private boolean shouldTower() {
      return this.mc.options.jumpKey.isPressed() && !this.mc.options.sneakKey.isPressed() && !this.isMovingHorizontally();
   }

   private void placeBlock(BlockPos pos, BlockHitResult hitResult) {
      PlacementUtils.placeHit(hitResult, Hand.MAIN_HAND, true);
      this.renderedBlocks.add(pos.toImmutable());
      this.tickDelay = 0;
   }

   private BlockHitResult getPlaceHit(BlockPos target) {
      Vec3d eye = this.mc.player.getEyePos();
      BlockHitResult best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction d : Direction.values()) {
         BlockPos against = target.offset(d);
         BlockState state = this.mc.world.getBlockState(against);
         if (!state.isReplaceable() && !state.getCollisionShape(this.mc.world, against).isEmpty()) {
            Direction side = d.getOpposite();
            Vec3d normal = Vec3d.of(side.getVector());
            Vec3d hitVec = Vec3d.ofCenter(against).add(normal.multiply(0.5));
            if (!(eye.subtract(hitVec).dotProduct(normal) <= 0.0)) {
               double dist = eye.squaredDistanceTo(hitVec);
               if (!(dist > REACH * REACH) && dist < bestDist) {
                  bestDist = dist;
                  best = new BlockHitResult(hitVec, side, against, false);
               }
            }
         }
      }

      return best;
   }

   private int getRegearBlocksPerTick() {
      double velocity = Math.abs(this.mc.player.getVelocity().y);
      if (velocity > 0.45) {
         return 3;
      }

      return velocity > 0.25 ? 2 : 1;
   }

   private FindItemResult findRegearBlock() {
      if (this.regearHotbarSlot >= 0 && this.regearHotbarSlot < 9) {
         ItemStack stack = this.mc.player.getInventory().getStack(this.regearHotbarSlot);
         if (this.isValidRegearBlock(stack)) {
            return InvUtils.findInHotbar(itemStack -> itemStack == stack);
         }

         return InvUtils.findInHotbar(itemStack -> false);
      }

      return InvUtils.findInHotbar(itemStack -> this.isValidRegearBlock(itemStack));
   }

   private FindItemResult findNormalBlock(BlockPos pos) {
      return InvUtils.findInHotbar(itemStack -> this.isValidNormalBlock(itemStack, pos));
   }

   private boolean isValidNormalBlock(ItemStack itemStack, BlockPos pos) {
      if (!(itemStack.getItem() instanceof BlockItem blockItem)) {
         return false;
      }

      Block block = blockItem.getBlock();
      if (this.blocksFilter.get() == MlepScaffold.ListMode.Blacklist && ((List) this.blocks.get()).contains(block)) {
         return false;
      }

      if (this.blocksFilter.get() == MlepScaffold.ListMode.Whitelist && !((List) this.blocks.get()).contains(block)) {
         return false;
      }

      if (!Block.isShapeFullCube(block.getDefaultState().getCollisionShape(this.mc.world, pos))) {
         return false;
      }

      return !(block instanceof FallingBlock) || !FallingBlock.canFallThrough(this.mc.world.getBlockState(pos));
   }

   private boolean isValidRegearBlock(ItemStack stack) {
      if (!(stack.getItem() instanceof BlockItem blockItem) || stack.isEmpty()) {
         return false;
      }

      if (stack.getItem() == Items.ENDER_CHEST) {
         return false;
      }

      Block block = blockItem.getBlock();
      return block.getDefaultState().isSolidBlock(this.mc.world, BlockPos.ORIGIN);
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean) this.render.get() && !this.renderedBlocks.isEmpty()) {
         this.renderedBlocks.removeIf(pos -> this.mc.world.getBlockState(pos).isReplaceable());

         for (BlockPos pos : this.renderedBlocks) {
            event.renderer.box(pos, (Color) this.sideColor.get(), (Color) this.lineColor.get(), (ShapeMode) this.shapeMode.get(), 0);
         }
      }
   }

   public boolean isSafeWalking() {
      return this.isActive() && (Boolean) this.safeWalk.get();
   }

   public enum ListMode {
      Whitelist,
      Blacklist;
   }

   public enum RotationMode {
      None,
      Simple,
      Precise;
   }
}