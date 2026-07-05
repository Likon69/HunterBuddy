package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.mixin.accessor.ClientPlayerInteractionManagerAccessor;
import com.hunterbuddy.modules.regear.util.PlacementUtils;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;

public class AutoPortal extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final List<BlockPos> waitingForBreak = new ArrayList<>();
   private final Setting<Integer> placeDelay = this.sgGeneral
      .add(
         new Builder().name("place-delay").description("Ticks between each obsidian placement.").defaultValue(1)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Integer> blocksPerTick = this.sgGeneral
      .add(
         new Builder().name("blocks-per-tick").description("How many blocks to place each tick.").defaultValue(1)
            .sliderRange(1, 5)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate")
                  .description("Smoothly rotates toward each block before placing (GrimAC bypass).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> render = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render")
                  .description("Renders the portal frame as it's being placed.")
               .defaultValue(true)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
                     .name("shape-mode")
                  .description("How the box is rendered.")
               .defaultValue(ShapeMode.Both)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgGeneral
      .add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("side-color")
            .defaultValue(new SettingColor(100, 100, 255, 10)).build()
      );
   private final Setting<SettingColor> lineColor = this.sgGeneral
      .add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("line-color")
            .defaultValue(new SettingColor(100, 100, 255, 255)).build()
      );
   private static final double REACH = 4.5;
   private static final int PLACE_CONFIRM_TICKS = 5;
   private static final int MAX_LIGHT_ATTEMPTS = 10;
   private final List<BlockPos> portalBlocks = new ArrayList<>();
   private final List<BlockPos> interiorBlocks = new ArrayList<>();
   private final Map<BlockPos, Integer> placeCooldown = new HashMap<>();
   private int delay = 0;
   private int lightCooldown = 0;
   private int lightAttempts = 0;

   public AutoPortal() {
      super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "auto-portal", "For the Base Hunter who has places to be. By Stash Hunt Addon (Jeff)");
   }

   public void onActivate() {
      int obsidianCount = 0;

      for (int i = 0; i < 36; i++) {
         if (this.mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN) {
            obsidianCount += this.mc.player.getInventory().getStack(i).getCount();
         }
      }

      if (obsidianCount < 10) {
         this.error("Not enough obsidian to build the portal (need at least 10)!", new Object[0]);
         this.toggle();
      } else {
         this.portalBlocks.clear();
         this.interiorBlocks.clear();
         this.placeCooldown.clear();
         this.waitingForBreak.clear();
         this.delay = 0;
         this.lightCooldown = 0;
         this.lightAttempts = 0;
         Direction forward = this.mc.player.getHorizontalFacing();
         Direction right = forward.rotateYClockwise();
         BlockPos standingPos = this.mc.player.getBlockPos();
         BlockPos blockBelow = standingPos.down();
         double blockHeight = this.mc.world.getBlockState(blockBelow).getCollisionShape(this.mc.world, blockBelow).getMax(Axis.Y);
         if (blockHeight < 1.0) {
            standingPos = standingPos.up();
         }

         BlockPos base = standingPos.offset(forward, 2).offset(right, -1);
         int obsidianCheck = 0;
         List<BlockPos> checkPositions = List.of(
            base.offset(right, 1),
            base.offset(right, 2),
            base.offset(right, 0).up(1),
            base.offset(right, 0).up(2),
            base.offset(right, 0).up(3),
            base.offset(right, 3).up(1),
            base.offset(right, 3).up(2),
            base.offset(right, 3).up(3),
            base.offset(right, 1).up(4),
            base.offset(right, 2).up(4)
         );

         for (BlockPos checkPos : checkPositions) {
            if (this.mc.world.getBlockState(checkPos).getBlock().asItem() == Items.OBSIDIAN) {
               obsidianCheck++;
            }
         }

         if (obsidianCheck >= checkPositions.size()) {
            this.error("A portal already exists here!", new Object[0]);
            this.toggle();
         } else {
            this.portalBlocks.add(base.offset(right, 1));
            this.portalBlocks.add(base.offset(right, 2));

            for (int i = 1; i <= 3; i++) {
               this.portalBlocks.add(base.offset(right, 0).up(i));
            }

            for (int i = 1; i <= 3; i++) {
               this.portalBlocks.add(base.offset(right, 3).up(i));
            }

            this.portalBlocks.add(base.offset(right, 1).up(4));
            this.portalBlocks.add(base.offset(right, 2).up(4));

            for (int i = 1; i <= 3; i++) {
               this.interiorBlocks.add(base.offset(right, 1).up(i));
               this.interiorBlocks.add(base.offset(right, 2).up(i));
            }

            for (int i = 0; i < 9; i++) {
               if (this.mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN) {
                  this.mc.player.getInventory().setSelectedSlot(i);
                  break;
               }
            }
         }
      }
   }

   public void onDeactivate() {
      this.portalBlocks.clear();
      this.interiorBlocks.clear();
      this.placeCooldown.clear();
      this.waitingForBreak.clear();
      this.delay = 0;
      this.lightCooldown = 0;
      this.lightAttempts = 0;
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player != null && this.mc.world != null && !this.portalBlocks.isEmpty()) {
         this.tickCooldowns();
         List<BlockPos> missing = this.getMissingBlocks();
         if (missing.isEmpty()) {
            this.tryFinishPortal();
         } else if (!this.hasObsidianSelected() && !this.selectObsidian()) {
            this.error("Ran out of obsidian!", new Object[0]);
            this.toggle();
         } else if (this.hasObsidianSelected()) {
            this.delay++;
            if (this.delay >= (Integer)this.placeDelay.get()) {
               int toPlace = this.rotate.get() ? 1 : (Integer)this.blocksPerTick.get();
               int placed = 0;

               for (BlockPos pos : missing) {
                  if (placed >= toPlace) {
                     break;
                  }

                  if (!this.placeCooldown.containsKey(pos)) {
                     if (!this.mc.world.getBlockState(pos).isReplaceable()) {
                        if (!this.waitingForBreak.contains(pos) && this.mc.interactionManager != null) {
                           this.mc.interactionManager.attackBlock(pos, Direction.UP);
                           this.mc.player.swingHand(Hand.MAIN_HAND);
                           this.waitingForBreak.add(pos);
                        }
                     } else {
                        this.waitingForBreak.remove(pos);
                        BlockHitResult hit = PlacementUtils.getAirPlaceHit(pos, 4.5);
                        if (hit != null) {
                           if ((Boolean)this.rotate.get()) {
                              float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), hit.getPos());
                              RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1]);
                              if (!RotationUtils.getInstance().isAligned()) {
                                 return;
                              }
                           }

                           this.placeObsidian(hit);
                           this.placeCooldown.put(pos, 5);
                           placed++;
                        }
                     }
                  }
               }

               if (placed > 0) {
                  this.delay = 0;
               }
            }
         }
      }
   }

   private void tickCooldowns() {
      Iterator<Entry<BlockPos, Integer>> it = this.placeCooldown.entrySet().iterator();

      while (it.hasNext()) {
         Entry<BlockPos, Integer> e = it.next();
         int v = e.getValue() - 1;
         if (v <= 0) {
            it.remove();
         } else {
            e.setValue(v);
         }
      }
   }

   private List<BlockPos> getMissingBlocks() {
      List<BlockPos> missing = new ArrayList<>();

      for (BlockPos pos : this.portalBlocks) {
         if (this.mc.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) {
            missing.add(pos);
         }
      }

      return missing;
   }

   private boolean hasObsidianSelected() {
      return this.mc.player.getMainHandStack().getItem() == Items.OBSIDIAN;
   }

   private boolean selectObsidian() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN) {
            this.mc.player.getInventory().setSelectedSlot(i);
            ((ClientPlayerInteractionManagerAccessor) this.mc.interactionManager).invokeSyncSelectedSlot();
            return true;
         }
      }

      return false;
   }

   private void tryFinishPortal() {
      if (this.isPortalLit()) {
         this.info("Portal complete. AutoPortal disabled.", new Object[0]);
         this.toggle();
      } else if (this.lightCooldown > 0) {
         this.lightCooldown--;
      } else if (this.lightAttempts >= 10) {
         this.error("Failed to light the portal (interior may be obstructed).", new Object[0]);
         this.toggle();
      } else {
         int slot = this.findFlintAndSteel();
         if (slot == -1) {
            this.error("No flint and steel to light the portal!", new Object[0]);
            this.toggle();
         } else {
            this.mc.player.getInventory().setSelectedSlot(slot);
            ((ClientPlayerInteractionManagerAccessor) this.mc.interactionManager).invokeSyncSelectedSlot();
            if (this.mc.player.getMainHandStack().getItem() == Items.FLINT_AND_STEEL) {
               BlockPos fireBlock = this.portalBlocks.get(0);
               Vec3d hitVec = Vec3d.ofCenter(fireBlock).add(0.0, 0.5, 0.0);
               if ((Boolean)this.rotate.get()) {
                  float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), hitVec);
                  RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1]);
                  if (!RotationUtils.getInstance().isAligned()) {
                     return;
                  }
               }

               this.lightPortal(fireBlock, hitVec);
               this.lightAttempts++;
               this.lightCooldown = 5;
            }
         }
      }
   }

   private boolean isPortalLit() {
      for (BlockPos pos : this.interiorBlocks) {
         if (this.mc.world.getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL) {
            return true;
         }
      }

      return false;
   }

   private int findFlintAndSteel() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).getItem() == Items.FLINT_AND_STEEL) {
            return i;
         }
      }

      return -1;
   }

   private void placeObsidian(BlockHitResult hit) {
      PlacementUtils.grimPlace(hit);
      this.mc.player.swingHand(Hand.MAIN_HAND);
   }

   private void lightPortal(BlockPos fireBlock, Vec3d hitVec) {
      BlockHitResult fireHit = new BlockHitResult(hitVec, Direction.UP, fireBlock, false);
      this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, fireHit);
      this.mc.player.swingHand(Hand.MAIN_HAND);
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get()) {
         for (BlockPos pos : this.portalBlocks) {
            if (this.mc.world == null || this.mc.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) {
               event.renderer.box(pos, (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
            }
         }
      }
   }
}
