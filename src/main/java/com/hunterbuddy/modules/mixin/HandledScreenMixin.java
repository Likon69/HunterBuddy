package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ShulkerOverviewModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    // Inject into drawSlot which has signature (DrawContext, Slot, int, int) in 1.21.11
    // The DrawContext is already translated to the GUI position, so slot.x/slot.y
    // are GUI-relative and will be drawn at the right screen position.
    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ShulkerOverviewModule module = (ShulkerOverviewModule) Modules.get().get(ShulkerOverviewModule.class);
        if (module == null || !module.isActive()) return;

        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;
        if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;

        module.renderShulkerOverlay(context, slot.x, slot.y, stack);
    }
}