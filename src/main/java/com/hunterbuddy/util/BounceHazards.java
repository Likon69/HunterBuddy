package com.hunterbuddy.util;

import java.util.Set;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;

public final class BounceHazards {
   private BounceHazards() {
   }

   public static Entity nearest(
      ClientWorld world, ClientPlayerEntity player, Vec3d heading, Set<EntityType<?>> types, double range, double width, double height
   ) {
      Vec3d origin = player.getEntityPos();
      Entity nearest = null;
      double nearestAhead = range;
      for (Entity entity : world.getEntities()) {
         if (!types.contains(entity.getType())) continue;
         Vec3d offset = entity.getEntityPos().subtract(origin);
         double ahead = offset.x * heading.x + offset.z * heading.z;
         double lateral = Math.abs(offset.x * heading.z - offset.z * heading.x);
         if (ahead <= 0.0 || ahead > nearestAhead) continue;
         if (lateral > width || Math.abs(offset.y) > height) continue;
         nearest = entity;
         nearestAhead = ahead;
      }
      return nearest;
   }
}
