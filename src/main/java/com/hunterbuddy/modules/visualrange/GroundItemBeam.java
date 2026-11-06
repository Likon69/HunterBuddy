package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;

public final class GroundItemBeam {
    private static final double CORE_HALF_WIDTH = 0.06;
    private static final double HALO_HALF_WIDTH = 0.3;
    private static final double HALO_ALPHA_SCALE = 0.12;
    private static final double SOLID_HEIGHT = 40.0;
    private static final double BASE_OFFSET = -0.5;
    private static final double CUBE_HALF_SIZE = 0.2;
    private static final double CUBE_LIFT = 0.1;

    private final GroundItemSettings settings;

    public GroundItemBeam(GroundItemSettings settings) {
        this.settings = settings;
    }

    public void render(Render3DEvent event) {
        Renderer3D renderer = settings.throughWalls.get() ? event.renderer : event.depthRenderer;
        SettingColor color = settings.beamColor.get();
        Color halo = new Color(color.r, color.g, color.b, (int) Math.round(color.a * HALO_ALPHA_SCALE));
        for (Entity entity : MeteorClient.mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (!settings.trackedItems.get().contains(item.getStack().getItem())) continue;
            Vec3d at = item.getLerpedPos(event.tickDelta);
            drawBeam(renderer, at, CORE_HALF_WIDTH, color);
            drawBeam(renderer, at, HALO_HALF_WIDTH, halo);
            drawCube(renderer, at, color);
        }
    }

    private void drawBeam(Renderer3D renderer, Vec3d at, double halfWidth, Color color) {
        double bottom = at.y + BASE_OFFSET;
        double top = at.y + settings.beamHeight.get();
        double solidTop = Math.min(at.y + SOLID_HEIGHT, top);
        drawCrossedPlanes(renderer, at, halfWidth, bottom, solidTop, color, color);
        if (solidTop < top) {
            Color clear = new Color(color.r, color.g, color.b, 0);
            drawCrossedPlanes(renderer, at, halfWidth, solidTop, top, clear, color);
        }
    }

    private static void drawCrossedPlanes(
        Renderer3D renderer, Vec3d at, double halfWidth, double bottom, double top, Color topColor, Color bottomColor
    ) {
        renderer.gradientQuadVertical(
            at.x - halfWidth, bottom, at.z - halfWidth, at.x + halfWidth, top, at.z + halfWidth, topColor, bottomColor);
        renderer.gradientQuadVertical(
            at.x - halfWidth, bottom, at.z + halfWidth, at.x + halfWidth, top, at.z - halfWidth, topColor, bottomColor);
    }

    private static void drawCube(Renderer3D renderer, Vec3d at, Color color) {
        double y = at.y + CUBE_LIFT;
        renderer.box(
            at.x - CUBE_HALF_SIZE, y, at.z - CUBE_HALF_SIZE,
            at.x + CUBE_HALF_SIZE, y + 2 * CUBE_HALF_SIZE, at.z + CUBE_HALF_SIZE,
            color, color, ShapeMode.Both, 0);
    }
}
