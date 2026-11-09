package com.hunterbuddy.modules.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.utils.player.TitleScreenCredits;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TitleScreenCredits.class, remap = false)
public abstract class TitleScreenCreditsMixin {
    @Unique private static final Identifier HUNTER_BUDDY$BEE = Identifier.of("hunterbuddy", "textures/gui/bee.png");
    @Unique private static final int HUNTER_BUDDY$BEE_SOURCE_SIZE = 112;

    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V",
            remap = true
        )
    )
    private static void hunterBuddy$beeBeforeLine(DrawContext context, TextRenderer renderer, Text text, int x, int y, int color, Operation<Void> original) {
        if (hunterBuddy$isOwnLine(text)) {
            int size = 2 * renderer.fontHeight + 2;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, HUNTER_BUDDY$BEE, x - size - 2, y, 0.0F, 0.0F, size, size,
                HUNTER_BUDDY$BEE_SOURCE_SIZE, HUNTER_BUDDY$BEE_SOURCE_SIZE, HUNTER_BUDDY$BEE_SOURCE_SIZE, HUNTER_BUDDY$BEE_SOURCE_SIZE);
        }
        original.call(context, renderer, text, x, y, color);
    }

    @Unique
    private static boolean hunterBuddy$isOwnLine(Text text) {
        for (MeteorAddon addon : AddonManager.ADDONS) {
            if (addon instanceof com.hunterbuddy.HunterBuddyAddon) {
                return text.getString().startsWith(addon.name + " by ");
            }
        }
        return false;
    }
}
