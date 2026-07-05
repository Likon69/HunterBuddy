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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Inject(method = "drawForeground", at = @At("TAIL"))
    private void onDrawForeground(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        ShulkerOverviewModule module = (ShulkerOverviewModule) Modules.get().get(ShulkerOverviewModule.class);
        if (module == null || !module.isActive()) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                // slot.x and slot.y are relative to the screen's top-left
                // We draw at absolute screen coordinates
                int slotX = this.x + slot.x;
                int slotY = this.y + slot.y;
                module.renderShulkerOverlay(context, slotX, slotY, stack);
            }
        }
    }
}