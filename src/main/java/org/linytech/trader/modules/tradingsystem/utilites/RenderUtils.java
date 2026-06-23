/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.utilites;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.VillagerProfession;

import java.util.List;

public class RenderUtils {
    private final static Color sideColor = Color.GREEN.copy().a((int) (Color.GREEN.a * 0.3));
    private final static Color lineColor = Color.GREEN.copy();

    public static void renderBoundingBox (Render3DEvent event, ClientWorld world, List<Integer> blocked) {
        for (Entity entity : world.getEntities()) {
            if (blocked.contains(entity.getId())) continue;
            if (!(entity instanceof VillagerEntity villager)) continue;
            if (!villager.getVillagerData().getProfession().equals(VillagerProfession.CLERIC)) continue;

            drawBoundingBox(event, entity);
        }
    }

    private static void drawBoundingBox(Render3DEvent event, Entity entity) {
        double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
        double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
        double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

        Box box = entity.getBoundingBox();
        event.renderer.box(
            x + box.minX, y + box.minY, z + box.minZ,
            x + box.maxX, y + box.maxY, z + box.maxZ,
            sideColor, lineColor,
            ShapeMode.Lines,
            0
        );
    }
}
