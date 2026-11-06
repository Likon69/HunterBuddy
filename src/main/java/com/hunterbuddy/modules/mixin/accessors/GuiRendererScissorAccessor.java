package com.hunterbuddy.modules.mixin.accessors;

import it.unimi.dsi.fastutil.Stack;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.Scissor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiRenderer.class, remap = false)
public interface GuiRendererScissorAccessor {
    @Accessor(value = "scissorStack", remap = false)
    Stack<Scissor> hunterbuddy$getScissorStack();
}
