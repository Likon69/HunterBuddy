package com.hunterbuddy.hud.text;

import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.gui.DrawContext;

public final class OverlayPass {
    private OverlayPass() { }

    public static void begin(DrawContext context) {
        Utils.unscaledProjection();
        context.createNewRootLayer();
        Renderer2D.COLOR.begin();
        VanillaTextRenderer.INSTANCE.scaleIndividually = true;
        VanillaTextRenderer.INSTANCE.begin();
        HudText.overlay(true);
    }

    public static void end(DrawContext context) {
        try {
            Renderer2D.COLOR.render();
            VanillaTextRenderer.INSTANCE.end();
        } finally {
            HudText.overlay(false);
            VanillaTextRenderer.INSTANCE.scaleIndividually = false;
            context.createNewRootLayer();
            Utils.scaledProjection();
        }
    }
}
