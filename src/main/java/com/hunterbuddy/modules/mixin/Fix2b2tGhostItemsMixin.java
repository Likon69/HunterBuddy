package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.InvFix2b2t;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.BannerItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.screen.ScreenHandler;

@Mixin(HandledScreen.class)
public class Fix2b2tGhostItemsMixin<T extends ScreenHandler> {
    @Shadow
    @Final
    protected T handler;

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    public void hunterBuddy$onMouseDragged(CallbackInfoReturnable<Boolean> cir) {
        InvFix2b2t module = Modules.get().get(InvFix2b2t.class);
        if (module == null || !module.isActive() || !module.fixGhostItems.get()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.isCreative()) return;

        ItemStack cursorStack = handler.getCursorStack();
        if (cursorStack == null || cursorStack.isEmpty()) return;

        if (!cursorStack.isStackable() || cursorStack.getItem() instanceof FilledMapItem || cursorStack.getItem() instanceof BannerItem) {
            cir.setReturnValue(true);
        }
    }
}
