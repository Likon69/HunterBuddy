package com.hunterbuddy.modules.mixin.accessors;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSignEditScreen.class)
public interface SignEditScreenAccessor {
    @Accessor("blockEntity")
    SignBlockEntity getSign();

    @Accessor("front")
    boolean isFront();
}
