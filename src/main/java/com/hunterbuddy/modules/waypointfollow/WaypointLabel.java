package com.hunterbuddy.modules.waypointfollow;

import com.hunterbuddy.modules.WaypointFollower;
import com.hunterbuddy.modules.WaypointFollower.HudState;
import com.hunterbuddy.util.TravelFormat;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class WaypointLabel {
    private static final double LABEL_LIFT = 2.0;
    private static final int PADDING = 2;
    private static final int LINE_HEIGHT = 12;
    private static final int TEXT_HEIGHT = 8;
    private static final int BACKGROUND = new Color(0, 0, 0, 150).getPacked();

    private WaypointLabel() {
    }

    public static void render(
        Render2DEvent event, WaypointFollower follower, double scale, SettingColor color, boolean showFinal) {
        if (!hasMeaningfulTarget(follower)) return;
        Vec3d target = follower.hudTargetPos();
        if (target == null) return;
        Vector3d screen = new Vector3d(target.x, target.y + LABEL_LIFT, target.z);
        if (!NametagUtils.to2D(screen, scale)) return;
        draw(event.drawContext, screen, lines(follower, showFinal), color);
    }

    private static boolean hasMeaningfulTarget(WaypointFollower follower) {
        HudState state = follower.hudState();
        return state != HudState.RECORDING
            && state != HudState.LOADING
            && state != HudState.PAUSED
            && state != HudState.NO_WAYPOINTS;
    }

    private static List<String> lines(WaypointFollower follower, boolean showFinal) {
        List<String> lines = new ArrayList<>();
        lines.add(describe(follower.hudDistance(), follower.hudEtaSeconds()));
        boolean hasFinal = showFinal && !follower.hudClosestMode() && follower.hudRemaining() > 1;
        if (hasFinal) lines.add("Final: " + describe(follower.hudRouteDistance(), follower.hudRouteEtaSeconds()));
        return lines;
    }

    private static String describe(double distance, int etaSeconds) {
        String eta = etaSeconds < 0 ? "--" : TravelFormat.formatTime(TravelFormat.quantiseEta(etaSeconds));
        return TravelFormat.formatDistance(distance) + " - " + eta;
    }

    private static void draw(DrawContext context, Vector3d screen, List<String> lines, SettingColor color) {
        int packed = new Color(color.r, color.g, color.b, 255).getPacked();
        NametagUtils.begin(screen, context);
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int width = MeteorClient.mc.textRenderer.getWidth(text);
            int top = i * LINE_HEIGHT;
            context.fill(-width / 2 - PADDING, top - PADDING, width / 2 + PADDING, top + TEXT_HEIGHT + PADDING, BACKGROUND);
            context.drawText(MeteorClient.mc.textRenderer, text, -width / 2, top, packed, true);
        }
        NametagUtils.end(context);
    }
}
