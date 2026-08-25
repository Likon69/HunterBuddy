package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-portal builder: places the 10 obsidian frame blocks in front of the
 * player, lights the portal with flint-and-steel, and (optionally) uses
 * Baritone to path the player into the portal center.
 *
 * <p>Frame placement logic + SWAP_ITEM_WITH_OFFHAND placement trick is from
 * meteor-stashhunting-addon's {@code AutoPortal}. The Baritone teleport
 * is the HunterBuddy extension.
 */
public class AutoPortal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final List<BlockPos> waitingForBreak = new ArrayList<>();

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between each obsidian placement.")
        .defaultValue(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place each tick.")
        .defaultValue(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the portal frame as it's being placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(100, 100, 255, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(100, 100, 255, 255))
        .build()
    );

    private final Setting<Boolean> interiorPreview = sgGeneral.add(new BoolSetting.Builder()
        .name("interior-preview")
        .description("Outlines the opening the portal will fill while the frame is still going up, so you can see where it lands before it exists.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> highlightLitPortal = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-lit-portal")
        .description("Highlights the portal you lit (only yours) for render-duration seconds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renderDuration = sgGeneral.add(new IntSetting.Builder()
        .name("render-duration")
        .description("Seconds the lit-portal highlight stays visible after activation.")
        .defaultValue(20)
        .min(5)
        .max(60)
        .sliderRange(5, 60)
        .build()
    );

    private final Setting<SettingColor> portalSideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("portal-side-color")
        .description("Fill color of the lit-portal highlight.")
        .defaultValue(new SettingColor(170, 0, 255, 80))
        .build()
    );

    private final Setting<SettingColor> portalLineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("portal-line-color")
        .description("Outline color of the lit-portal highlight.")
        .defaultValue(new SettingColor(170, 0, 255, 255))
        .build()
    );

    private final Setting<Boolean> baritonePath = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone-path-to-portal")
        .description("After lighting, use Baritone to pathfind the player into the portal center.")
        .defaultValue(true)
        .build()
    );

    private final List<BlockPos> portalBlocks = new ArrayList<>();
    private int delay = 0;
    private int index = 0;

    // Direction perpendicular to the player's facing at onActivate.
    // Stored so the lit-portal highlight renders the interior in the correct orientation
    // (interior width is along `right`, interior height is always +Y).
    private Direction portalRight;

    // Tracks the portals I lit: anchor (interior lower-left corner BlockPos) -> LitPortal.
    // Stores the right-direction so render works regardless of the player's facing at activation.
    // Only portals activated by THIS AutoPortal instance are stored, so no scan-all-portals logic.
    private record LitPortal(Direction right, long expiresAtMs) {}
    private final LinkedHashMap<BlockPos, LitPortal> litPortalAnchors = new LinkedHashMap<>();

    // Phase machine: BUILDING places blocks, WAITING lets the server activate
    // the portal (server needs ~2-3 ticks after the fire packet before the
    // portal block is active), DONE has either set Baritone or finished.
    // Previously the lighting packet and the Baritone goal were sent in the
    // same tick, so Baritone started pathing into an inactive portal and the
    // player could bounce off the frame.
    private enum Phase { BUILDING, WAITING, DONE }
    private Phase phase = Phase.BUILDING;
    private int waitTicksRemaining = 0;

    /** How long the lit-portal highlight spends fading out at the end of its life. */
    private static final float HIGHLIGHT_FADE_MS = 2000.0F;

    public AutoPortal() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "auto-portal",
            "Builds a nether portal frame, lights it, and (optionally) paths the player into it via Baritone.");
    }

    @Override
    public void onActivate() {
        phase = Phase.BUILDING;
        waitTicksRemaining = 0;
        int obsidianCount = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN) {
                obsidianCount += mc.player.getInventory().getStack(i).getCount();
            }
        }

        if (obsidianCount < 10) {
            error("Not enough obsidian to build the portal (need at least 10)!");
            toggle();
            return;
        }
        portalBlocks.clear();
        index = 0;
        delay = 0;

        Direction forward = mc.player.getHorizontalFacing();
        Direction right = forward.rotateYClockwise();
        BlockPos standingPos = mc.player.getBlockPos();
        BlockPos blockBelow = standingPos.down();
        double blockHeight = mc.world.getBlockState(blockBelow).getCollisionShape(mc.world, blockBelow).getMax(Direction.Axis.Y);
        if (blockHeight < 1.0) {
            standingPos = standingPos.up();
        }
        BlockPos base = standingPos
            .offset(forward, 2)
            .offset(right, -1);
        this.portalRight = right;

        List<BlockPos> checkPositions = List.of(
            base.offset(right, 1), base.offset(right, 2),
            base.offset(right, 0).up(1), base.offset(right, 0).up(2), base.offset(right, 0).up(3),
            base.offset(right, 3).up(1), base.offset(right, 3).up(2), base.offset(right, 3).up(3),
            base.offset(right, 1).up(4), base.offset(right, 2).up(4)
        );

        // Interior air blocks (2 wide x 3 tall) that the portal fills and the player walks through.
        // These must be clear too, otherwise the frame ends up buried in a wall and the portal can't light.
        List<BlockPos> interiorPositions = List.of(
            base.offset(right, 1).up(1), base.offset(right, 1).up(2), base.offset(right, 1).up(3),
            base.offset(right, 2).up(1), base.offset(right, 2).up(2), base.offset(right, 2).up(3)
        );

        // Refuse to build (and refuse to render) when anything blocks the frame or the interior.
        // A frame slot that is already obsidian is NOT an obstruction (building skips it / lets us
        // resume a partial frame); a non-obsidian solid block is. The interior must be fully clear.
        // Previously this only pre-filled portalBlocks and set index = size, but onTick's
        // "retry if frame incomplete" branch reset index to 0 and force-built through the wall.
        boolean obstructed = checkPositions.stream().anyMatch(pos ->
                !mc.world.getBlockState(pos).isReplaceable() && !mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN))
            || interiorPositions.stream().anyMatch(pos -> !mc.world.getBlockState(pos).isReplaceable());
        if (obstructed) {
            error("Portal area obstructed. Move and try again.");
            toggle();
            return;
        }

        int obsidianCheck = 0;
        for (BlockPos checkPos : checkPositions) {
            if (mc.world.getBlockState(checkPos).getBlock().asItem() == Items.OBSIDIAN) {
                obsidianCheck++;
            }
        }

        if (obsidianCheck >= checkPositions.size()) {
            error("A portal already exists here!");
            toggle();
            return;
        }

        portalBlocks.add(base.offset(right, 1));
        portalBlocks.add(base.offset(right, 2));

        for (int i = 1; i <= 3; i++) {
            portalBlocks.add(base.offset(right, 0).up(i));
        }

        for (int i = 1; i <= 3; i++) {
            portalBlocks.add(base.offset(right, 3).up(i));
        }

        portalBlocks.add(base.offset(right, 1).up(4));
        portalBlocks.add(base.offset(right, 2).up(4));

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN) {
                ((com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor)mc.player.getInventory()).setSelectedSlot(i);
                break;
            }
        }
    }

    @Override
    public void onDeactivate() {
        portalBlocks.clear();
        index = 0;
        delay = 0;
        litPortalAnchors.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (phase == Phase.WAITING) {
            waitTicksRemaining--;
            if (waitTicksRemaining <= 0) {
                if (!isPortalLit()) {
                    info("Portal activation was not confirmed. Checking the frame and retrying.");
                    phase = Phase.BUILDING;
                    index = 0;
                    delay = 0;
                    return;
                }

                // Register this portal as "lit by me" so onRender can highlight it.
                // NOTE: only toggle() if there is nothing to keep alive onRender for.
                // Otherwise onDeactivate() would clear litPortalAnchors immediately.
                if (highlightLitPortal.get() && portalBlocks.size() >= 10 && portalRight != null) {
                    BlockPos anchor = portalBlocks.get(0).up();
                    long expiry = System.currentTimeMillis() + renderDuration.get() * 1000L;
                    litPortalAnchors.put(anchor, new LitPortal(portalRight, expiry));
                }

                if (baritonePath.get() && portalBlocks.size() >= 10) {
                    BlockPos portalCenter = portalBlocks.get(0).up();
                    try {
                        BaritoneAPI.getProvider().getPrimaryBaritone()
                            .getCustomGoalProcess().setGoalAndPath(new GoalBlock(portalCenter));
                        info("Portal activated. Baritone pathing into portal.");
                    } catch (Throwable t) {
                        info("Portal activated. Baritone pathing failed: " + t.getMessage());
                    }
                } else {
                    info("Portal activated. AutoPortal disabled.");
                }
                phase = Phase.DONE;
                if (!highlightLitPortal.get() || litPortalAnchors.isEmpty()) {
                    toggle();
                }
            }
            return;
        }

        if (phase == Phase.DONE) {
            // Keep cleaning expired entries; auto-disable once all highlights have expired.
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<BlockPos, LitPortal>> it = litPortalAnchors.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().expiresAtMs() <= now) it.remove();
            }
            if (litPortalAnchors.isEmpty()) {
                toggle();
            }
            return;
        }

        if (index >= portalBlocks.size()) {
            if (!isFrameComplete()) {
                index = 0;
                delay = 0;
            } else {
                lightPortal();
                return;
            }
        }

        if (!selectHotbarItem(Items.OBSIDIAN)) return;

        delay++;
        if (delay < placeDelay.get()) return;
        for (int i = 0; i < blocksPerTick.get() && index < portalBlocks.size(); i++) {
            BlockPos pos = portalBlocks.get(index);
            if (mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) {
                waitingForBreak.remove(pos);
                index++;
                continue;
            }

            if (!mc.world.getBlockState(pos).isReplaceable()) {
                if (!waitingForBreak.contains(pos)) {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.attackBlock(pos, Direction.UP);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        waitingForBreak.add(pos);
                    }
                }
                return;
            }

            waitingForBreak.remove(pos);

            BlockHitResult bhr = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);

            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            mc.player.swingHand(Hand.MAIN_HAND);
            index++;
        }
        delay = 0;

        if (index >= portalBlocks.size()) {
            if (!isFrameComplete()) {
                index = 0;
                delay = 0;
                return;
            }

            lightPortal();
        }
    }

    private boolean isFrameComplete() {
        return portalBlocks.size() == 10
            && portalBlocks.stream().allMatch(pos -> mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN));
    }

    private boolean isPortalLit() {
        if (portalBlocks.size() != 10) return false;

        BlockPos leftInterior = portalBlocks.get(0).up();
        BlockPos rightInterior = portalBlocks.get(1).up();
        for (int y = 0; y < 3; y++) {
            if (!mc.world.getBlockState(leftInterior.up(y)).isOf(Blocks.NETHER_PORTAL)
                || !mc.world.getBlockState(rightInterior.up(y)).isOf(Blocks.NETHER_PORTAL)) {
                return false;
            }
        }

        return true;
    }

    private void lightPortal() {
        if (!selectHotbarItem(Items.FLINT_AND_STEEL)) {
            error("No flint and steel in hotbar.");
            toggle();
            return;
        }

        BlockPos firePos = portalBlocks.get(0).up();
        BlockHitResult fireHit = new BlockHitResult(Vec3d.ofCenter(firePos), Direction.UP, firePos, false);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fireHit);
        mc.player.swingHand(Hand.MAIN_HAND);

        // The server needs a few ticks to activate the portal block after the
        // fire interaction, so do not hand control to Baritone immediately.
        phase = Phase.WAITING;
        waitTicksRemaining = 10;
        delay = 0;
    }

    private boolean selectHotbarItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                ((com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(i);
                return true;
            }
        }

        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get()) renderFrame(event);
        if (highlightLitPortal.get()) renderLitPortals(event);
    }

    /**
     * The frame under construction, in three weights.
     *
     * <p>One look answers three questions instead of one. The blocks already down
     * keep a thin outline, so the shape of the portal reads from the very first
     * one rather than appearing all at once at the end. What is still missing
     * stays solid. And the one going down next is drawn full size at full
     * strength while the ones behind it shrink and fade, so the order the module
     * works in is visible instead of being guessed from boxes disappearing.
     */
    private void renderFrame(Render3DEvent event) {
        if (portalBlocks.isEmpty()) return;

        // Before the frame, so the frame draws over it.
        if (interiorPreview.get() && phase == Phase.BUILDING && portalRight != null) {
            event.renderer
                .box(
                    interiorBox(portalBlocks.get(0).up(), portalRight),
                    fade(portalSideColor.get(), 0.3F),
                    fade(portalLineColor.get(), 0.4F),
                    ShapeMode.Both,
                    0
                );
        }

        for (int i = 0; i < index && i < portalBlocks.size(); i++) {
            event.renderer.box(portalBlocks.get(i), fade(sideColor.get(), 0.0F), fade(lineColor.get(), 0.3F), ShapeMode.Lines, 0);
        }

        for (int i = index; i < portalBlocks.size(); i++) {
            BlockPos pos = portalBlocks.get(i);
            boolean next = i == index;
            int rank = Math.min(i - index, 4);
            float dim = next ? 1.0F : 0.85F - 0.15F * rank;
            double inset = next ? 0.0 : 0.1 + 0.02 * rank;

            event.renderer
                .box(
                    new Box(
                        pos.getX() + inset,
                        pos.getY() + inset,
                        pos.getZ() + inset,
                        pos.getX() + 1.0 - inset,
                        pos.getY() + 1.0 - inset,
                        pos.getZ() + 1.0 - inset
                    ),
                    fade(sideColor.get(), dim),
                    fade(lineColor.get(), dim),
                    shapeMode.get(),
                    0
                );
        }
    }

    /** Portals I lit, as one opening rather than six stacked cubes, fading out at the end. */
    private void renderLitPortals(Render3DEvent event) {
        if (litPortalAnchors.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, LitPortal>> it = litPortalAnchors.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<BlockPos, LitPortal> e = it.next();
            long left = e.getValue().expiresAtMs() - now;
            if (left <= 0L) {
                it.remove();
                continue;
            }

            // Fades over its last two seconds instead of blinking out.
            float factor = left >= HIGHLIGHT_FADE_MS ? 1.0F : (float)left / HIGHLIGHT_FADE_MS;
            event.renderer
                .box(
                    interiorBox(e.getKey(), e.getValue().right()),
                    fade(portalSideColor.get(), factor),
                    fade(portalLineColor.get(), factor),
                    ShapeMode.Both,
                    0
                );
        }
    }

    /** The opening as a single box: 2 wide along {@code right}, 3 tall, from the anchor up. */
    private Box interiorBox(BlockPos anchor, Direction right) {
        BlockPos far = anchor.offset(right, 1).up(2);

        return new Box(
            Math.min(anchor.getX(), far.getX()),
            anchor.getY(),
            Math.min(anchor.getZ(), far.getZ()),
            Math.max(anchor.getX(), far.getX()) + 1.0,
            far.getY() + 1.0,
            Math.max(anchor.getZ(), far.getZ()) + 1.0
        );
    }

    /** The configured colour at a fraction of its own alpha, so the menu still rules. */
    private Color fade(SettingColor base, float factor) {
        return new Color(base.r, base.g, base.b, MathHelper.clamp(Math.round(base.a * factor), 0, 255));
    }
}
