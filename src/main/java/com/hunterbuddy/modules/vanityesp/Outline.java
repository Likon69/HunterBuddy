package com.hunterbuddy.modules.vanityesp;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class Outline {
    private Outline() {
    }

    static void box(Render3DEvent event, Box box, FeatureStyle style) {
        event.renderer.box(box, style.sideColor.get(), style.lineColor.get(), style.shapeMode(), 0);
    }

    static void block(Render3DEvent event, BlockPos pos, FeatureStyle style) {
        event.renderer.box(pos, style.sideColor.get(), style.lineColor.get(), style.shapeMode(), 0);
    }

    static void tracer(Render3DEvent event, Vec3d target, FeatureStyle style) {
        if (!style.tracers.get()) return;
        event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, target.x, target.y, target.z,
            style.tracerColor.get());
    }
}
