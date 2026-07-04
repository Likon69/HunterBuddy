package com.hunterbuddy.modules.regear.util;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class PositionUtil {
   public static BlockPos getRoundedBlockPos(double x, double y, double z) {
      int flooredX = MathHelper.floor(x);
      int flooredY = (int)Math.round(y);
      int flooredZ = MathHelper.floor(z);
      return new BlockPos(flooredX, flooredY, flooredZ);
   }

   public static List<BlockPos> getAllInBox(Box box, BlockPos pos) {
      List<BlockPos> intersections = new ArrayList<>();

      for (int x = (int)Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
         for (int z = (int)Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
            intersections.add(new BlockPos(x, pos.getY(), z));
         }
      }

      return intersections;
   }

   public static List<BlockPos> getAllInBox(Box box) {
      List<BlockPos> intersections = new ArrayList<>();

      for (int x = (int)Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
         for (int y = (int)Math.floor(box.minY); y < Math.ceil(box.maxY); y++) {
            for (int z = (int)Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
               intersections.add(new BlockPos(x, y, z));
            }
         }
      }

      return intersections;
   }

   public static boolean isPhasing() {
      return MeteorClient.mc.player != null && MeteorClient.mc.world != null
         ? getAllInBox(MeteorClient.mc.player.getBoundingBox())
            .stream()
            .anyMatch(blockPos -> !MeteorClient.mc.world.getBlockState(blockPos).getCollisionShape(MeteorClient.mc.world, blockPos).isEmpty())
         : false;
   }
}
