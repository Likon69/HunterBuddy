package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GroundItemLabels {
    private static final int MAX_LABELS = 5;
    private static final double MIN_DISTANCE = 16.0;
    private static final double LABEL_LIFT = 1.0;
    private static final int PADDING = 2;
    private static final int TEXT_HEIGHT = 8;
    private static final int BACKGROUND = new Color(0, 0, 0, 150).getPacked();

    private final GroundItemSettings settings;

    public GroundItemLabels(GroundItemSettings settings) {
        this.settings = settings;
    }

    public void render(Render2DEvent event) {
        Vec3d player = MeteorClient.mc.player.getEntityPos();
        for (ItemEntity item : closestItems(player)) draw(event, item, player);
    }

    private List<ItemEntity> closestItems(Vec3d player) {
        List<ItemEntity> items = new ArrayList<>();
        for (Entity entity : MeteorClient.mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (!settings.trackedItems.get().contains(item.getStack().getItem())) continue;
            if (item.getEntityPos().distanceTo(player) > MIN_DISTANCE) items.add(item);
        }
        items.sort(Comparator.comparingDouble(item -> item.getEntityPos().squaredDistanceTo(player)));
        return items.subList(0, Math.min(MAX_LABELS, items.size()));
    }

    private void draw(Render2DEvent event, ItemEntity item, Vec3d player) {
        Vec3d at = item.getLerpedPos(event.tickDelta);
        Vector3d screen = new Vector3d(at.x, at.y + LABEL_LIFT, at.z);
        if (!NametagUtils.to2D(screen, 1.0)) return;
        String text = describe(item.getStack(), at.distanceTo(player));
        int width = MeteorClient.mc.textRenderer.getWidth(text);
        SettingColor color = settings.beamColor.get();
        NametagUtils.begin(screen, event.drawContext);
        event.drawContext.fill(
            -width / 2 - PADDING, -PADDING, width / 2 + PADDING, TEXT_HEIGHT + PADDING, BACKGROUND);
        event.drawContext.drawText(
            MeteorClient.mc.textRenderer, text, -width / 2, 0, new Color(color.r, color.g, color.b, 255).getPacked(), true);
        NametagUtils.end(event.drawContext);
    }

    private static String describe(ItemStack stack, double distance) {
        String count = stack.getCount() > 1 ? " x" + stack.getCount() : "";
        return stack.getName().getString() + count + " · " + Math.round(distance) + "m";
    }
}
