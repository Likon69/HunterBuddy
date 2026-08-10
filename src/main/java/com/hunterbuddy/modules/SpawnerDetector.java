package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mixin.accessors.MobSpawnerLogicAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.enums.TrialSpawnerState;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Finds spawners somebody has already stood next to, and says whether the loot is still there.
 *
 * <p>The signal is the spawn countdown. A server only ticks it while a player is within range,
 * so a spawner whose delay has moved off its idle value has been visited — and out in the
 * middle of nowhere, visited means a base. A dungeon nobody has emptied still has its chests;
 * one that has been looted, or lit up with torches, tells a different story, and the module
 * separates the two so a long flight is only interrupted for the first.
 */
public class SpawnerDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Boolean> detectActivated = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-activated")
        .description("Report spawners whose countdown has moved, meaning a player has stood near them.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectTorches = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-torches")
        .description("Tell apart the spawners somebody has lit up. A torch on a spawner is a deliberate act, so these get their own colour rather than being hidden.")
        .defaultValue(true).build());

    private final Setting<Boolean> detectTrial = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-trial")
        .description("Report trial spawners that have left their waiting state.")
        .defaultValue(true).build());

    private final Setting<Boolean> requireChests = sgGeneral.add(new BoolSetting.Builder()
        .name("require-chests")
        .description("Hide a find with no container left around it. An emptied dungeon is not worth the detour.")
        .defaultValue(false).build());

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Ticks between two evaluation passes. Only newly discovered spawners are looked at, so this can stay high.")
        .defaultValue(10).min(1).max(100).sliderRange(1, 40).build());

    private final Setting<Integer> chestRange = sgGeneral.add(new IntSetting.Builder()
        .name("chest-range")
        .description("How far around a spawner to look for containers, in blocks.")
        .defaultValue(16).min(4).max(32).sliderRange(4, 32).build());

    private final Setting<Integer> lightRange = sgGeneral.add(new IntSetting.Builder()
        .name("light-range")
        .description("How far around a spawner a light block counts as someone having been here.")
        .defaultValue(6).min(1).max(16).sliderRange(1, 16)
        .visible(detectTorches::get).build());

    private final Setting<List<Block>> containers = sgGeneral.add(new BlockListSetting.Builder()
        .name("containers")
        .description("Blocks that count as loot worth flying down for.")
        .defaultValue(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.SHULKER_BOX)
        .build());

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Print a line when a spawner is classified.")
        .defaultValue(true).build());

    private final Setting<Double> renderDistance = sgRender.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Stop drawing a find past this distance, in blocks.")
        .defaultValue(256.0).min(32.0).sliderRange(32.0, 1024.0).build());

    private final Setting<Integer> fadeTime = sgRender.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Milliseconds a box takes to fade in and out.")
        .defaultValue(400).min(0).max(3000).sliderRange(0, 2000).build());

    private final Setting<Boolean> distanceFade = sgRender.add(new BoolSetting.Builder()
        .name("distance-fade")
        .description("Dim a box as it approaches the render distance, instead of having it vanish.")
        .defaultValue(true).build());

    private final Setting<Boolean> showLabel = sgRender.add(new BoolSetting.Builder()
        .name("show-label")
        .description("Draw the mob type and the container count above the box. That count is what decides whether the detour is worth it.")
        .defaultValue(true).build());

    private final Setting<Double> labelScale = sgRender.add(new DoubleSetting.Builder()
        .name("label-scale").description("Size of the label.")
        .defaultValue(1.0).min(0.3).sliderRange(0.3, 3.0).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How the boxes are drawn.")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> lootedColor = sgColors.add(new ColorSetting.Builder()
        .name("dungeon-color").description("A visited spawner that still has containers around it.")
        .defaultValue(new SettingColor(255, 60, 60, 200)).build());

    private final Setting<SettingColor> emptyColor = sgColors.add(new ColorSetting.Builder()
        .name("emptied-color").description("A visited spawner with nothing left around it.")
        .defaultValue(new SettingColor(130, 130, 130, 140)).build());

    private final Setting<SettingColor> stashColor = sgColors.add(new ColorSetting.Builder()
        .name("stash-color")
        .description("Lit up AND still holding containers: someone lives here and the storage is standing. The find worth breaking a flight for.")
        .defaultValue(new SettingColor(255, 80, 255, 220)).build());

    private final Setting<SettingColor> torchColor = sgColors.add(new ColorSetting.Builder()
        .name("torches-color").description("Lit up, but nothing left around it.")
        .defaultValue(new SettingColor(255, 200, 60, 200)).build());

    private final Setting<SettingColor> trialColor = sgColors.add(new ColorSetting.Builder()
        .name("trial-color").description("An activated trial spawner.")
        .defaultValue(new SettingColor(120, 180, 255, 200)).build());

    /**
     * Spawners seen in a chunk packet and not yet judged.
     *
     * <p>Discovery is event-driven for a reason: the published version rebuilds a set of every
     * loaded chunk on every tick and walks all of them. Here a chunk announces its spawners
     * once, when it arrives.
     */
    private final Set<BlockPos> pending = ConcurrentHashMap.newKeySet();

    private final Map<BlockPos, Detection> found = new ConcurrentHashMap<>();
    private int scanCooldown;

    public SpawnerDetector() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "SpawnerDetector",
            "Finds spawners a player has already visited, and whether the loot is still there.");
    }

    @Override
    public void onActivate() {
        pending.clear();
        found.clear();
        scanCooldown = 0;
    }

    @Override
    public void onDeactivate() {
        pending.clear();
        found.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;

        for (BlockEntity be : event.chunk().getBlockEntities().values()) {
            if (be instanceof MobSpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) {
                BlockPos pos = be.getPos().toImmutable();
                if (!found.containsKey(pos)) pending.add(pos);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }

        scanCooldown = scanInterval.get();
        evaluatePending();
    }

    /**
     * Judges the spawners discovered so far, and forgets the ones that cannot be judged yet.
     *
     * <p>The countdown only means something once the chunk is really loaded and the block
     * entity is present; a position whose chunk has gone away is dropped rather than retried
     * forever, and will come back on its own with the next chunk packet.
     */
    private void evaluatePending() {
        Iterator<BlockPos> it = pending.iterator();

        while (it.hasNext()) {
            BlockPos pos = it.next();

            if (found.containsKey(pos)) {
                it.remove();
                continue;
            }

            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                it.remove();
                continue;
            }

            BlockEntity be = mc.world.getBlockEntity(pos);

            if (be instanceof MobSpawnerBlockEntity spawner) {
                classifyMobSpawner(pos, spawner);
                it.remove();
            } else if (be instanceof TrialSpawnerBlockEntity trial) {
                classifyTrialSpawner(pos, trial);
                it.remove();
            } else {
                it.remove();
            }
        }
    }

    private void classifyMobSpawner(BlockPos pos, MobSpawnerBlockEntity spawner) {
        if (!detectActivated.get()) return;

        int delay = ((MobSpawnerLogicAccessor) spawner.getLogic()).hb$getSpawnDelay();

        // 20 is the value a spawner sits at while no player has ever come close. In the Nether
        // a fresh one also reads 0, so that value carries no information there and is skipped
        // rather than reported as a find.
        if (delay == 20) return;
        if (delay == 0 && mc.world.getRegistryKey() == net.minecraft.world.World.NETHER) return;

        int chests = countContainersAround(pos);
        boolean lit = detectTorches.get() && hasLightNear(pos);

        if (requireChests.get() && chests == 0) return;

        // Two independent facts — is there loot, was a human here — so they are crossed rather
        // than ranked. Letting "lit" win outright buried the best find of all under the
        // quietest colour: a spawner someone lit *and* left containers around is somebody's
        // base with its storage still standing, which is the whole point of the search.
        Category category = lit
            ? (chests > 0 ? Category.STASH : Category.LIT)
            : (chests > 0 ? Category.DUNGEON : Category.EMPTIED);

        record(pos, category, mobNameOf(spawner), chests);
    }

    private void classifyTrialSpawner(BlockPos pos, TrialSpawnerBlockEntity trial) {
        if (!detectTrial.get()) return;
        if (trial.getSpawnerState() == TrialSpawnerState.WAITING_FOR_PLAYERS) return;

        int chests = countContainersAround(pos);
        if (requireChests.get() && chests == 0) return;

        record(pos, Category.TRIAL, "trial", chests);
    }

    private void record(BlockPos pos, Category category, String mob, int chests) {
        found.put(pos, new Detection(category, mob, chests, new Animation(fadeTime.get())));

        com.hunterbuddy.util.HuntFeed.get().publish(
            com.hunterbuddy.util.HuntFeed.Type.SPAWNER,
            category.label + " spawner · " + mob, pos);

        if (chatFeedback.get()) {
            info("%s spawner at %d %d %d (%d container%s)",
                category.label, pos.getX(), pos.getY(), pos.getZ(), chests, chests == 1 ? "" : "s");
        }
    }

    /**
     * Counts the containers around a spawner.
     *
     * <p>This is where the published detector spends nearly all of its time: it walks a
     * 33x33x33 cube — some 36 000 block positions — and fires one entity query per position,
     * which is what drops the framerate the moment a dungeon comes into view.
     *
     * <p>Neither loop is needed. Containers are block entities, and the game already keeps
     * those indexed per chunk: a handful of map lookups over the few chunks the radius touches
     * replaces thirty-six thousand block reads. Minecarts take one entity query over the whole
     * box instead of one per block. Same answer, four orders of magnitude fewer calls.
     */
    private int countContainersAround(BlockPos pos) {
        int range = chestRange.get();
        int count = 0;

        int minChunkX = (pos.getX() - range) >> 4;
        int maxChunkX = (pos.getX() + range) >> 4;
        int minChunkZ = (pos.getZ() - range) >> 4;
        int maxChunkZ = (pos.getZ() + range) >> 4;
        int rangeSq = range * range;

        List<Block> wanted = containers.get();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;

                WorldChunk chunk = mc.world.getChunk(cx, cz);

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.getPos().getSquaredDistance(pos) > rangeSq) continue;

                    // The block list is the setting the player edits, but a chest that has not
                    // had its state read yet still answers as a block entity, so both are
                    // accepted and the type check is the cheap one first.
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                        || be instanceof ShulkerBoxBlockEntity
                        || wanted.contains(be.getCachedState().getBlock())) {
                        count++;
                    }
                }
            }
        }

        Box box = new Box(
            pos.getX() - range, pos.getY() - range, pos.getZ() - range,
            pos.getX() + range, pos.getY() + range, pos.getZ() + range);

        count += mc.world.getEntitiesByClass(ChestMinecartEntity.class, box, e -> true).size();
        count += mc.world.getEntitiesByClass(HopperMinecartEntity.class, box, e -> true).size();

        return count;
    }

    /**
     * Whether a light block sits near the spawner.
     *
     * <p>Kept as a block walk, unlike the container scan: light blocks are not block entities,
     * so there is no index to consult. It stays cheap because the radius is small — thirteen
     * cubed at the default against thirty-three cubed — and because it runs once per spawner
     * in a session rather than on every tick.
     */
    private boolean hasLightNear(BlockPos pos) {
        int range = lightRange.get();
        boolean inNether = mc.world.getRegistryKey() == net.minecraft.world.World.NETHER;
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    cursor.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (isPlacedLight(mc.world.getBlockState(cursor), inNether)) return true;
                }
            }
        }

        return false;
    }

    /**
     * Whether this block is light somebody put there.
     *
     * <p>Brightness alone is not the question. Lava is fifteen, fire fifteen, soul fire ten,
     * magma three — all bright, all geology. Accepting any luminous block would file every
     * cave dungeon sitting near a lava pool as "lit by a player", and in the Nether, where
     * lava is the landscape, it would paint every blaze spawner the same colour and leave the
     * module saying nothing at all.
     *
     * <p>So the natural sources are subtracted rather than the placed ones enumerated. A
     * whitelist would have to be extended for every light block a future version adds; this
     * way an unknown light still counts, and only the handful that mean geology are removed.
     *
     * <p>Glowstone and shroomlight are natural in the Nether and nowhere else — glowstone in
     * clusters under the ceilings, shroomlight throughout the crimson and warped forests — so
     * they are subtracted there and kept everywhere else. Dropping them outright would have
     * been the easy call and the wrong one: glowstone is what 2b2t builders light with,
     * precisely because it spawns nothing, and in the Overworld a glowstone block cannot be
     * anything but placed. This keeps the Nether readable without going blind to the very
     * light a base is most likely to use.
     */
    private static boolean isPlacedLight(BlockState state, boolean inNether) {
        if (state.getLuminance() <= 0) return false;

        Block block = state.getBlock();

        if (inNether && (block == Blocks.GLOWSTONE || block == Blocks.SHROOMLIGHT)) return false;

        return block != Blocks.LAVA
            && block != Blocks.FIRE
            && block != Blocks.SOUL_FIRE
            && block != Blocks.MAGMA_BLOCK
            && block != Blocks.AMETHYST_CLUSTER
            && block != Blocks.LARGE_AMETHYST_BUD
            && block != Blocks.MEDIUM_AMETHYST_BUD
            && block != Blocks.SMALL_AMETHYST_BUD
            && block != Blocks.GLOW_LICHEN
            && block != Blocks.BREWING_STAND
            && block != Blocks.SEA_PICKLE;
    }

    /** Short name of the mob the spawner is queued to produce, or "spawner". */
    private String mobNameOf(MobSpawnerBlockEntity spawner) {
        try {
            var entry = ((MobSpawnerLogicAccessor) spawner.getLogic()).hb$getSpawnEntry();
            if (entry == null) return "spawner";

            String id = entry.getNbt().getString("id", "");
            if (id.isEmpty()) return "spawner";

            int colon = id.indexOf(':');
            return colon >= 0 ? id.substring(colon + 1) : id;
        } catch (Exception e) {
            return "spawner";
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || found.isEmpty()) return;

        double maxDistance = renderDistance.get();
        double maxDistanceSq = maxDistance * maxDistance;
        Vec3d eye = mc.player.getEyePos();

        for (Map.Entry<BlockPos, Detection> entry : found.entrySet()) {
            BlockPos pos = entry.getKey();
            Detection detection = entry.getValue();

            double distanceSq = eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

            // Out of range is a fade-out, not a disappearance: a box that blinks off the moment
            // you cross a threshold reads as a bug.
            detection.animation.setVisible(distanceSq <= maxDistanceSq);

            float factor = detection.animation.factor();
            if (factor <= 0.0f) continue;

            if (distanceFade.get()) {
                double distance = Math.sqrt(distanceSq);
                factor *= (float) (1.0 - Math.min(0.75, distance / maxDistance * 0.75));
            }

            SettingColor base = colorOf(detection.category);
            Color line = new Color(base.r, base.g, base.b, (int) (base.a * factor));
            Color side = new Color(base.r, base.g, base.b, (int) (base.a * factor * 0.25));

            event.renderer.box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                side, line, shapeMode.get(), 0);
        }
    }

    /**
     * Labels, drawn in the 2D pass.
     *
     * <p>They cannot ride along with the boxes: a nametag is screen-space text, and the world
     * pass has no drawing context for it. Fade and range are recomputed here rather than
     * carried over — it is a handful of arithmetic per find, and it keeps the two passes from
     * having to agree on a shared buffer.
     */
    @EventHandler
    private void onRender2D(meteordevelopment.meteorclient.events.render.Render2DEvent event) {
        if (!showLabel.get() || mc.player == null || mc.world == null || found.isEmpty()) return;

        double maxDistance = renderDistance.get();
        double maxDistanceSq = maxDistance * maxDistance;
        Vec3d eye = mc.player.getEyePos();

        for (Map.Entry<BlockPos, Detection> entry : found.entrySet()) {
            BlockPos pos = entry.getKey();
            Detection detection = entry.getValue();

            if (eye.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistanceSq) continue;

            float factor = detection.animation.factor();
            if (factor <= 0.0f) continue;

            org.joml.Vector3d screen = new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5);
            if (!NametagUtils.to2D(screen, labelScale.get())) continue;

            String text = detection.mob + "  " + detection.chests + "x";
            int width = mc.textRenderer.getWidth(text);
            SettingColor base = colorOf(detection.category);

            NametagUtils.begin(screen, event.drawContext);
            event.drawContext.fill(-width / 2 - 2, -2, width / 2 + 2, 10,
                new Color(0, 0, 0, (int) (140 * factor)).getPacked());
            event.drawContext.drawText(mc.textRenderer, text, -width / 2, 0,
                new Color(base.r, base.g, base.b, (int) (255 * factor)).getPacked(), true);
            NametagUtils.end(event.drawContext);
        }
    }

    private SettingColor colorOf(Category category) {
        return switch (category) {
            case DUNGEON -> lootedColor.get();
            case EMPTIED -> emptyColor.get();
            case STASH -> stashColor.get();
            case LIT -> torchColor.get();
            case TRIAL -> trialColor.get();
        };
    }

    private enum Category {
        STASH("Stash"),
        DUNGEON("Dungeon"),
        LIT("Lit"),
        EMPTIED("Emptied"),
        TRIAL("Trial");

        private final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private record Detection(Category category, String mob, int chests, Animation animation) {
    }


    /** Eases a box in when it is found and out when it leaves range. */
    private static final class Animation {
        private final long duration;
        private boolean visible = false;
        private long changedAt = System.currentTimeMillis();

        Animation(long duration) {
            this.duration = duration;
        }

        void setVisible(boolean visible) {
            if (this.visible != visible) {
                this.visible = visible;
                this.changedAt = System.currentTimeMillis();
            }
        }

        float factor() {
            if (duration <= 0) return visible ? 1.0f : 0.0f;

            float progress = Math.min(1.0f, (System.currentTimeMillis() - changedAt) / (float) duration);
            return visible ? progress : 1.0f - progress;
        }
    }
}
