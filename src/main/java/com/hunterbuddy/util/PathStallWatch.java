package com.hunterbuddy.util;

public final class PathStallWatch {
   private static final int STALL_TICKS = 20;
   private static final double MOVE_EPSILON = 0.25;
   private double anchorX;
   private double anchorZ;
   private boolean anchored;
   private int stillTicks;

   public boolean tick(double x, double z, boolean onGround, boolean busy) {
      if (!onGround || busy) {
         this.reset();
         return false;
      }
      if (!this.anchored || Math.hypot(x - this.anchorX, z - this.anchorZ) > MOVE_EPSILON) {
         this.anchorX = x;
         this.anchorZ = z;
         this.anchored = true;
         this.stillTicks = 0;
         return false;
      }
      this.stillTicks++;
      if (this.stillTicks >= STALL_TICKS) {
         this.reset();
         return true;
      }
      return false;
   }

   public void reset() {
      this.anchored = false;
      this.stillTicks = 0;
   }
}
