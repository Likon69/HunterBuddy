package com.hunterbuddy.hud.cards;

import java.util.function.Consumer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;

public final class CardIcons {
    private static final int NATIVE_SIZE = 16;

    private CardIcons() {
    }

    public static void head(HudRenderer renderer, SkinTextures skin, double x, double y, double size, double alpha) {
        int color = (int) Math.round(255 * Math.max(0, Math.min(1, alpha))) << 24 | 0xFFFFFF;
        place(renderer, x, y, size, context -> PlayerSkinDrawer.draw(context, skin, 0, 0, NATIVE_SIZE, color));
    }

    public static void item(HudRenderer renderer, ItemStack stack, double x, double y, double size) {
        place(renderer, x, y, size, context -> context.drawItem(stack, 0, 0));
    }

    private static void place(HudRenderer renderer, double x, double y, double size, Consumer<DrawContext> draw) {
        renderer.post(() -> {
            DrawContext context = renderer.drawContext;
            double gui = MeteorClient.mc.getWindow().getScaleFactor();
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            try {
                matrices.translate((float) (x / gui), (float) (y / gui));
                matrices.scale((float) (size / (NATIVE_SIZE * gui)));
                draw.accept(context);
            } finally {
                matrices.popMatrix();
            }
        });
    }
}
