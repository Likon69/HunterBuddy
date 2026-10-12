package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ShulkerOverview;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ShulkerHotbarOverlayMixin {
    @Inject(method = "renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
    private void hunterBuddy$drawHotbarShulkerOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;

        ShulkerOverview overview = modules.get(ShulkerOverview.class);
        if (overview == null || !overview.isActive()) return;

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        int center = context.getScaledWindowWidth() / 2;
        int row = context.getScaledWindowHeight() - 19;

        for (int i = 0; i < 9; i++) {
            overview.drawOverlay(context, center - 90 + i * 20 + 2, row, player.getInventory().getStack(i));
        }

        ItemStack offHand = player.getOffHandStack();
        if (!offHand.isEmpty()) {
            int x = player.getMainArm() == Arm.RIGHT ? center - 91 - 26 : center + 91 + 10;
            overview.drawOverlay(context, x, row, offHand);
        }
    }
}
