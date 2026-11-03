package com.hunterbuddy.modules.vanityesp;

import com.hunterbuddy.modules.regear.util.MapUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.vehicle.AbstractChestBoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class StackedFeature implements VanityFeature {
    private static final float SOUND_PITCH = 0.5f;
    private static final double TRACER_HEIGHT = 0.5;
    private static final String WAYPOINT_INITIALS = "⚠";

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final VanityOptions options;
    private final AlertDispatcher alerts;
    private final Set<BlockPos> announced = new HashSet<>();

    StackedFeature(VanityOptions options) {
        this.options = options;
        this.alerts = new AlertDispatcher(options.stackedAlerts);
    }

    @Override
    public void deactivate() {
        announced.clear();
    }

    @Override
    public void render(Render3DEvent event) {
        if (!options.stackedEntities.get() || !options.stackedStyle.drawsShapes() || mc.world == null) return;
        Set<BlockPos> stillStacked = new HashSet<>();
        for (Map.Entry<BlockPos, List<Entity>> group : groupByBlock().entrySet()) {
            if (group.getValue().size() < options.minStackedCount.get()) continue;
            stillStacked.add(group.getKey());
            if (announced.add(group.getKey())) {
                announce(group.getKey(), group.getValue());
            }
            group.getValue().forEach(entity -> outline(event, entity));
        }
        announced.retainAll(stillStacked);
    }

    private Map<BlockPos, List<Entity>> groupByBlock() {
        Map<BlockPos, List<Entity>> groups = new HashMap<>();
        for (Entity entity : mc.world.getEntities()) {
            if (isInventoryHolder(entity)) {
                groups.computeIfAbsent(entity.getBlockPos(), pos -> new ArrayList<>()).add(entity);
            }
        }
        return groups;
    }

    private static boolean isInventoryHolder(Entity entity) {
        return entity instanceof ChestMinecartEntity || entity instanceof HopperMinecartEntity
            || entity instanceof AbstractChestBoatEntity || entity instanceof Inventory;
    }

    private void outline(Render3DEvent event, Entity entity) {
        Outline.box(event, entity.getBoundingBox(), options.stackedStyle);
        Outline.tracer(event, entity.getEntityPos().add(0.0, TRACER_HEIGHT, 0.0), options.stackedStyle);
    }

    private void announce(BlockPos pos, List<Entity> members) {
        String type = typeName(members.get(0));
        int count = members.size();
        alerts.chat(Formatting.GOLD + "Found " + Formatting.YELLOW + count + Formatting.GOLD + " stacked " + Formatting.YELLOW + type
            + Formatting.GRAY, pos);
        alerts.sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SOUND_PITCH);
        alerts.waypoint(pos, count + " Stacked " + type, WAYPOINT_INITIALS, MapUtil.WpColor.Gold);
    }

    private static String typeName(Entity first) {
        if (first instanceof ChestMinecartEntity) return "Chest Minecarts";
        if (first instanceof HopperMinecartEntity) return "Hopper Minecarts";
        return first instanceof AbstractChestBoatEntity ? "Chest Boats" : "Entities";
    }
}
