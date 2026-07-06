package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

public class NoJumpDelay extends Module {
   public NoJumpDelay() {
      super(HunterBuddyAddon.UTILITY_CATEGORY, "NoJumpDelay", "Removes the delay between jumps.");
   }
}
