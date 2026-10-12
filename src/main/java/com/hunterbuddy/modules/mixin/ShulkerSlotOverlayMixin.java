package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ShulkerOverview;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class ShulkerSlotOverlayMixin {
    @Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V", at = @At("TAIL"))
    private void hunterBuddy$drawShulkerOverlay(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;

        ShulkerOverview overview = modules.get(ShulkerOverview.class);
        if (overview != null && overview.isActive()) {
            overview.drawOverlay(context, slot.x, slot.y, slot.getStack());
        }
    }
}
