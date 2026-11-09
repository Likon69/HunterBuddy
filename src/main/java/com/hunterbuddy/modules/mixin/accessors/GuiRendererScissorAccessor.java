package com.hunterbuddy.modules.mixin.accessors;

import it.unimi.dsi.fastutil.Stack;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.Scissor;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiRenderer.class, remap = false)
public interface GuiRendererScissorAccessor {
    @Accessor(value = "drawContext", remap = false)
    DrawContext hunterbuddy$getDrawContext();

    @Accessor(value = "scissorStack", remap = false)
    Stack<Scissor> hunterbuddy$getScissorStack();
}
