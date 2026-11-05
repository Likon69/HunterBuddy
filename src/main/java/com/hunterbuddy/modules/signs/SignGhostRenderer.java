package com.hunterbuddy.modules.signs;

import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public final class SignGhostRenderer {
    private static final double LABEL_LIFT = 0.75;

    public void boxes(Render3DEvent event, List<SignRecord> ghosts, Vec3d viewer, double range, Color side, Color line, ShapeMode mode) {
        for (SignRecord ghost : ghosts) {
            if (inRange(ghost.key(), viewer, range)) event.renderer.box(ghost.key().pos(), side, line, mode, 0);
        }
    }

    public void labels(List<SignRecord> ghosts, Vec3d viewer, double range, Color color) {
        TextRenderer font = TextRenderer.get();
        Vector3d point = new Vector3d();
        font.begin(1, false, true);
        for (SignRecord ghost : ghosts) {
            SignKey key = ghost.key();
            if (!inRange(key, viewer, range)) continue;
            point.set(key.x() + 0.5, key.y() + LABEL_LIFT, key.z() + 0.5);
            if (NametagUtils.to2D(point, 1.0)) drawLines(font, ghost.visibleLines(), point.x, point.y, color);
        }
        font.end();
    }

    private static void drawLines(TextRenderer font, List<String> lines, double centerX, double centerY, Color color) {
        double lineHeight = font.getHeight(true);
        double y = centerY - lines.size() * lineHeight / 2;
        for (String line : lines) {
            font.render(line, centerX - font.getWidth(line, true) / 2, y, color, true);
            y += lineHeight;
        }
    }

    private static boolean inRange(SignKey key, Vec3d viewer, double range) {
        double dx = key.x() + 0.5 - viewer.x;
        double dy = key.y() + 0.5 - viewer.y;
        double dz = key.z() + 0.5 - viewer.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}
