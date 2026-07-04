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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

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

    private final Setting<Boolean> baritonePath = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone-path-to-portal")
        .description("After lighting, use Baritone to pathfind the player into the portal center.")
        .defaultValue(true)
        .build()
    );

    private final List<BlockPos> portalBlocks = new ArrayList<>();
    private int delay = 0;
    private int index = 0;

    // Phase machine: BUILDING places blocks, WAITING lets the server activate
    // the portal (server needs ~2-3 ticks after the fire packet before the
    // portal block is active), DONE has either set Baritone or finished.
    // Previously the lighting packet and the Baritone goal were sent in the
    // same tick, so Baritone started pathing into an inactive portal and the
    // player could bounce off the frame.
    private enum Phase { BUILDING, WAITING, DONE }
    private Phase phase = Phase.BUILDING;
    private int waitTicksRemaining = 0;

    public AutoPortal() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "hb-auto-portal",
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

        List<BlockPos> checkPositions = List.of(
            base.offset(right, 1), base.offset(right, 2),
            base.offset(right, 0).up(1), base.offset(right, 0).up(2), base.offset(right, 0).up(3),
            base.offset(right, 3).up(1), base.offset(right, 3).up(2), base.offset(right, 3).up(3),
            base.offset(right, 1).up(4), base.offset(right, 2).up(4)
        );

        boolean obstructed = checkPositions.stream().anyMatch(pos -> !mc.world.getBlockState(pos).isReplaceable());
        if (obstructed) {
            error("Portal area obstructed. Move and try again.");
            portalBlocks.clear();
            portalBlocks.addAll(checkPositions);
            index = checkPositions.size();
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
                mc.player.getInventory().selectedSlot = i;
                break;
            }
        }
    }

    @Override
    public void onDeactivate() {
        portalBlocks.clear();
        index = 0;
        delay = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (phase == Phase.WAITING) {
            waitTicksRemaining--;
            if (waitTicksRemaining <= 0) {
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
                toggle();
            }
            return;
        }

        if (phase == Phase.DONE) return;

        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem blockItem)) return;
        if (blockItem.getBlock().asItem() != Items.OBSIDIAN) return;

        if (index >= portalBlocks.size()) {
            toggle();
            return;
        }

        delay++;
        if (delay < placeDelay.get()) return;
        for (int i = 0; i < blocksPerTick.get() && index < portalBlocks.size(); i++, index++) {
            BlockPos pos = portalBlocks.get(index);
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                if (!waitingForBreak.contains(pos) && mc.world.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN) {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.attackBlock(pos, Direction.UP);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        waitingForBreak.add(pos);
                    }
                }
                index--;
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
        }
        delay = 0;

        if (index >= portalBlocks.size()) {
            // Auto-light
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.FLINT_AND_STEEL) {
                    mc.player.getInventory().selectedSlot = i;

                    BlockPos firePos = portalBlocks.get(0).up();
                    BlockHitResult fireHit = new BlockHitResult(Vec3d.ofCenter(firePos), Direction.UP, firePos, false);

                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, fireHit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    break;
                }
            }

            // Switch to WAITING phase. The server needs a few ticks to
            // activate the portal block after the fire packet, so we
            // don't immediately hand control to Baritone.
            phase = Phase.WAITING;
            waitTicksRemaining = 10;
            delay = 0;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        for (int i = index; i < portalBlocks.size(); i++) {
            BlockPos pos = portalBlocks.get(i);
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
}