package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * HunterBuddy Shader — entity glow/outline driven by Mixins on
 * {@link Entity#isGlowing()} and {@link Entity#getTeamColorValue()}. When the
 * module is active and the entity is selected by the targets filter, the
 * Mixins force the vanilla outline path to fire with the configured colour.
 *
 * <p>Targets follow the Future 3 ordering:
 * Self, Hand, Players, Monsters, Animals, Vehicles, Others, Crystals,
 * Pearls, Items, Storages, Armor.
 *
 * <p>Sodium does not break this pipeline (it optimises chunk rendering, not
 * entity rendering). Iris can break it depending on the active shader pack.
 *
 * <p>Lives under the {@code Future} addon category.
 */
public class Shader extends Module {
    private final SettingGroup sgTargets = settings.createGroup("Targets");
    private final SettingGroup sgRender  = settings.createGroup("Render");

    // Targets — order matches Future 3.
    private final Setting<Boolean> self = sgTargets.add(new Builder()
        .name("self").description("Outline yourself.").defaultValue(false).build());
    private final Setting<Boolean> hand = sgTargets.add(new Builder()
        .name("hand").description("Outline the player while a non-empty main-hand stack is held.").defaultValue(false).build());
    private final Setting<Boolean> players = sgTargets.add(new Builder()
        .name("players").description("Outline other players.").defaultValue(true).build());
    private final Setting<Boolean> monsters = sgTargets.add(new Builder()
        .name("monsters").description("Outline hostile mobs.").defaultValue(false).build());
    private final Setting<Boolean> animals = sgTargets.add(new Builder()
        .name("animals").description("Outline passive mobs.").defaultValue(false).build());
    private final Setting<Boolean> vehicles = sgTargets.add(new Builder()
        .name("vehicles").description("Outline boats and minecarts.").defaultValue(false).build());
    private final Setting<Boolean> others = sgTargets.add(new Builder()
        .name("others").description("Outline misc entities (paintings, item frames, etc.).").defaultValue(false).build());
    private final Setting<Boolean> crystals = sgTargets.add(new Builder()
        .name("crystals").description("Outline end crystals.").defaultValue(false).build());
    private final Setting<Boolean> pearls = sgTargets.add(new Builder()
        .name("pearls").description("Outline ender pearls in flight.").defaultValue(false).build());
    private final Setting<Boolean> items = sgTargets.add(new Builder()
        .name("items").description("Outline dropped items.").defaultValue(false).build());
    private final Setting<Boolean> storages = sgTargets.add(new Builder()
        .name("storages").description("Outline storage entities (chest minecarts, etc.).").defaultValue(false).build());
    private final Setting<Boolean> armor = sgTargets.add(new Builder()
        .name("armor").description("Outline worn armor stands.").defaultValue(false).build());

    // Render
    private final Setting<Boolean> outline = sgRender.add(new Builder()
        .name("outline").description("Force the vanilla outline shader on each target.").defaultValue(true).build());
    private final Setting<SettingColor> outlineColor = sgRender.add(new ColorSetting.Builder()
        .name("outline-color").description("Color of the outline.")
        .defaultValue(new SettingColor(255, 80, 80, 220)).build());

    /** Targets updated on each tick; read by the EntityGlowMixin /
     *  EntityTeamColorMixin to decide whether the vanilla outline path should
     *  fire and with what colour. */
    private final Set<Entity> glowTargets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Storage BlockEntity positions updated on each tick — drawn as 12-edge
     *  outline via Render3DEvent in ShapeMode.Lines (cube silhouette through
     *  walls, default depthTest=false on Meteor's renderer3D mesh). */
    private final Set<BlockPos> storageTargets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Shader() {
        super(HunterBuddyAddon.FUTURE_CATEGORY, "shader",
            "Entity glow/outline shader driven by EntityGlowMixin + EntityTeamColorMixin.");
    }

    public boolean shouldGlow(Entity e) {
        return e != null && glowTargets.contains(e);
    }

    /** RGB-packed outline colour consumed by EntityTeamColorMixin.
     *  Note: alpha is intentionally omitted — the vanilla outline pipeline
     *  does not consult alpha here. */
    public int outlineRgb() {
        Color c = toColor(outlineColor.get());
        return (Math.max(0, Math.min(255, c.r)) << 16)
             | (Math.max(0, Math.min(255, c.g)) << 8)
             |  Math.max(0, Math.min(255, c.b));
    }

    private boolean isTarget(Entity e) {
        if (e == null || e.isRemoved() || mc.world == null) return false;
        if (e == mc.player) {
            if (self.get()) return true;
            if (hand.get() && handHasItem()) return true;
            return false;
        }
        if (e instanceof PlayerEntity) return players.get();
        if (e instanceof EndCrystalEntity) return crystals.get();
        if (e instanceof EnderPearlEntity) return pearls.get();
        if (e instanceof ItemEntity) return items.get();
        if (e instanceof MinecartEntity mc2) {
            String id2 = net.minecraft.registry.Registries.ENTITY_TYPE.getId(mc2.getType()).toString();
            if (id2.contains("chest_minecart") || id2.contains("hopper_minecart")) return storages.get();
            return vehicles.get();
        }
        if (e instanceof BoatEntity) return vehicles.get();
        if (e instanceof LivingEntity le) {
            SpawnGroup g = le.getType().getSpawnGroup();
            if (g == SpawnGroup.MONSTER) return monsters.get();
            if (g == SpawnGroup.CREATURE || g == SpawnGroup.AMBIENT
                || g == SpawnGroup.WATER_CREATURE || g == SpawnGroup.WATER_AMBIENT) return animals.get();
            String id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(le.getType()).toString();
            if (id.equals("minecraft:armor_stand")) return armor.get();
        }
        String id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString();
        if (id.equals("minecraft:painting") || id.equals("minecraft:item_frame")
            || id.equals("minecraft:glow_item_frame")) return others.get();
        return false;
    }

    private boolean handHasItem() {
        if (mc.player == null) return false;
        ItemStack main = mc.player.getMainHandStack();
        return main != null && !main.isEmpty();
    }

    @EventHandler
    private void onTick(Post event) {
        glowTargets.clear();
        storageTargets.clear();
        if (mc.world == null || mc.player == null) return;
        if (!outline.get()) return;
        for (Entity e : mc.world.getEntities()) {
            if (!isTarget(e)) continue;
            glowTargets.add(e);
        }
        if (storages.get()) {
            for (BlockEntity be : Utils.blockEntities()) {
                if (!isStorageBlock(be)) continue;
                storageTargets.add(be.getPos());
            }
        }
    }

    private boolean isStorageBlock(BlockEntity be) {
        return be instanceof ChestBlockEntity
            || be instanceof BarrelBlockEntity
            || be instanceof ShulkerBoxBlockEntity
            || be instanceof EnderChestBlockEntity;
    }

    /** Renders the 12 edges of the cube silhouette around each storage
     *  BlockEntity. Meteor's renderer3D mesh defaults to depthTest=false
     *  (Mesh.java:42) so the outlines are visible through walls — same
     *  through-wall principle as the vanilla entity outline shader. */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (storageTargets.isEmpty()) return;
        SettingColor sc = outlineColor.get();
        Color color = toColor(sc);
        for (BlockPos pos : storageTargets) {
            event.renderer.box(pos, color, color, ShapeMode.Lines, 0);
        }
    }

    private static Color toColor(SettingColor c) {
        return new Color(c.r, c.g, c.b, c.a);
    }
}
