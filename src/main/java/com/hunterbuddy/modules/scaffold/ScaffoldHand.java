package com.hunterbuddy.modules.scaffold;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;

final class ScaffoldHand {
    private ScaffoldHand() {
    }

    static boolean ready(FindItemResult block, boolean autoSwitch) {
        boolean inHand = block.isOffhand() || block.isMainHand();
        if (inHand) return true;
        if (!autoSwitch) return false;
        InvUtils.swap(block.slot(), true);
        return true;
    }
}
