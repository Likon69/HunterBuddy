package com.hunterbuddy.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.hunterbuddy.HunterBuddyAddon;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.mixininterface.IGameRenderer;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3d;

/**
 * Two visual treatments for living entities, both purely client-side:
 *
 * <ul>
 *   <li><b>Scale</b> — mobs and players are drawn bigger or smaller. Applied by
 *       {@code EntityScaleMixin} through the render state's {@code baseScale}.</li>
 *   <li><b>Gear tags</b> — the entity's armour and held items are drawn as item
 *       icons floating above it, with optional durability. Drawn in
 *       {@code Render2DEvent}, i.e. as a screen overlay after the world is
 *       finished, so it is visible through terrain by construction.</li>
 * </ul>
 *
 * <p>That same 2D pass is why the tags are untouched by shader packs: Iris has
 * already finished its post-processing when the HUD is composited. Fake glow or
 * bloom here would read as cheap next to the real thing, so the tag deliberately
 * stays crisp flat UI and leans on contrast instead.
 *
 * <p><b>One API constraint shapes the depth cues.</b> {@code RenderUtils.drawItem}
 * takes no colour, and 1.21.11's {@code DrawContext} exposes no global tint, so
 * item icons cannot be alpha-faded without reimplementing item rendering. Depth
 * is therefore carried by <em>size</em> (which affects everything uniformly) and,
 * for occlusion, by a scrim quad drawn over the icons.
 *
 * <p>Nothing here touches hitboxes, reach, or anything the server sees.
 */
public class EntityView extends Module {
    /** Armor slots, helmet to boots. A row reads: main hand, these four, offhand. */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Longest row we can produce: main hand + four armor pieces + offhand. */
    private static final int MAX_SLOTS = 6;

    public enum Durability {
        None,
        Percentage,
        Total
    }

    private final SettingGroup sgScale  = settings.getDefaultGroup();
    private final SettingGroup sgPlayer = settings.createGroup("Player Scale");
    private final SettingGroup sgTags   = settings.createGroup("Gear Tags");
    private final SettingGroup sgDepth  = settings.createGroup("Tag Depth");

    // ── Scale ─────────────────────────────────────────────────────────────────

    private final Setting<Double> mobScale = sgScale.add(new DoubleSetting.Builder()
        .name("mob-scale")
        .description("Visual scale applied to every mob. 1.0 is normal size.")
        .defaultValue(1.0).min(0.05).sliderRange(0.05, 5.0)
        .build()
    );

    private final Setting<Boolean> nametagsFollowScale = sgScale.add(new BoolSetting.Builder()
        .name("nametags-follow-scale")
        .description("Push the vanilla nametag and Meteor's nametag up or down with the rescaled model.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> playerScale = sgPlayer.add(new DoubleSetting.Builder()
        .name("player-scale")
        .description("Visual scale applied to your own model. Third person only — your hands are unaffected.")
        .defaultValue(1.0).min(0.05).sliderRange(0.05, 5.0)
        .build()
    );

    private final Setting<Boolean> scaleOtherPlayers = sgPlayer.add(new BoolSetting.Builder()
        .name("scale-other-players")
        .description("Also rescale other players.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> otherPlayerScale = sgPlayer.add(new DoubleSetting.Builder()
        .name("other-player-scale")
        .description("Visual scale applied to other players.")
        .defaultValue(1.0).min(0.05).sliderRange(0.05, 5.0)
        .visible(scaleOtherPlayers::get)
        .build()
    );

    // ── Gear tags ─────────────────────────────────────────────────────────────

    private final Setting<Boolean> gearTags = sgTags.add(new BoolSetting.Builder()
        .name("gear-tags")
        .description("Draw the armor and held items of entities as icons above them, through walls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Set<EntityType<?>>> tagEntities = sgTags.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Which entities get a gear tag. Ones carrying nothing are skipped anyway.")
        .defaultValue(
            EntityType.PLAYER,
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED,
            EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.EVOKER, EntityType.ILLUSIONER,
            EntityType.ARMOR_STAND
        )
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Boolean> showMainHand = sgTags.add(new BoolSetting.Builder()
        .name("main-hand")
        .description("Include the main hand item.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Boolean> showArmor = sgTags.add(new BoolSetting.Builder()
        .name("armor")
        .description("Include the four armor pieces.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Boolean> showOffhand = sgTags.add(new BoolSetting.Builder()
        .name("offhand")
        .description("Include the offhand item.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Boolean> keepEmptySlots = sgTags.add(new BoolSetting.Builder()
        .name("keep-empty-slots")
        .description("Leave a gap for empty slots so icons always line up the same way.")
        .defaultValue(false)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Double> tagScale = sgTags.add(new DoubleSetting.Builder()
        .name("tag-scale")
        .description("Overall size of the tag, before perspective.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 4.0)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Double> iconScale = sgTags.add(new DoubleSetting.Builder()
        .name("icon-scale")
        .description("Size of each item icon. 1.0 is a 16 pixel icon.")
        .defaultValue(2.0).min(0.5).sliderRange(0.5, 5.0)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Integer> iconSpacing = sgTags.add(new IntSetting.Builder()
        .name("icon-spacing")
        .description("Gap between icons, in pixels.")
        .defaultValue(2).min(0).sliderRange(0, 16)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Double> tagHeight = sgTags.add(new DoubleSetting.Builder()
        .name("tag-height")
        .description("Extra height above the entity, in blocks. Follows the entity's visual scale.")
        .defaultValue(0.4).min(0.0).sliderRange(0.0, 3.0)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Durability> durability = sgTags.add(new EnumSetting.Builder<Durability>()
        .name("durability")
        .description("Show remaining durability on damageable items.")
        .defaultValue(Durability.None)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgTags.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Never tag your own player.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Double> range = sgTags.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance from the camera.")
        .defaultValue(64.0).min(1.0).sliderRange(1.0, 256.0)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Integer> maxCount = sgTags.add(new IntSetting.Builder()
        .name("max-count")
        .description("Hard cap on how many tags are drawn. The closest entities win.")
        .defaultValue(32).min(1).sliderRange(1, 128)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Boolean> background = sgTags.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw a quad behind the whole tag, text included.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgTags.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of that quad.")
        .defaultValue(new SettingColor(0, 0, 0, 110))
        .visible(() -> gearTags.get() && background.get())
        .build()
    );

    // ── Depth ─────────────────────────────────────────────────────────────────

    private final Setting<Double> textMaxDistance = sgDepth.add(new DoubleSetting.Builder()
        .name("text-max-distance")
        .description("Past this distance only the icons are drawn. The durability text is unreadable long before it stops adding clutter.")
        .defaultValue(28.0).min(0.0).sliderRange(0.0, 128.0)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Double> distanceShrink = sgDepth.add(new DoubleSetting.Builder()
        .name("distance-shrink")
        .description("How much further the tag shrinks with distance. Meteor's own perspective scaling stops at half size, which is why far tags read as loud as near ones. 0 keeps Meteor's behaviour.")
        .defaultValue(0.35).min(0.0).sliderRange(0.0, 0.9)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Double> appearDuration = sgDepth.add(new DoubleSetting.Builder()
        .name("appear-duration")
        .description("Seconds a tag takes to grow in when it appears and shrink out when it leaves. 0 makes it pop.")
        .defaultValue(0.18).min(0.0).sliderRange(0.0, 1.0)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Boolean> dimOccluded = sgDepth.add(new BoolSetting.Builder()
        .name("dim-occluded")
        .description("Fade tags whose entity is behind terrain. Keeps the see-through but lets what you can actually see stand out.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Double> occludedOpacity = sgDepth.add(new DoubleSetting.Builder()
        .name("occluded-opacity")
        .description("How much of a hidden tag survives. Icons are dimmed with a scrim since they cannot be alpha-blended.")
        .defaultValue(0.45).min(0.05).sliderRange(0.05, 1.0)
        .visible(() -> gearTags.get() && dimOccluded.get())
        .build()
    );

    private final Setting<Boolean> declutter = sgDepth.add(new BoolSetting.Builder()
        .name("declutter")
        .description("Push overlapping tags apart vertically instead of letting them pile into mush.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Integer> declutterGap = sgDepth.add(new IntSetting.Builder()
        .name("declutter-gap")
        .description("Pixels left between two tags that had to be separated.")
        .defaultValue(2).min(0).sliderRange(0, 16)
        .visible(() -> gearTags.get() && declutter.get())
        .build()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    /** Entities to draw: the ones that qualify, plus the ones still shrinking out. */
    private final List<LivingEntity> tagged = new ArrayList<>();
    private final List<LivingEntity> previouslyTagged = new ArrayList<>();
    /** Of those, the ones that still qualify. The rest are on their way out. */
    private final IntOpenHashSet activeIds = new IntOpenHashSet();
    private final IntOpenHashSet occludedIds = new IntOpenHashSet();
    private final IntOpenHashSet knownIds = new IntOpenHashSet();
    /** Which tags currently show text. Kept per entity so the LOD can hysterise. */
    private final IntOpenHashSet textEnabledIds = new IntOpenHashSet();

    /** Appear/disappear progress per entity id, 0 to 1. */
    private final Int2FloatOpenHashMap appearProgress = new Int2FloatOpenHashMap();

    /** Laid-out tags for the current frame, reused between frames. */
    private final List<Layout> layouts = new ArrayList<>();
    private int layoutCount;

    private final Vector3d pos = new Vector3d();
    private final Color scratch = new Color();
    private long lastFrameNanos;

    public EntityView() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "entity-view",
            "Rescales mobs and players, and shows their gear above them through walls.");

        appearProgress.defaultReturnValue(0.0f);
    }

    @Override
    public void onDeactivate() {
        tagged.clear();
        previouslyTagged.clear();
        activeIds.clear();
        occludedIds.clear();
        textEnabledIds.clear();
        appearProgress.clear();
        layoutCount = 0;
        lastFrameNanos = 0L;
    }

    // ── Scale API, read by the mixins ─────────────────────────────────────────

    /** Visual scale for a living entity, or 1.0 when nothing applies. */
    public float getScaleFor(LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            if (entity == mc.player) return playerScale.get().floatValue();
            return scaleOtherPlayers.get() ? otherPlayerScale.get().floatValue() : 1.0f;
        }
        return mobScale.get().floatValue();
    }

    /** Whether nametag anchors should be moved to match the rescaled model. */
    public boolean nametagsFollowScale() {
        return nametagsFollowScale.get();
    }

    // ── Collection ────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!gearTags.get() || mc.world == null || mc.player == null || mc.gameRenderer == null) {
            tagged.clear();
            activeIds.clear();
            occludedIds.clear();
            textEnabledIds.clear();
            appearProgress.clear();
            return;
        }

        previouslyTagged.clear();
        previouslyTagged.addAll(tagged);
        tagged.clear();
        activeIds.clear();

        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        double maxDistSq = range.get() * range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!living.isAlive()) continue;
            if (!tagEntities.get().contains(living.getType())) continue;
            if (ignoreSelf.get() && living == mc.player) continue;
            if (living.squaredDistanceTo(camera) > maxDistSq) continue;
            if (!hasAnythingToShow(living)) continue;

            tagged.add(living);
        }

        // Nearest first, so trimming to the cap keeps the closest entities.
        tagged.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(camera)));
        if (tagged.size() > maxCount.get()) tagged.subList(maxCount.get(), tagged.size()).clear();

        for (LivingEntity entity : tagged) activeIds.add(entity.getId());

        // An entity that stops qualifying is kept around until it has finished
        // shrinking out. Without this the shrink never renders: the tag would be
        // gone from the list on the very frame it was supposed to start leaving.
        if (appearDuration.get() > 0.0) {
            for (LivingEntity entity : previouslyTagged) {
                int id = entity.getId();
                if (activeIds.contains(id)) continue;
                if (entity.isRemoved() || appearProgress.get(id) <= 0.0f) continue;

                tagged.add(entity);
            }
        }

        // Raycasts run once per tick, never per frame. A tag on its way out keeps
        // whatever state it last had, otherwise it would brighten as it fades.
        if (dimOccluded.get()) {
            for (LivingEntity entity : tagged) {
                int id = entity.getId();
                if (!activeIds.contains(id)) continue;

                if (isOccluded(camera, entity)) occludedIds.add(id);
                else occludedIds.remove(id);
            }
        } else {
            occludedIds.clear();
        }

        // Drop state for entities that vanished outright, so nothing can grow.
        knownIds.clear();
        for (LivingEntity entity : tagged) knownIds.add(entity.getId());
        appearProgress.keySet().retainAll(knownIds);
        occludedIds.retainAll(knownIds);
        textEnabledIds.retainAll(knownIds);
    }

    /**
     * A single ray to the eyes calls a mob hidden whenever its head happens to be
     * behind a block, which on uneven Nether terrain is most of the time. Three
     * samples down the body, bailing out on the first one that gets through.
     */
    private boolean isOccluded(Vec3d camera, LivingEntity entity) {
        if (!blocked(camera, entity.getEyePos())) return false;
        if (!blocked(camera, entity.getBoundingBox().getCenter())) return false;
        return blocked(camera, entity.getEntityPos().add(0.0, 0.1, 0.0));
    }

    private boolean blocked(Vec3d from, Vec3d to) {
        RaycastContext ctx = new RaycastContext(
            from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        return mc.world.raycast(ctx).getType() == HitResult.Type.BLOCK;
    }

    private boolean hasAnythingToShow(LivingEntity entity) {
        if (showMainHand.get() && !entity.getMainHandStack().isEmpty()) return true;
        if (showOffhand.get() && !entity.getOffHandStack().isEmpty()) return true;
        if (showArmor.get()) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (!entity.getEquippedStack(slot).isEmpty()) return true;
            }
        }
        return false;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!gearTags.get() || mc.world == null || mc.gameRenderer == null) {
            appearProgress.clear();
            return;
        }

        float step = advanceAppearClock();
        boolean shadow = Config.get().customFont.get();
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();

        layoutCount = 0;

        for (LivingEntity entity : tagged) {
            project(entity, camera, event.tickDelta, shadow, step);
        }

        if (layoutCount == 0) return;

        if (declutter.get()) resolveOverlaps();

        // Three phases, because item icons are not drawn when we ask for them.
        // DrawContext#drawItem only queues an element into the GuiRenderState,
        // which the GuiRenderer executes later, while Renderer2D and TextRenderer
        // hit the framebuffer immediately. Drawn in one pass, every icon would
        // land on top of the scrim meant to dim it and on top of the text meant
        // to label it. So: backgrounds, then icons, then a flush, then the rest.
        // Far to near within each phase, so closer tags stay on top.
        for (int i = layoutCount - 1; i >= 0; i--) drawBackground(event, layouts.get(i));
        for (int i = layoutCount - 1; i >= 0; i--) drawIcons(event, layouts.get(i));

        ((IGameRenderer) mc.gameRenderer).meteor$flushGuiState();

        // GuiRenderer installs its own orthographic projection while it drains the
        // state and leaves it behind. Meteor's own renderers read the static
        // RenderUtils.projection and would not notice, but the vanilla text
        // renderer draws through the ambient one — with custom-font off, phase 3
        // would come out at GUI scale. Restoring costs nothing and also covers
        // anything else the flush might leave set.
        Utils.unscaledProjection();

        for (int i = layoutCount - 1; i >= 0; i--) drawOverlay(event, layouts.get(i), shadow);
    }

    /** Frame delta converted into an appear/disappear step, clamped against hitches. */
    private float advanceAppearClock() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0f : (now - lastFrameNanos) / 1.0e9f;
        lastFrameNanos = now;
        dt = Math.max(0.0f, Math.min(dt, 0.1f));

        double duration = appearDuration.get();
        return duration <= 0.0 ? 1.0f : (float) (dt / duration);
    }

    /**
     * Measures one tag and projects it to screen. Nothing is drawn yet: the
     * declutter pass needs every tag's bounds before any of them is committed.
     */
    private void project(LivingEntity entity, Vec3d camera, float tickDelta, boolean shadow, float step) {
        int id = entity.getId();

        // Growing while it qualifies, shrinking once it stops.
        float delta = activeIds.contains(id) ? step : -step;
        float progress = Math.max(0.0f, Math.min(1.0f, appearProgress.get(id) + delta));
        appearProgress.put(id, progress);
        if (progress <= 0.001f) return;

        Layout layout = layoutFor(layoutCount);
        layout.collect(entity);
        if (layout.slots == 0) return;

        double distance = Math.sqrt(entity.squaredDistanceTo(camera));

        // Dead band around the cutoff. On a bare threshold, walking at exactly the
        // limit makes every label flicker on and off frame by frame.
        double turnOn = textMaxDistance.get();
        double turnOff = turnOn * 1.15 + 1.0;
        boolean withText = textEnabledIds.contains(id) ? distance <= turnOff : distance <= turnOn;
        if (withText) textEnabledIds.add(id);
        else textEnabledIds.remove(id);

        layout.withText = withText;
        layout.measure(this);

        Utils.set(pos, entity, tickDelta);
        pos.add(0.0, entity.getHeight() * getScaleFor(entity) + tagHeight.get(), 0.0);

        // Both the appear animation and the extra distance falloff ride on the
        // scale rather than on alpha, because item icons cannot be alpha-blended.
        double falloff = 1.0 - Math.min(1.0, distance / range.get()) * distanceShrink.get();
        if (!NametagUtils.to2D(pos, tagScale.get() * falloff * progress)) return;

        layout.screenX = pos.x;
        layout.screenY = pos.y;
        layout.scale = NametagUtils.scale;
        layout.push = 0.0;
        layout.occluded = occludedIds.contains(id);
        layout.alpha = progress;

        layoutCount++;
    }

    /**
     * Pushes overlapping tags downward until they clear each other. Tags are
     * already ordered nearest first, so the closest one keeps its true position
     * and the ones behind it give way.
     */
    private void resolveOverlaps() {
        double gap = declutterGap.get();

        // Pushing a tag clear of one neighbour can drive it into another that was
        // already checked, so a single sweep leaves overlaps behind in a dense
        // cluster. Repeat until nothing moves, bounded so a pathological pile-up
        // cannot eat the frame.
        for (int pass = 0; pass < 4; pass++) {
            boolean moved = false;

            for (int i = 1; i < layoutCount; i++) {
                Layout a = layouts.get(i);

                for (int j = 0; j < i; j++) {
                    Layout b = layouts.get(j);

                    if (a.right() <= b.left() || a.left() >= b.right()) continue;
                    if (a.top() >= b.bottom() || a.bottom() <= b.top()) continue;

                    a.push += (b.bottom() - a.top()) + gap;
                    moved = true;
                }
            }

            if (!moved) break;
        }
    }

    /** Effective opacity of a tag: its appear progress, dimmed when occluded. */
    private float alphaOf(Layout layout) {
        float alpha = layout.alpha;
        if (layout.occluded && dimOccluded.get()) alpha *= occludedOpacity.get().floatValue();
        return alpha;
    }

    private void begin(Render2DEvent event, Layout layout) {
        pos.set(layout.screenX, layout.screenY + layout.push, 0.0);
        NametagUtils.scale = layout.scale;
        NametagUtils.begin(pos, event.drawContext);
    }

    private void drawBackground(Render2DEvent event, Layout layout) {
        if (!background.get()) return;

        begin(event, layout);

        double height = -layout.blockTop;
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(
            -layout.totalWidth / 2.0 - 2, layout.blockTop - 2,
            layout.totalWidth + 4, height + 4,
            fade(backgroundColor.get(), alphaOf(layout)));
        Renderer2D.COLOR.render();

        NametagUtils.end(event.drawContext);
    }

    private void drawIcons(Render2DEvent event, Layout layout) {
        begin(event, layout);

        double x = -layout.totalWidth / 2.0;
        double iconTop = -layout.iconSize;

        for (int i = 0; i < layout.slots; i++) {
            ItemStack stack = layout.row.get(i);

            if (!stack.isEmpty()) {
                double iconX = x + (layout.cellWidths[i] - layout.iconSize) / 2.0;
                RenderUtils.drawItem(event.drawContext, stack,
                    (int) Math.round(iconX), (int) Math.round(iconTop),
                    layout.iconScale, true, null, false);
            }

            x += layout.cellWidths[i] + layout.spacing;
        }

        NametagUtils.end(event.drawContext);
    }

    /** Everything that has to sit above the icons: text, then the occlusion scrim. */
    private void drawOverlay(Render2DEvent event, Layout layout, boolean shadow) {
        float alpha = alphaOf(layout);
        boolean scrimNeeded = alpha < 0.999f;
        if (!layout.withText && !scrimNeeded) return;

        TextRenderer text = TextRenderer.get();
        begin(event, layout);

        double x = -layout.totalWidth / 2.0;
        double iconTop = -layout.iconSize;

        if (layout.withText) {
            double tx = x;

            for (int i = 0; i < layout.slots; i++) {
                ItemStack stack = layout.row.get(i);

                if (!stack.isEmpty()) {
                    double iconX = tx + (layout.cellWidths[i] - layout.iconSize) / 2.0;
                    drawDurability(text, stack, iconX, iconTop, alpha, shadow);
                }

                tx += layout.cellWidths[i] + layout.spacing;
            }
        }

        // Icons ignore alpha, so a hidden tag is dimmed with a scrim instead. Laid
        // per icon rather than across the whole strip: the gaps between cells show
        // the background, which is already faded, and darkening them twice makes
        // the backing look patchy.
        if (scrimNeeded) {
            int scrim = Math.round((1.0f - alpha) * 190);

            if (scrim > 0) {
                Renderer2D.COLOR.begin();
                double sx = x;

                for (int i = 0; i < layout.slots; i++) {
                    if (!layout.row.get(i).isEmpty()) {
                        double iconX = sx + (layout.cellWidths[i] - layout.iconSize) / 2.0;
                        Renderer2D.COLOR.quad(iconX, iconTop, layout.iconSize, layout.iconSize,
                            scratch.set(0, 0, 0, scrim));
                    }
                    sx += layout.cellWidths[i] + layout.spacing;
                }

                Renderer2D.COLOR.render();
            }
        }

        NametagUtils.end(event.drawContext);
    }

    private void drawDurability(TextRenderer text, ItemStack stack, double x, double y, float alpha, boolean shadow) {
        if (durability.get() == Durability.None || !stack.isDamageable()) return;

        int max = stack.getMaxDamage();
        if (max <= 0) return;
        int left = max - stack.getDamage();

        String label = switch (durability.get()) {
            case Percentage -> String.format("%.0f%%", (left * 100f) / (float) max);
            case Total -> Integer.toString(left);
            default -> "";
        };

        Color base = new Color(stack.getItemBarColor()).a(255);
        text.begin(0.75 * iconScale.get(), false, true);
        text.render(label, x, y, fade(base, alpha), shadow);
        text.end();
    }


    private Color fade(Color base, float alpha) {
        return scratch.set(base.r, base.g, base.b, Math.round(base.a * Math.max(0.0f, Math.min(1.0f, alpha))));
    }

    private Layout layoutFor(int index) {
        while (layouts.size() <= index) layouts.add(new Layout());
        return layouts.get(index);
    }

    @Override
    public String getInfoString() {
        return gearTags.get() ? Integer.toString(layoutCount) : null;
    }

    /**
     * One tag's measured contents and screen placement. Pooled and reused, so a
     * busy frame allocates nothing at all.
     */
    private final class Layout {
        final List<ItemStack> row = new ArrayList<>(MAX_SLOTS);
        final double[] cellWidths = new double[MAX_SLOTS];

        int slots;
        boolean withText;
        float iconScale;
        double iconSize;
        double spacing;
        double totalWidth;
        /** Top of everything drawn, in local units. Negative is upward. */
        double blockTop;

        double screenX;
        double screenY;
        double scale;
        double push;
        boolean occluded;
        float alpha;

        Layout() {
        }

        void collect(LivingEntity entity) {
            row.clear();

            if (showMainHand.get()) add(entity.getMainHandStack());
            if (showArmor.get()) {
                for (EquipmentSlot slot : ARMOR_SLOTS) add(entity.getEquippedStack(slot));
            }
            if (showOffhand.get()) add(entity.getOffHandStack());

            // A run of placeholders at either end would just pad the row with dead space.
            if (keepEmptySlots.get()) {
                while (!row.isEmpty() && row.get(row.size() - 1).isEmpty()) row.remove(row.size() - 1);
                while (!row.isEmpty() && row.get(0).isEmpty()) row.remove(0);
            }

            slots = row.size();
        }

        private void add(ItemStack stack) {
            if (stack.isEmpty() && !keepEmptySlots.get()) return;
            row.add(stack);
        }

        /** Cell widths and the true top of the tag, which the background covers. */
        void measure(EntityView view) {
            iconScale = view.iconScale.get().floatValue();
            iconSize = 16.0 * iconScale;

            // Rounded so the row lands on whole pixels. Fractional widths make the
            // icons, which drawItem quantises, drift against the background as the
            // distance scale changes.
            double cell = Math.ceil(iconSize);

            spacing = view.iconSpacing.get();
            for (int i = 0; i < slots; i++) cellWidths[i] = cell;

            // Spacing goes between cells, not after the last one. Counting it
            // everywhere left dead space on the right and pushed the row's optical
            // centre half a gap to the left.
            totalWidth = cell * slots + (slots > 1 ? spacing * (slots - 1) : 0.0);
            blockTop = -iconSize;
        }

        // Screen-space bounds used by the declutter pass. Local units are scaled
        // the same way the matrix scales them, so the comparison is consistent
        // without needing the window scale factor.
        double left()   { return screenX - (totalWidth / 2.0) * scale; }
        double right()  { return screenX + (totalWidth / 2.0) * scale; }
        double top()    { return screenY + push + blockTop * scale; }
        double bottom() { return screenY + push; }
    }
}
