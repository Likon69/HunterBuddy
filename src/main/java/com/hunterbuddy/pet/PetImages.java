package com.hunterbuddy.pet;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

final class PetImages {
    private PetImages() {
    }

    static void draw(DrawContext context, Identifier texture, int x, int y, int width, int height, int direction, int color) {
        boolean mirrored = direction < 0;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, mirrored ? -x - width : x, y,
            mirrored ? width : 0, 0, width, height, mirrored ? -width : width, height, width, height, color);
    }
}
