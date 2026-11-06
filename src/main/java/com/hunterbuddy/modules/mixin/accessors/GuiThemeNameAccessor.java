package com.hunterbuddy.modules.mixin.accessors;

import meteordevelopment.meteorclient.gui.GuiTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiTheme.class, remap = false)
public interface GuiThemeNameAccessor {
    @Mutable
    @Accessor(value = "name", remap = false)
    void hunterbuddy$setName(String name);
}
