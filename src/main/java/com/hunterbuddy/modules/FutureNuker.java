package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;

/**
 * FutureNuker — port of lambda's Nuker
 * (com.lambda.module.modules.world.Nuker), faithful to the original.
 *
 * <p>Like lambda, Nuker is INDEPENDENT of PacketMine. It uses its own
 * direct packet-mine sequence (START → STOP) per block, no shared break
 * pipeline.
 *
 * <p>isInFlatten and the FlattenMode enum come from
 * com.lambda.util.PlayerBuildLayerUtils. The build system (BuildTask →
 * BuildSimulator → BuildRequest) is unavailable in Meteor; the fill-floor
 * feature is stubbed.
 */
public class FutureNuker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("Vertical reach of the nuker region (1-8).")
        .defaultValue(6)
        .min(1).max(8).sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> width = sgGeneral.add(new IntSetting.Builder()
        .name("width")
        .description("Horizontal reach of the nuker region (1-8).")
        .defaultValue(6)
        .min(1).max(8).sliderRange(1, 8)
        .build()
    );

    private final Setting<FlattenMode> flattenMode = sgGeneral.add(new EnumSetting.Builder<FlattenMode>()
        .name("flatten-mode")
        .description("How the nuker treats vertical layers.")
        .defaultValue(FlattenMode.Standard)
        .build()
    );

    private final Setting<DigDirection> directionalDig = sgGeneral.add(new EnumSetting.Builder<DigDirection>()
        .name("directional-dig")
        .description("Only break blocks in the given direction relative to the player.")
        .defaultValue(DigDirection.None)
        .build()
    );

    private final Setting<Boolean> onGround = sgGeneral.add(new BoolSetting.Builder()
        .name("on-ground")
        .description("Only break blocks when the player is standing on ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> fillFloor = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-floor")
        .description("Also place magma blocks one layer below the broken blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> baritoneSelection = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone-selection")
        .description("Restricts nuker to your baritone selection.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> inverseSelection = sgGeneral.add(new BoolSetting.Builder()
        .name("inverse-selection")
        .description("Breaks blocks outside of the baritone selection and ignores blocks inside.")
        .defaultValue(false)
        .visible(baritoneSelection::get)
        .build()
    );

    private final Setting<Boolean> sneakLowersFlatten = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak-lowers-flatten")
        .description("When sneaking, the flatten floor drops one Y level.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> async = sgGeneral.add(new BoolSetting.Builder()
        .name("async")
        .description("Allows the simulation 50ms between ticks where nothing changes. This causes a 1 tick wait between starting the module, and it performing actions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fillFluids = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-fluids")
        .description("Also break blocks that are fluids (when fillable).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Face a block before breaking it. Servers that check where you are looking reject the break otherwise.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing the hand visibly. Off still sends the swing packet, it just does not animate.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-tool")
        .description("Switch to the fastest tool for the block before breaking it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxBlocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("max-blocks-per-tick")
        .description("How many blocks may be broken in one tick. Only ever applies to blocks that break instantly; anything slower takes one per tick by nature.")
        .defaultValue(1)
        .min(1).max(64).sliderRange(1, 16)
        .build()
    );

    /** Per-Nuker BreakConfig instance (gives access to fillFluids for target selection). */
    private final com.hunterbuddy.lambda.BreakConfig breakConfig = new com.hunterbuddy.lambda.BreakConfig();

    public FutureNuker() {
        super(HunterBuddyAddon.LAB_CATEGORY, "lambda-nuker",
            "Lambda Nuker (port) — breaks blocks around you, direct packet-mine (no PacketMine dep).");
    }

    @EventHandler
    private void onTick(Post event) {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;
        if (Boolean.TRUE.equals(this.onGround.get()) && !mc.player.isOnGround()) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int w = this.width.get();
        int h = this.height.get();

        // Iterate the region like lambda's tickingBlueprint.
        // Apply breakConfig.fillFluids to filter air targets — when fillFluids
        // is false (lambda default), air blocks are skipped entirely.
        // When fillFluids is true, air blocks are kept (the original
        // TargetState.Air in lambda's associateWith).
        List<BlockPos> targets = new ArrayList<>();
        Iterator<BlockPos> it = BlockPos.iterateOutwards(playerPos, w, h, w).iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (mc.world.getBlockState(pos).isAir() && !breakConfig.isFillFluids()) continue;
            if (Boolean.TRUE.equals(this.baritoneSelection.get())
                && this.isInBaritoneSelection(pos) == Boolean.TRUE.equals(this.inverseSelection.get())) continue;
            if (!this.isInFlatten(pos, this.flattenMode.get(), Boolean.TRUE.equals(this.sneakLowersFlatten.get()))) continue;
            if (!this.isWithinDigDirection(pos, this.directionalDig.get(), playerPos)) continue;

            // iterateOutwards hands back one mutable cursor over and over, so a position kept
            // past this line has to be a copy or every entry ends up being the last one.
            targets.add(pos.toImmutable());
        }

        // Nearest first: a block you are standing in matters more than one at the edge of the
        // region, and with a per-tick budget the order decides what actually gets broken.
        Vec3d eyes = mc.player.getEyePos();
        targets.sort((a, b) -> Double.compare(
            a.getSquaredDistance(eyes.x, eyes.y, eyes.z), b.getSquaredDistance(eyes.x, eyes.y, eyes.z)));

        int broken = 0;

        for (BlockPos pos : targets) {
            if (broken >= this.maxBlocksPerTick.get()) break;

            boolean insta = BlockUtils.canInstaBreak(pos);

            this.autoSwitch(pos);
            this.breakBlock(pos);
            broken++;

            // A block that does not break instantly needs the progress kept on it, and the
            // client can only be breaking one block at a time. Moving on would restart the
            // count somewhere else and neither would ever finish.
            if (!insta) break;
        }

        if (Boolean.TRUE.equals(this.fillFloor.get())) {
            this.fillFloor(playerPos, w);
        }
        // No autoDisable — lambda's Nuker stays active.
    }

    /**
     * Breaks one block the way Meteor's own Nuker does.
     *
     * <p>Instant blocks get the sequenced START/STOP pair; anything slower goes through
     * {@link BlockUtils#breakBlock} which keeps the progress across ticks. Both take the real
     * face from {@link BlockUtils#getDirection} — the previous version claimed UP for every
     * block, which is a face the server can see is wrong for anything you are not standing on.
     */
    private void breakBlock(BlockPos pos) {
        if (Boolean.TRUE.equals(this.rotate.get())) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), 50, () -> sendBreak(pos));
        } else {
            sendBreak(pos);
        }
    }

    private void sendBreak(BlockPos pos) {
        if (!BlockUtils.canInstaBreak(pos)) {
            BlockUtils.breakBlock(pos, this.swing.get());
            return;
        }

        Direction direction = BlockUtils.getDirection(pos);

        // Sequenced, not raw: the server hands out an id per action and answers with it, and a
        // client that never quotes one is trivially not a vanilla client.
        mc.interactionManager.sendSequencedPacket(mc.world, sequence -> new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence));

        if (this.swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        mc.interactionManager.sendSequencedPacket(mc.world, sequence -> new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence));
    }

    /** Picks the fastest tool for the block, so stone is not attacked with a fist. */
    private void autoSwitch(BlockPos pos) {
        if (!Boolean.TRUE.equals(this.autoSwitch.get())) return;

        FindItemResult slot = InvUtils.findFastestTool(mc.world.getBlockState(pos));
        if (!slot.found() || !slot.isHotbar()) return;
        if (mc.player.getInventory().getSelectedSlot() == slot.slot()) return;

        mc.player.getInventory().setSelectedSlot(slot.slot());
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot.slot()));
    }

    /**
     * Keeps the vanilla break cooldown out of the way.
     *
     * <p>The game imposes a pause after every break, which is what makes continuous mining stall
     * after the first block. Meteor's Nuker zeroes it the same way.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        event.cooldown = 0;
    }

    private void fillFloor(BlockPos playerPos, int w) {
        // Stub: lambda uses its build system (BuildTask → BuildSimulator)
        // to associate each empty floor position with TargetState.Solid(MAGMA)
        // and place them. The build system doesn't exist in Meteor, so
        // this is a manual stub: we place magma at each air position in the
        // floor layer when the player is holding magma and is on the ground.
        if (mc.interactionManager == null || mc.player == null) return;
        if (!mc.player.getMainHandStack().isOf(Items.MAGMA_BLOCK)) return;
        BlockPos.iterateOutwards(playerPos.down(), w, 0, w).forEach(floorPos -> {
            if (mc.world.getBlockState(floorPos).isAir()) {
                BlockPos above = floorPos.up();
                if (mc.world.getBlockState(above).isReplaceable()) {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(above), Direction.DOWN, above, false));
                }
            }
        });
    }

    // ============ Lambda PlayerBuildLayerUtils.isInFlatten port ============

    private boolean isInFlatten(BlockPos pos, FlattenMode mode, boolean sneakLowers) {
        if (mode == FlattenMode.None) return true;

        if (mode == FlattenMode.Staircase) {
            BlockPos up = pos.up();
            if ((!mc.world.getBlockState(up).isAir()
                    && (Boolean.FALSE.equals(this.baritoneSelection.get())
                        || this.isInBaritoneSelection(up) == Boolean.FALSE.equals(this.inverseSelection.get())))
                || (!mc.world.getBlockState(up.east()).isAir()
                    && (Boolean.FALSE.equals(this.baritoneSelection.get())
                        || this.isInBaritoneSelection(up.east()) == Boolean.FALSE.equals(this.inverseSelection.get())))
                || (!mc.world.getBlockState(up.south()).isAir()
                    && (Boolean.FALSE.equals(this.baritoneSelection.get())
                        || this.isInBaritoneSelection(up.south()) == Boolean.FALSE.equals(this.inverseSelection.get())))
                || (!mc.world.getBlockState(up.west()).isAir()
                    && (Boolean.FALSE.equals(this.baritoneSelection.get())
                        || this.isInBaritoneSelection(up.west()) == Boolean.FALSE.equals(this.inverseSelection.get())))
                || (!mc.world.getBlockState(up.north()).isAir()
                    && (Boolean.FALSE.equals(this.baritoneSelection.get())
                        || this.isInBaritoneSelection(up.north()) == Boolean.FALSE.equals(this.inverseSelection.get())))) {
                return false;
            }
        }

        int flattenY = (int) Math.ceil(mc.player.getY());
        BlockPos playerPos = mc.player.getBlockPos();
        int flattenLevel = (sneakLowers && mc.player.isSneaking()) ? flattenY - 1 : flattenY;

        boolean isSmart = (mode == FlattenMode.Smart || mode == FlattenMode.ReverseSmart);
        if (!isSmart && pos.getY() < flattenLevel) return false;
        if (pos.equals(playerPos.down())) return false;
        if (pos.getY() >= flattenLevel) return true;

        Direction playerLookDir = mc.player.getHorizontalFacing();
        Direction smartFlattenDir = (mode == FlattenMode.Smart) ? playerLookDir : playerLookDir.getOpposite();

        int dx = pos.getX() - playerPos.getX();
        int dz = pos.getZ() - playerPos.getZ();

        return (dx < 0 && smartFlattenDir == Direction.EAST)
            || (dz < 0 && smartFlattenDir == Direction.SOUTH)
            || (dx > 0 && smartFlattenDir == Direction.WEST)
            || (dz > 0 && smartFlattenDir == Direction.NORTH);
    }

    private boolean isInBaritoneSelection(BlockPos pos) {
        // TODO: Baritone API not ported. Returns false → baritoneSelection
        // filter effectively becomes a no-op.
        return false;
    }

    private boolean isWithinDigDirection(BlockPos pos, DigDirection dir, BlockPos playerPos) {
        return switch (dir) {
            case None -> true;
            case East -> playerPos.getX() <= pos.getX();
            case West -> playerPos.getX() >= pos.getX();
            case North -> playerPos.getZ() >= pos.getZ();
            case South -> playerPos.getZ() <= pos.getZ();
        };
    }

    // ============ Enums (lambda) ============

    public enum FlattenMode {
        None,
        Standard,
        Smart,
        ReverseSmart,
        Staircase;

        public boolean isSmart() { return this == Smart || this == ReverseSmart; }
    }

    public enum DigDirection { None, East, South, West, North }
}
