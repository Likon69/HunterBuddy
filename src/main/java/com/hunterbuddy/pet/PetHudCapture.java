package com.hunterbuddy.pet;

import com.hunterbuddy.modules.mixin.accessors.PetHudAccess;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

public final class PetHudCapture {
    private static boolean capturing;

    private PetHudCapture() {
    }

    public static void begin() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.begin(MeteorClient.mc.getWindow().getFramebufferWidth(), MeteorClient.mc.getWindow().getFramebufferHeight());
        capturing = surfaces.enabled && !MeteorClient.mc.options.hudHidden;
    }

    public static void texture(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        if (!capturing || !texture.getNamespace().equals("minecraft")) return;
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        String path = texture.getPath();
        Matrix3x2f matrix = context.getMatrices();
        float scale = MeteorClient.mc.getWindow().getScaleFactor();
        float left = (matrix.m00() * x + matrix.m20()) * scale;
        float right = (matrix.m00() * (x + width) + matrix.m20()) * scale;
        int kind;
        if (path.equals("hud/hotbar")) {
            surfaces.excludeFloor(left - surfaces.petSize * 0.4f, right + surfaces.petSize * 0.4f);
            if (!surfaces.hotbar) return;
            kind = 1;
        }
        else if (surfaces.bars && (path.startsWith("hud/heart/") || path.startsWith("hud/food_")
            || path.startsWith("hud/armor_") || path.startsWith("hud/experience_bar"))) kind = 2;
        else return;
        surfaces.add(left, right,
            (matrix.m11() * y + matrix.m21()) * scale, kind);
    }

    public static void finish() {
        capturing = false;
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        if (!surfaces.enabled || !surfaces.meteor || MeteorClient.mc.options.hudHidden || MeteorClient.mc.debugHudEntryList.isF3Enabled()) return;
        Hud hud = Hud.get();
        if (hud == null || !hud.active || ((PetHudAccess) hud).hunterbuddy$shouldHideHud()) return;
        List<HudElement> elements = ((PetHudAccess) hud).hunterbuddy$elements();
        for (int i = 0; i < elements.size(); i++) {
            HudElement element = elements.get(i);
            if (!element.isActive() || element.getWidth() <= 0 || element.getHeight() <= 0) continue;
            surfaces.add(element.box.getRenderX(), element.box.getRenderX() + element.getWidth(), element.box.getRenderY(), 3);
        }
    }
}
