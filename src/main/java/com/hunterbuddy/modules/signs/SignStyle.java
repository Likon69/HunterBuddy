package com.hunterbuddy.modules.signs;

import net.minecraft.block.entity.SignText;
import net.minecraft.util.DyeColor;

public record SignStyle(String color, boolean glowing) {
    public static SignStyle of(SignText text) {
        return new SignStyle(text.getColor().getId(), text.isGlowing());
    }

    public DyeColor dye() {
        return DyeColor.byId(color, null);
    }
}
