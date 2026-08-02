package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.WorldChunk;

public class Shader extends Module {
    private final SettingGroup sgTargets = settings.createGroup("Targets");
    private final SettingGroup sgRender  = settings.createGroup("Render");
    private final SettingGroup sgColors  = settings.createGroup("Colors");

    // Targets
    private final Setting<Boolean> self = sgTargets.add(new Builder()
        .name("self").description("Outline yourself.").defaultValue(false).build());
    private final Setting<Boolean> hand = sgTargets.add(new Builder()
        .name("hand")
        .description("Glow the first-person held item and arm (and the player in third person) while a non-empty main-hand stack is held.")
        .defaultValue(false).build());
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
        .name("storages").description("Highlight storage blocks (chests, barrels, shulkers, ender chests).").defaultValue(false).build());
    private final Setting<Boolean> armor = sgTargets.add(new Builder()
        .name("armor").description("Outline armor stands.").defaultValue(false).build());
    private final Setting<Boolean> portals = sgTargets.add(new Builder()
        .name("portals").description("Highlight all loaded nether portals.").defaultValue(true).build());

    // Render
    private final Setting<Boolean> outline = sgRender.add(new Builder()
        .name("outline")
        .description("Force the vanilla outline shader on each target. Master switch: the glow post-process builds on the silhouettes this produces.")
        .defaultValue(true).build());
    private final Setting<Boolean> glow = sgRender.add(new Builder()
        .name("glow")
        .description("Add a gaussian glow halo around the outline (post-process).")
        .defaultValue(true).build());
    private final Setting<Double> glowWidth = sgRender.add(new DoubleSetting.Builder()
        .name("glow-width").description("Glow radius in pixels.")
        .defaultValue(9.0).min(1.0).max(20.0).sliderRange(1.0, 20.0)
        .visible(glow::get).build());
    private final Setting<Integer> glowSamples = sgRender.add(new IntSetting.Builder()
        .name("glow-samples").description("Gaussian taps per side. Higher is smoother and costlier.")
        .defaultValue(2).min(1).max(10).sliderRange(1, 10)
        .visible(glow::get).build());
    private final Setting<Double> glowIntensity = sgRender.add(new DoubleSetting.Builder()
        .name("glow-intensity").description("Glow strength.")
        .defaultValue(0.13).min(0.01).max(1.0).sliderRange(0.01, 1.0)
        .visible(glow::get).build());
    private final Setting<Boolean> filled = sgRender.add(new Builder()
        .name("filled").description("Fill the entity silhouette with a semi-transparent colour.")
        .defaultValue(false).build());
    private final Setting<Double> fillOpacity = sgRender.add(new DoubleSetting.Builder()
        .name("fill-opacity").description("Opacity of the silhouette fill.")
        .defaultValue(0.3).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(filled::get).build());

    // Colors
    private final Setting<SettingColor> selfColor = sgColors.add(new ColorSetting.Builder()
        .name("self-color").description("Outline color for yourself.")
        .defaultValue(new SettingColor(255, 255, 255, 220)).build());
    private final Setting<SettingColor> playersColor = sgColors.add(new ColorSetting.Builder()
        .name("players-color").description("Outline color for other players.")
        .defaultValue(new SettingColor(255, 80, 80, 220)).build());
    private final Setting<SettingColor> monstersColor = sgColors.add(new ColorSetting.Builder()
        .name("monsters-color").description("Outline color for hostile mobs.")
        .defaultValue(new SettingColor(255, 160, 40, 220)).build());
    private final Setting<SettingColor> animalsColor = sgColors.add(new ColorSetting.Builder()
        .name("animals-color").description("Outline color for passive mobs.")
        .defaultValue(new SettingColor(80, 255, 80, 220)).build());
    private final Setting<SettingColor> vehiclesColor = sgColors.add(new ColorSetting.Builder()
        .name("vehicles-color").description("Outline color for boats and minecarts.")
        .defaultValue(new SettingColor(255, 255, 80, 220)).build());
    private final Setting<SettingColor> othersColor = sgColors.add(new ColorSetting.Builder()
        .name("others-color").description("Outline color for misc entities.")
        .defaultValue(new SettingColor(150, 200, 255, 220)).build());
    private final Setting<SettingColor> crystalsColor = sgColors.add(new ColorSetting.Builder()
        .name("crystals-color").description("Outline color for end crystals.")
        .defaultValue(new SettingColor(255, 80, 255, 220)).build());
    private final Setting<SettingColor> pearlsColor = sgColors.add(new ColorSetting.Builder()
        .name("pearls-color").description("Outline color for ender pearls.")
        .defaultValue(new SettingColor(80, 255, 255, 220)).build());
    private final Setting<SettingColor> itemsColor = sgColors.add(new ColorSetting.Builder()
        .name("items-color").description("Outline color for dropped items.")
        .defaultValue(new SettingColor(255, 255, 255, 220)).build());
    private final Setting<SettingColor> storagesColor = sgColors.add(new ColorSetting.Builder()
        .name("storages-color").description("Color for storage block highlights.")
        .defaultValue(new SettingColor(255, 165, 0, 150)).build());
    private final Setting<SettingColor> armorColor = sgColors.add(new ColorSetting.Builder()
        .name("armor-color").description("Outline color for armor stands.")
        .defaultValue(new SettingColor(180, 180, 180, 220)).build());
    private final Setting<SettingColor> portalColor = sgColors.add(new ColorSetting.Builder()
        .name("portal-color").description("Color for nether portal highlights.")
        .defaultValue(new SettingColor(170, 0, 255, 80)).build());

    private final Set<Entity> glowTargets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<BlockPos> storageTargets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<BlockPos> portalPositions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Shader() {
        super(HunterBuddyAddon.FUTURE_CATEGORY, "shader",
            "Entity glow/outline shader driven by EntityGlowMixin + EntityTeamColorMixin.");
    }

    public boolean shouldGlow(Entity e) {
        return e != null && glowTargets.contains(e);
    }

    // --- Glow post-process (see com.hunterbuddy.render.HbGlowShader) ---

    /** True when the post-process pass has anything to do at all. */
    public boolean postProcessEnabled() {
        return glow.get() || filled.get();
    }

    /** True when there is at least one silhouette in the outline framebuffer. */
    public boolean hasPostProcessTargets() {
        return !glowTargets.isEmpty() || handGlowActive();
    }

    public boolean glowEnabled() {
        return glow.get();
    }

    public double glowWidth() {
        return glowWidth.get();
    }

    public int glowSamples() {
        return glowSamples.get();
    }

    public double glowIntensity() {
        return glowIntensity.get();
    }

    public boolean filled() {
        return filled.get();
    }

    public double fillOpacity() {
        return fillOpacity.get();
    }

    /** True when the first-person held item should be pulled into the outline pass. */
    public boolean handGlowActive() {
        return hand.get() && handHasItem();
    }

    /** ARGB outline colour for the first-person hand. */
    public int handGlowColor() {
        SettingColor c = selfColor.get();
        return 0xFF000000 | (clamp(c.r) << 16) | (clamp(c.g) << 8) | clamp(c.b);
    }

    public int outlineRgb(Entity e) {
        SettingColor c = getColorForEntity(e);
        return (clamp(c.r) << 16) | (clamp(c.g) << 8) | clamp(c.b);
    }

    private SettingColor getColorForEntity(Entity e) {
        if (e == mc.player) return selfColor.get();
        if (e instanceof PlayerEntity) return playersColor.get();
        if (e instanceof EndCrystalEntity) return crystalsColor.get();
        if (e instanceof EnderPearlEntity) return pearlsColor.get();
        if (e instanceof ItemEntity) return itemsColor.get();
        if (e instanceof BoatEntity) return vehiclesColor.get();
        if (e instanceof MinecartEntity) return vehiclesColor.get();
        if (e instanceof LivingEntity le) {
            SpawnGroup g = le.getType().getSpawnGroup();
            if (g == SpawnGroup.MONSTER) return monstersColor.get();
            if (g == SpawnGroup.CREATURE || g == SpawnGroup.AMBIENT
                || g == SpawnGroup.WATER_CREATURE || g == SpawnGroup.WATER_AMBIENT) return animalsColor.get();
            String id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(le.getType()).toString();
            if (id.equals("minecraft:armor_stand")) return armorColor.get();
        }
        return othersColor.get();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
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
        portalPositions.clear();
        if (mc.world == null || mc.player == null) return;
        if (!outline.get() && !portals.get()) return;
        if (outline.get()) {
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
        if (portals.get()) {
            ClientChunkManager cm = mc.world.getChunkManager();
            int viewDistance = mc.options.getViewDistance().getValue();
            ChunkPos center = mc.player.getChunkPos();
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    WorldChunk chunk = cm.getWorldChunk(center.x + dx, center.z + dz);
                    if (chunk == null) continue;
                    chunk.forEachBlockMatchingPredicate(
                        state -> state.isOf(Blocks.NETHER_PORTAL),
                        (pos, state) -> portalPositions.add(pos.toImmutable())
                    );
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        glowTargets.clear();
        storageTargets.clear();
        portalPositions.clear();
    }

    private boolean isStorageBlock(BlockEntity be) {
        return be instanceof ChestBlockEntity
            || be instanceof BarrelBlockEntity
            || be instanceof ShulkerBoxBlockEntity
            || be instanceof EnderChestBlockEntity;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!storageTargets.isEmpty()) {
            Color sc = toColor(storagesColor.get());
            for (BlockPos pos : storageTargets) {
                BlockState state = mc.world.getBlockState(pos);
                VoxelShape shape = state.getOutlineShape(mc.world, pos);
                if (!shape.isEmpty()) {
                    event.renderer.box(
                        pos.getX() + shape.getMin(Direction.Axis.X),
                        pos.getY() + shape.getMin(Direction.Axis.Y),
                        pos.getZ() + shape.getMin(Direction.Axis.Z),
                        pos.getX() + shape.getMax(Direction.Axis.X),
                        pos.getY() + shape.getMax(Direction.Axis.Y),
                        pos.getZ() + shape.getMax(Direction.Axis.Z),
                        sc, sc, ShapeMode.Both, 0);
                } else {
                    event.renderer.box(pos, sc, sc, ShapeMode.Both, 0);
                }
            }
        }
        if (portals.get() && !portalPositions.isEmpty()) {
            Color pc = toColor(portalColor.get());
            for (BlockPos pos : portalPositions) {
                BlockState state = mc.world.getBlockState(pos);
                VoxelShape shape = state.getOutlineShape(mc.world, pos);
                if (!shape.isEmpty()) {
                    event.renderer.box(
                        pos.getX() + shape.getMin(Direction.Axis.X),
                        pos.getY() + shape.getMin(Direction.Axis.Y),
                        pos.getZ() + shape.getMin(Direction.Axis.Z),
                        pos.getX() + shape.getMax(Direction.Axis.X),
                        pos.getY() + shape.getMax(Direction.Axis.Y),
                        pos.getZ() + shape.getMax(Direction.Axis.Z),
                        pc, pc, ShapeMode.Both, 0);
                } else {
                    event.renderer.box(pos, pc, pc, ShapeMode.Both, 0);
                }
            }
        }
    }

    private static Color toColor(SettingColor c) {
        return new Color(c.r, c.g, c.b, c.a);
    }
}
