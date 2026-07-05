package com.hunterbuddy.modules.regear.util;

public class PushOutOfBlocksEvent {
   private boolean canceled = false;

   public boolean isCanceled() {
      return this.canceled;
   }

   public void cancel() {
      this.canceled = true;
   }

   public void setCanceled(boolean canceled) {
      this.canceled = canceled;
   }
}
