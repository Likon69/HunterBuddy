package com.hunterbuddy.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.hunterbuddy.HunterBuddyAddon;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnchantmentListSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

/**
 * Two visual treatments for living entities, both purely client-side:
 *
 * <ul>
 *   <li><b>Scale</b> — mobs and players are drawn bigger or smaller. Applied by
 *       {@code EntityScaleMixin} through the render state's {@code baseScale}.</li>
 *   <li><b>Gear tags</b> — the entity's armour and held items are drawn as item
 *       icons floating above it, with durability and enchantments, the way
 *       Meteor's own nametags do it for players only. Drawn in
 *       {@code Render2DEvent}, i.e. as a screen overlay after the world is
 *       finished, so it is visible through terrain by construction — there is no
 *       depth test to disable.</li>
 * </ul>
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

    private static final Color CURSE_COLOR = new Color(255, 60, 60, 255);

    public enum Durability {
        None,
        Percentage,
        Total
    }

    public enum EnchantPos {
        Above,
        OnTop
    }

    private final SettingGroup sgScale  = settings.getDefaultGroup();
    private final SettingGroup sgPlayer = settings.createGroup("Player Scale");
    private final SettingGroup sgTags   = settings.createGroup("Gear Tags");

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

    private final Setting<Boolean> showEnchants = sgTags.add(new BoolSetting.Builder()
        .name("enchantments")
        .description("List each item's enchantments next to its icon.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<Set<RegistryKey<Enchantment>>> shownEnchants = sgTags.add(new EnchantmentListSetting.Builder()
        .name("shown-enchantments")
        .description("Which enchantments are worth printing.")
        .vanillaDefaults()
        .visible(() -> gearTags.get() && showEnchants.get())
        .build()
    );

    private final Setting<EnchantPos> enchantPos = sgTags.add(new EnumSetting.Builder<EnchantPos>()
        .name("enchantment-position")
        .description("Above the icons, or stacked on top of them.")
        .defaultValue(EnchantPos.Above)
        .visible(() -> gearTags.get() && showEnchants.get())
        .build()
    );

    private final Setting<Integer> enchantLength = sgTags.add(new IntSetting.Builder()
        .name("enchantment-length")
        .description("How many letters of the enchantment name to keep.")
        .defaultValue(3).min(1).sliderRange(1, 12)
        .visible(() -> gearTags.get() && showEnchants.get())
        .build()
    );

    private final Setting<Double> enchantScale = sgTags.add(new DoubleSetting.Builder()
        .name("enchantment-scale")
        .description("Size of the enchantment text.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 2.0)
        .visible(() -> gearTags.get() && showEnchants.get())
        .build()
    );

    private final Setting<SettingColor> enchantColor = sgTags.add(new ColorSetting.Builder()
        .name("enchantment-color")
        .description("Color of the enchantment text. Curses are always drawn red.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> gearTags.get() && showEnchants.get())
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
        .description("Draw a quad behind the icons.")
        .defaultValue(true)
        .visible(gearTags::get)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgTags.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of that quad.")
        .defaultValue(new SettingColor(0, 0, 0, 75))
        .visible(() -> gearTags.get() && background.get())
        .build()
    );

    // ── State ─────────────────────────────────────────────────────────────────

    private final List<LivingEntity> tagged = new ArrayList<>();
    private final List<ItemStack> row = new ArrayList<>(MAX_SLOTS);
    private final double[] cellWidths = new double[MAX_SLOTS];

    /** Per-cell enchantment labels and their curse flags, reused between frames. */
    private final List<List<String>> cellEnchants = new ArrayList<>(MAX_SLOTS);
    private final List<List<Boolean>> cellCurses = new ArrayList<>(MAX_SLOTS);

    private final Vector3d pos = new Vector3d();

    public EntityView() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "entity-view",
            "Rescales mobs and players, and shows their gear above them through walls.");

        for (int i = 0; i < MAX_SLOTS; i++) {
            cellEnchants.add(new ArrayList<>());
            cellCurses.add(new ArrayList<>());
        }
    }

    @Override
    public void onDeactivate() {
        tagged.clear();
        row.clear();
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

    // ── Gear tag collection ───────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tagged.clear();
        if (!gearTags.get() || mc.world == null || mc.player == null || mc.gameRenderer == null) return;

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

    /**
     * Fills {@link #row} with the slots to draw, in main hand → helmet →
     * chestplate → leggings → boots → offhand order. Empty stacks are included
     * only when the user asked for fixed positions.
     */
    private void collectRow(LivingEntity entity) {
        row.clear();

        if (showMainHand.get()) addSlot(entity.getMainHandStack());
        if (showArmor.get()) {
            for (EquipmentSlot slot : ARMOR_SLOTS) addSlot(entity.getEquippedStack(slot));
        }
        if (showOffhand.get()) addSlot(entity.getOffHandStack());

        // A run of placeholders at either end would just pad the row with dead space.
        if (keepEmptySlots.get()) {
            while (!row.isEmpty() && row.get(row.size() - 1).isEmpty()) row.remove(row.size() - 1);
            while (!row.isEmpty() && row.get(0).isEmpty()) row.remove(0);
        }
    }

    private void addSlot(ItemStack stack) {
        if (stack.isEmpty() && !keepEmptySlots.get()) return;
        row.add(stack);
    }

    // ── Gear tag rendering ────────────────────────────────────────────────────

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!gearTags.get() || tagged.isEmpty() || mc.world == null) return;

        boolean shadow = Config.get().customFont.get();

        // Far to near, so closer tags end up on top.
        for (int i = tagged.size() - 1; i >= 0; i--) {
            LivingEntity entity = tagged.get(i);
            if (!entity.isAlive()) continue;

            collectRow(entity);
            if (row.isEmpty()) continue;

            Utils.set(pos, entity, event.tickDelta);
            pos.add(0.0, entity.getHeight() * getScaleFor(entity) + tagHeight.get(), 0.0);

            if (!NametagUtils.to2D(pos, tagScale.get())) continue;

            renderRow(event, shadow);
        }
    }

    /**
     * Lays out one gear row. Each slot gets its own cell width: an enchantment
     * label is regularly wider than the 16 pixel icon it belongs to, and a fixed
     * pitch would overlap the neighbouring labels.
     */
    private void renderRow(Render2DEvent event, boolean shadow) {
        TextRenderer text = TextRenderer.get();

        float icon = iconScale.get().floatValue();
        double cell = 16.0 * icon;
        boolean enchants = showEnchants.get();

        int slots = row.size();
        double total = 0.0;
        int maxLines = 0;

        for (int i = 0; i < slots; i++) {
            ItemStack stack = row.get(i);
            double width = cell + iconSpacing.get();

            List<String> labels = cellEnchants.get(i);
            List<Boolean> curses = cellCurses.get(i);
            labels.clear();
            curses.clear();

            if (enchants && !stack.isEmpty()) {
                ItemEnchantmentsComponent component = EnchantmentHelper.getEnchantments(stack);

                for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
                    if (!entry.matches(shownEnchants.get()::contains)) continue;

                    String label = Utils.getEnchantSimpleName(entry, enchantLength.get())
                        + " " + component.getLevel(entry);

                    labels.add(label);
                    curses.add(entry.isIn(EnchantmentTags.CURSE));

                    // getWidth measures at full size while the label is drawn at
                    // 0.5 * enchantment-scale, so fold that factor in. Meteor hardcodes
                    // the 0.5 and ignores its own text-scale setting, which makes its
                    // cells too narrow as soon as that setting leaves 1.0.
                    width = Math.max(width, text.getWidth(label, shadow) * 0.5 * enchantScale.get());
                }

                maxLines = Math.max(maxLines, labels.size());
            }

            cellWidths[i] = width;
            total += width;
        }

        double x = -total / 2.0;
        double y = -cell;

        NametagUtils.begin(pos, event.drawContext);

        if (background.get()) {
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(x - 1, y - 1, total + 2, cell + 2, backgroundColor.get());
            Renderer2D.COLOR.render();
        }

        for (int i = 0; i < slots; i++) {
            ItemStack stack = row.get(i);

            if (!stack.isEmpty()) {
                RenderUtils.drawItem(event.drawContext, stack, (int) x, (int) y, icon, true, null, false);
                renderDurability(text, stack, x, y, shadow);
                if (enchants && maxLines > 0) renderEnchants(text, i, x, y, cell, shadow);
            }

            x += cellWidths[i];
        }

        NametagUtils.end(event.drawContext);
    }

    private void renderDurability(TextRenderer text, ItemStack stack, double x, double y, boolean shadow) {
        if (durability.get() == Durability.None || !stack.isDamageable()) return;

        int max = stack.getMaxDamage();
        if (max <= 0) return;
        int left = max - stack.getDamage();

        String label = switch (durability.get()) {
            case Percentage -> String.format("%.0f%%", (left * 100f) / (float) max);
            case Total -> Integer.toString(left);
            default -> "";
        };

        text.begin(0.75 * iconScale.get(), false, true);
        text.render(label, x, y, new Color(stack.getItemBarColor()).a(255), shadow);
        text.end();
    }

    private void renderEnchants(TextRenderer text, int cellIndex, double x, double y, double cell, boolean shadow) {
        List<String> labels = cellEnchants.get(cellIndex);
        if (labels.isEmpty()) return;

        List<Boolean> curses = cellCurses.get(cellIndex);
        double width = cellWidths[cellIndex];

        text.begin(0.5 * enchantScale.get(), false, true);

        double lineHeight = text.getHeight(shadow);
        double offsetY = switch (enchantPos.get()) {
            case Above -> -((labels.size() + 1) * lineHeight);
            case OnTop -> (cell - labels.size() * lineHeight) / 2.0;
        };

        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            double labelWidth = text.getWidth(label, shadow);

            double labelX = switch (enchantPos.get()) {
                case Above -> x + (width / 2.0) - (labelWidth / 2.0);
                case OnTop -> x + (width - labelWidth) / 2.0;
            };

            Color color = curses.get(i) ? CURSE_COLOR : enchantColor.get();
            text.render(label, labelX, y + offsetY + i * lineHeight, color, shadow);
        }

        text.end();
    }

    @Override
    public String getInfoString() {
        return gearTags.get() ? Integer.toString(tagged.size()) : null;
    }
}
