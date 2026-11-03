package com.hunterbuddy.modules.stash;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;

public final class FrameClassifier {
    private final StashDetectionSettings detection;

    public FrameClassifier(StashDetectionSettings detection) {
        this.detection = detection;
    }

    public StashType classify(ItemFrameEntity frame) {
        ItemStack held = frame.getHeldItemStack();
        if (detection.detects(StashType.MAP_ITEM_FRAME) && held.contains(DataComponentTypes.MAP_ID)) {
            return StashType.MAP_ITEM_FRAME;
        }
        return detection.detects(StashType.ITEM_FRAME) ? StashType.ITEM_FRAME : null;
    }
}
