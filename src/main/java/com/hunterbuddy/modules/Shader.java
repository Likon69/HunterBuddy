package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.render.HbGlowShader;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.utils.render.MeshBuilderVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.SimpleBlockRenderer;
import net.minecraft.client.gl.Framebuffer;
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
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.WorldChunk;

public class Shader extends Module {
    /**
     * One entry per entity type, since the hash behind a shade never changes.
     *
     * <p>Cleared whenever the mode, the spread or either family colour changes — the shades
     * are derived from those, so a stale cache would leave the world painted from settings
     * the player has already moved on from.
     */
    private static final java.util.Map<EntityType<?>, SettingColor> typeColorCache = new ConcurrentHashMap<>();

    public enum EntityColorMode {
        CATEGORY,
        PER_TYPE
    }

    private final SettingGroup sgTargets = settings.createGroup("Targets");
    private final SettingGroup sgRender  = settings.createGroup("Render");
    private final SettingGroup sgColors  = settings.createGroup("Colors");
    private final SettingGroup sgStorageColors = settings.createGroup("Storage Colors");

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
        .description("Trace each target with a crisp line following its real silhouette. Also gates which entities are collected, so turning it off leaves entities with neither line nor glow. Storages and portals have their own switches.")
        .defaultValue(true).build());
    private final Setting<Double> outlineWidth = sgRender.add(new DoubleSetting.Builder()
        .name("outline-width")
        .description("Thickness of that line in pixels. Vanilla's own outline has no width control, which is why small targets like a firework rocket used to be swallowed whole by it.")
        .defaultValue(1.5).min(0.25).max(6.0).sliderRange(0.25, 6.0)
        .visible(outline::get).build());
    private final Setting<Boolean> glow = sgRender.add(new Builder()
        .name("glow")
        .description("Add a halo around the outline, fading with the distance to the silhouette so a thin target glows as strongly as a wide one.")
        .defaultValue(true).build());
    private final Setting<Double> glowWidth = sgRender.add(new DoubleSetting.Builder()
        .name("glow-width").description("Glow radius in pixels.")
        .defaultValue(9.0).min(1.0).max(20.0).sliderRange(1.0, 20.0)
        .visible(glow::get).build());
    private final Setting<Integer> glowSamples = sgRender.add(new IntSetting.Builder()
        .name("glow-quality")
        .description("How finely the distance to the nearest silhouette is searched. Only matters at large radii, where a coarse sweep plus a refine pass replaces the full search.")
        .defaultValue(2).min(1).max(10).sliderRange(1, 10)
        .visible(glow::get).build());
    private final Setting<Double> glowIntensity = sgRender.add(new DoubleSetting.Builder()
        .name("glow-intensity")
        .description("Opacity of the halo where it leaves the line. This is now read directly, where the old blur multiplied it eightfold — a config from before will look far dimmer until it is raised.")
        .defaultValue(0.35).min(0.01).max(1.0).sliderRange(0.01, 1.0)
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
    private final Setting<Shader.EntityColorMode> entityColorMode = sgColors.add(
        new meteordevelopment.meteorclient.settings.EnumSetting.Builder<Shader.EntityColorMode>()
            .name("entity-color-mode")
            .description("CATEGORY paints every hostile the same and every passive the same. PER_TYPE keeps those two families but gives each species its own shade inside its family, so a zombie, a skeleton and a creeper stop looking alike without losing what tells you which is dangerous.")
            .defaultValue(Shader.EntityColorMode.CATEGORY)
            .onChanged(v -> typeColorCache.clear())
            .build());
    private final Setting<Integer> typeColorSpread = sgColors.add(new IntSetting.Builder()
        .name("type-color-spread")
        .description("How far a species may drift from its family hue, in degrees. Small keeps the family obvious, large tells species apart more sharply but starts bleeding into neighbouring hues.")
        .defaultValue(25).min(10).max(60).sliderRange(10, 60)
        .visible(() -> entityColorMode.get() == Shader.EntityColorMode.PER_TYPE)
        .onChanged(v -> typeColorCache.clear())
        .build());
    private final Setting<SettingColor> monstersColor = sgColors.add(new ColorSetting.Builder()
        .name("monsters-color").description("Outline color for hostile mobs.")
        .defaultValue(new SettingColor(255, 160, 40, 220))
        .onChanged(v -> typeColorCache.clear())
        .build());
    private final Setting<SettingColor> animalsColor = sgColors.add(new ColorSetting.Builder()
        .name("animals-color").description("Outline color for passive mobs.")
        .defaultValue(new SettingColor(80, 255, 80, 220))
        .onChanged(v -> typeColorCache.clear())
        .build());
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
    private final Setting<SettingColor> chestColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("chest-color").description("Outline color for chests.")
        .defaultValue(new SettingColor(255, 160, 0, 255)).build());
    private final Setting<SettingColor> trappedChestColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("trapped-chest-color").description("Outline color for trapped chests.")
        .defaultValue(new SettingColor(255, 60, 60, 255)).build());
    private final Setting<SettingColor> barrelColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("barrel-color").description("Outline color for barrels.")
        .defaultValue(new SettingColor(190, 130, 60, 255)).build());
    private final Setting<SettingColor> shulkerColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("shulker-box-color").description("Outline color for shulker boxes.")
        .defaultValue(new SettingColor(200, 80, 255, 255)).build());
    private final Setting<SettingColor> enderChestColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("ender-chest-color").description("Outline color for ender chests.")
        .defaultValue(new SettingColor(60, 240, 220, 255)).build());
    private final Setting<SettingColor> hopperColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("hopper-color").description("Outline color for hoppers.")
        .defaultValue(new SettingColor(150, 150, 160, 255)).build());
    private final Setting<SettingColor> furnaceColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("furnace-color").description("Outline color for furnaces, blast furnaces and smokers.")
        .defaultValue(new SettingColor(230, 230, 230, 255)).build());
    private final Setting<SettingColor> dispenserColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("dispenser-color").description("Outline color for dispensers and droppers.")
        .defaultValue(new SettingColor(120, 200, 255, 255)).build());
    private final Setting<SettingColor> crafterColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("crafter-color").description("Outline color for crafters.")
        .defaultValue(new SettingColor(255, 220, 90, 255)).build());
    private final Setting<SettingColor> brewingStandColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("brewing-stand-color").description("Outline color for brewing stands.")
        .defaultValue(new SettingColor(180, 255, 140, 255)).build());
    private final Setting<SettingColor> otherStorageColor = sgStorageColors.add(new ColorSetting.Builder()
        .name("other-storage-color").description("Outline color for bookshelves and decorated pots.")
        .defaultValue(new SettingColor(210, 170, 120, 255)).build());
    private final Setting<SettingColor> armorColor = sgColors.add(new ColorSetting.Builder()
        .name("armor-color").description("Outline color for armor stands.")
        .defaultValue(new SettingColor(180, 180, 180, 220)).build());
    private final Setting<SettingColor> portalColor = sgColors.add(new ColorSetting.Builder()
        .name("portal-color").description("Color for nether portal highlights.")
        .defaultValue(new SettingColor(170, 0, 255, 80)).build());

    private final Set<Entity> glowTargets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<BlockPos> storageTargets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<BlockPos> portalPositions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Block geometry for the current frame, blitted into the silhouette buffer. */
    private final MeshBuilder blockMesh = new MeshBuilder(MeteorRenderPipelines.WORLD_COLORED);
    private final MeshBuilderVertexConsumerProvider blockMeshProvider = new MeshBuilderVertexConsumerProvider(blockMesh);
    private final Color storageScratch = new Color();

    public Shader() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "shader",
            "Entity glow/outline shader driven by EntityGlowMixin + EntityTeamColorMixin.");
    }

    public boolean shouldGlow(Entity e) {
        return e != null && glowTargets.contains(e);
    }

    public boolean selfEnabled() {
        return self.get();
    }

    // --- Glow post-process (see com.hunterbuddy.render.HbGlowShader) ---

    /** True when the post-process pass has anything to do at all. */
    public boolean postProcessEnabled() {
        return glow.get() || filled.get() || outline.get();
    }

    /** True while we draw the outline ourselves, which also suppresses vanilla's. */
    public boolean outlineEnabled() {
        return outline.get();
    }

    public double outlineWidth() {
        return outlineWidth.get();
    }

    /**
     * True when there is at least one silhouette to work with. Storages and
     * portals count: they are drawn into the capture buffer during the 3D pass,
     * and that buffer only exists if the capture ran, which this gates.
     */
    public boolean hasPostProcessTargets() {
        return !glowTargets.isEmpty() || !storageTargets.isEmpty() || !portalPositions.isEmpty() || handGlowActive();
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
            if (g == SpawnGroup.MONSTER) return shadeForType(le.getType(), monstersColor.get());
            if (g == SpawnGroup.CREATURE || g == SpawnGroup.AMBIENT
                || g == SpawnGroup.WATER_CREATURE || g == SpawnGroup.WATER_AMBIENT) return shadeForType(le.getType(), animalsColor.get());
            String id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(le.getType()).toString();
            if (id.equals("minecraft:armor_stand")) return armorColor.get();
        }
        return othersColor.get();
    }

    /**
     * The family colour, nudged onto a shade of its own for this species.
     *
     * <p>Returns the family colour untouched in CATEGORY mode, so nothing about the existing
     * look changes unless the mode is switched.
     *
     * <p>The nudge is a hue offset derived from the entity type's registry id. Being a hash
     * of a stable string, a zombie lands on the same shade every session and on every world,
     * which is what makes the colours learnable rather than merely varied. The offset is
     * bounded by the spread setting so hostiles stay in the warm half and passives in the
     * green half: telling a creeper from a skeleton is worth something, but not at the cost
     * of knowing at a glance which one can kill you.
     *
     * <p>Brightness moves too, on a second, independent hash. Hue alone leaves collisions —
     * inside a window of fifty degrees plenty of species land close together — and a lighter
     * or darker version of nearly the same hue separates them at no cost to the family read.
     */
    private SettingColor shadeForType(EntityType<?> type, SettingColor base) {
        if (entityColorMode.get() != Shader.EntityColorMode.PER_TYPE) return base;

        return typeColorCache.computeIfAbsent(type, t -> {
            int spread = typeColorSpread.get();
            String id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(t).toString();

            float[] hsb = java.awt.Color.RGBtoHSB(clamp(base.r), clamp(base.g), clamp(base.b), null);

            int h = mix(id.hashCode());

            // Spread over 1024 steps rather than one per whole degree. A degree-wide bucket
            // leaves only 51 slots at the default spread, and with some forty hostile types
            // the birthday problem alone puts several pairs on the exact same shade —
            // measured, not assumed: zombie and creeper came out byte-identical.
            float t01 = (h & 1023) / 1024.0f;
            float hueOffset = (t01 * 2.0f - 1.0f) * spread / 360.0f;
            float hue = (hsb[0] + hueOffset + 1.0f) % 1.0f;

            // Darkens only, never brightens. Both family colours ship at full brightness, so
            // any upward variation was clipped straight back to where it started and the
            // tie-break silently did nothing.
            //
            // The bits come from a different slice of the same mixed hash, not from hashing a
            // suffixed string. String.hashCode is linear, so h(id + "#shade") is an affine
            // function of h(id); modulo 1024 that is a bijection, and two types sharing a hue
            // were therefore guaranteed to share a brightness too. The tie-break was dead in
            // exactly the case it existed for — hoglin and guardian came out identical, as
            // did donkey and camel.
            int shadeHash = (h >>> 10) & 1023;
            float brightness = Math.max(0.20f, hsb[2] * (1.0f - shadeHash / 1024.0f * 0.30f));

            int rgb = java.awt.Color.HSBtoRGB(hue, hsb[1], brightness);

            // Alpha is carried over rather than rebuilt: these colours ship at 220, and the
            // outline pass reads it.
            return new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, base.a);
        });
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Avalanche step, so slices of the result can be treated as unrelated to one another.
     *
     * <p>Raw {@code String.hashCode} values are far from it: for short ids that differ late,
     * the low bits carry nearly all of the difference and the high bits barely move. Slicing
     * such a value would give a brightness that tracks the hue instead of separating it. This
     * is the finalizer from MurmurHash3, which spreads every input bit across all 32.
     *
     * <p>Disjoint slices alone are enough for the seventy-odd vanilla mobs — that much is
     * measured. The mixer is what keeps it true if a mod ever adds a few hundred more, and it
     * runs once per entity type in a lifetime.
     */
    private static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
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
        if (!outline.get() && !storages.get() && !portals.get()) return;
        if (outline.get()) {
            for (Entity e : mc.world.getEntities()) {
                if (!isTarget(e)) continue;
                glowTargets.add(e);
            }
        }
        // Storages are their own target set: they feed the silhouette buffer
        // directly rather than through the entity path, so gating them on the
        // entity outline switch only hid them for no reason.
        if (storages.get()) {
            for (BlockEntity be : Utils.blockEntities()) {
                if (!isStorageBlock(be)) continue;
                storageTargets.add(be.getPos());
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
            || be instanceof EnderChestBlockEntity
            || be instanceof HopperBlockEntity
            || be instanceof AbstractFurnaceBlockEntity
            || be instanceof DispenserBlockEntity
            || be instanceof CrafterBlockEntity
            || be instanceof BrewingStandBlockEntity
            || be instanceof ChiseledBookshelfBlockEntity
            || be instanceof DecoratedPotBlockEntity;
    }

    /**
     * Colour for one storage block. Trapped chests are tested before plain chests
     * and droppers before dispensers: both are subclasses, so the wrong order
     * silently collapses two colours into one.
     */
    private Color colorForStorage(BlockEntity be) {
        SettingColor c;

        if (be instanceof TrappedChestBlockEntity) c = trappedChestColor.get();
        else if (be instanceof ChestBlockEntity) c = chestColor.get();
        else if (be instanceof BarrelBlockEntity) c = barrelColor.get();
        else if (be instanceof ShulkerBoxBlockEntity) c = shulkerColor.get();
        else if (be instanceof EnderChestBlockEntity) c = enderChestColor.get();
        else if (be instanceof HopperBlockEntity) c = hopperColor.get();
        else if (be instanceof AbstractFurnaceBlockEntity) c = furnaceColor.get();
        else if (be instanceof DispenserBlockEntity) c = dispenserColor.get();
        else if (be instanceof CrafterBlockEntity) c = crafterColor.get();
        else if (be instanceof BrewingStandBlockEntity) c = brewingStandColor.get();
        else c = otherStorageColor.get();

        // Forced opaque. WORLD_COLORED blends translucently with no depth test, so
        // a colour with alpha would write darkened RGB and let overlapping faces
        // of the same block re-blend into each other — the composite would then
        // read a marbled, dimmed silhouette instead of a flat one. Softness is the
        // job of glow-intensity and fill-opacity, not of the silhouette buffer.
        //
        // Reused rather than allocated per block per frame: the mesh bakes the
        // colour into the vertices during the render call that follows.
        return storageScratch.set(c.r, c.g, c.b, 255);
    }

    /**
     * Draws storages and portals into the same silhouette buffer the entities
     * live in, so the one composite pass gives them the identical outline, glow
     * and fill.
     *
     * <p>These used to be axis-aligned boxes built from the block's VoxelShape
     * bounds. A chest is 14/16 wide, so the box never matched the model, and an
     * open lid or a shulker mid-animation was not represented at all. Rendering
     * the block model plus its block-entity renderer gives the real shape, lid
     * included, which is the whole point of a shader outline.
     *
     * <p>The timing works because {@code Render3DEvent} is dispatched from
     * {@code GameRenderer.renderWorld} after {@code WorldRenderer.render} has
     * returned, so the capture already happened and its depth is cleared. The
     * buffer is deliberately not cleared here — the entity silhouettes are
     * already in it.
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;
        if (storageTargets.isEmpty() && portalPositions.isEmpty()) return;

        Framebuffer target = HbGlowShader.captureTarget();
        if (target == null) return;

        boolean any = false;

        for (BlockPos pos : storageTargets) {
            BlockEntity be = mc.world.getBlockEntity(pos);
            if (be == null || be.isRemoved()) continue;

            if (!any) {
                blockMesh.begin();
                any = true;
            }

            blockMeshProvider.setColor(colorForStorage(be));
            SimpleBlockRenderer.renderWithBlockEntity(be, event.tickDelta, blockMeshProvider);
        }

        if (portals.get()) {
            SettingColor p = portalColor.get();
            Color pc = storageScratch.set(p.r, p.g, p.b, 255);

            for (BlockPos pos : portalPositions) {
                BlockState state = mc.world.getBlockState(pos);
                if (!state.isOf(Blocks.NETHER_PORTAL)) continue;

                if (!any) {
                    blockMesh.begin();
                    any = true;
                }

                // A portal has no block entity, so the offset the block-entity
                // path normally sets has to be supplied by hand.
                blockMeshProvider.setColor(pc);
                blockMeshProvider.setOffset(pos.getX(), pos.getY(), pos.getZ());
                SimpleBlockRenderer.render(pos, state, blockMeshProvider);
            }
        }

        if (!any) return;

        MeshRenderer.begin()
            .attachments(target)
            .pipeline(MeteorRenderPipelines.WORLD_COLORED)
            .mesh(blockMesh, event.matrices)
            .end();
    }

}
