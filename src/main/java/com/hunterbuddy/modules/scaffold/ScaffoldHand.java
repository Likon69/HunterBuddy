package com.hunterbuddy.modules.scaffold;

import com.hunterbuddy.util.ActionSwap;
import meteordevelopment.meteorclient.utils.player.FindItemResult;

final class ScaffoldHand {
    private ScaffoldHand() {}

    static void run(FindItemResult block, boolean autoSwitch, Runnable action) {
        if (block.isOffhand() || block.isMainHand()) action.run();
        else if (autoSwitch && block.isHotbar()) ActionSwap.run(block.slot(), false, action);
    }
}
