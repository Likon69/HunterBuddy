package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

public final class TracerRenderer {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_CENTER_OFFSET = 8;
    private static final int CHUNK_SHIFT = 4;

    private final StashRenderSettings settings;
    private final TracerBoard board;

    public TracerRenderer(StashRenderSettings settings, TracerBoard board) {
        this.settings = settings;
        this.board = board;
    }

    public void render(Render3DEvent event) {
        if (board.isEmpty() || MeteorClient.mc.player == null) return;
        Vec3d viewer = MeteorClient.mc.player.getEntityPos();
        board.removeNear(viewer, settings.hideAtDistance.get());
        if (!settings.renderTracer.get() && !settings.renderColumn.get()) return;
        for (Vec3d point : board.all().values()) {
            if (TracerBoard.horizontalDistance(viewer, point) > settings.maxTraceDistance.get()) continue;
            if (settings.renderTracer.get()) drawLine(event, point);
            if (settings.renderColumn.get()) drawColumn(event.renderer, point);
        }
    }

    private void drawLine(Render3DEvent event, Vec3d point) {
        Vec3d from = RenderUtils.center;
        event.renderer.line(from.x, from.y, from.z, point.x, point.y, point.z, settings.tracerColor.get());
    }

    private void drawColumn(Renderer3D renderer, Vec3d point) {
        ClientWorld world = MeteorClient.mc.world;
        double west = ((int) point.x - CHUNK_CENTER_OFFSET >> CHUNK_SHIFT) << CHUNK_SHIFT;
        double north = ((int) point.z - CHUNK_CENTER_OFFSET >> CHUNK_SHIFT) << CHUNK_SHIFT;
        double bottom = world.getBottomY();
        double top = bottom + world.getHeight();
        Square square = new Square(west, north, west + CHUNK_SIZE, north + CHUNK_SIZE);
        Color color = settings.columnColor.get();
        verticalEdges(renderer, square, bottom, top, color);
        outline(renderer, square, bottom, color);
        outline(renderer, square, top, color);
    }

    private static void verticalEdges(Renderer3D renderer, Square s, double bottom, double top, Color color) {
        renderer.line(s.west(), bottom, s.north(), s.west(), top, s.north(), color);
        renderer.line(s.east(), bottom, s.north(), s.east(), top, s.north(), color);
        renderer.line(s.west(), bottom, s.south(), s.west(), top, s.south(), color);
        renderer.line(s.east(), bottom, s.south(), s.east(), top, s.south(), color);
    }

    private static void outline(Renderer3D renderer, Square s, double y, Color color) {
        renderer.line(s.west(), y, s.north(), s.east(), y, s.north(), color);
        renderer.line(s.east(), y, s.north(), s.east(), y, s.south(), color);
        renderer.line(s.east(), y, s.south(), s.west(), y, s.south(), color);
        renderer.line(s.west(), y, s.south(), s.west(), y, s.north(), color);
    }

    private record Square(double west, double north, double east, double south) {
    }
}
