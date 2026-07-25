package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
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

    /** Block positions that have been submitted to the server for breaking
     *  (via START_DESTROY_BLOCK). Cleared when the server confirms break
     *  via BlockUpdateS2CPacket(air). Prevents re-submitting the same
     *  block every tick. */
    private final Set<BlockPos> pending = new HashSet<>();

    public FutureNuker() {
        super(HunterBuddyAddon.FUTURE_CATEGORY, "lambda-nuker",
            "Lambda Nuker (port) — breaks blocks around you, direct packet-mine (no PacketMine dep).");
    }

    @Override
    public void onActivate() {
        pending.clear();
    }

    @Override
    public void onDeactivate() {
        pending.clear();
    }

    @EventHandler
    private void onTick(Post event) {
        if (mc.world == null || mc.player == null) return;
        if (Boolean.TRUE.equals(this.onGround.get()) && !mc.player.isOnGround()) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int w = this.width.get();
        int h = this.height.get();

        // Iterate the region like lambda's tickingBlueprint.
        Set<BlockPos> toBreak = new HashSet<>();
        Iterator<BlockPos> it = BlockPos.iterateOutwards(playerPos, w, h, w).iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (mc.world.getBlockState(pos).isAir()) continue;
            if (Boolean.TRUE.equals(this.baritoneSelection.get())
                && this.isInBaritoneSelection(pos) == Boolean.TRUE.equals(this.inverseSelection.get())) continue;
            if (!this.isInFlatten(pos, this.flattenMode.get(), Boolean.TRUE.equals(this.sneakLowersFlatten.get()))) continue;
            if (!this.isWithinDigDirection(pos, this.directionalDig.get(), playerPos)) continue;
            if (this.pending.contains(pos)) continue;
            this.pending.add(pos);
            toBreak.add(pos);
        }

        // Direct packet-mine sequence: START → STOP per block (lambda's
        // BreakInfo.startBreakPacket + stopBreakPacket).
        if (!toBreak.isEmpty()) {
            for (BlockPos pos : toBreak) {
                this.startBreaking(pos);
            }
        }

        if (Boolean.TRUE.equals(this.fillFloor.get())) {
            this.fillFloor(playerPos, w);
        }
        // No autoDisable — lambda's Nuker stays active.
    }

    @EventHandler
    private void onPacketInbound(PacketEvent.Receive event) {
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            if (packet.getState().isAir()) {
                // Server confirmed break — clear from pending so we don't
                // re-submit it next tick.
                this.pending.remove(packet.getPos());
            }
        }
    }

    private void startBreaking(BlockPos pos) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
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
