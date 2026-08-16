package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

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

    private final Setting<com.hunterbuddy.lambda.BreakConfig.BreakMode> breakMode = sgGeneral.add(
        new EnumSetting.Builder<com.hunterbuddy.lambda.BreakConfig.BreakMode>()
            .name("break-mode")
            .description("Which packet sequence to open a break with. Grim is the current bypass; OldGrim adds the offset burst that spends the server's delay.")
            .defaultValue(com.hunterbuddy.lambda.BreakConfig.BreakMode.Grim)
            .build()
    );

    /** Per-Nuker BreakConfig instance (gives access to fillFluids for target selection). */
    private final com.hunterbuddy.lambda.BreakConfig breakConfig = new com.hunterbuddy.lambda.BreakConfig();

    private final com.hunterbuddy.lambda.BreakContextFactory.Configs automated =
        new com.hunterbuddy.lambda.BreakContextFactory.Configs(breakConfig);

    private final com.hunterbuddy.lambda.BreakEngine engine = new com.hunterbuddy.lambda.BreakEngine();

    public FutureNuker() {
        super(HunterBuddyAddon.LAB_CATEGORY, "lambda-nuker",
            "Lambda Nuker (port) — breaks blocks around you, direct packet-mine (no PacketMine dep).");
    }

    /**
     * Pre and not Post: the engine queues a rotation whose callback carries the break packets, and
     * Meteor runs that callback partway through the tick. Started at the end of one, it lands in
     * the next.
     */
    @EventHandler
    private void onTick(Pre event) {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;

        // Read every tick rather than only on enable: the settings can be turned while the module
        // is running, and the engine only ever looks at the config object.
        syncConfig();

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

        // The engine owns the sequence; the module only decides which block is worth offering.
        // It refuses anything while a break is running or the inter-block delay is still going,
        // so handing it the nearest candidate every tick is enough.
        engine.tick();

        for (BlockPos pos : targets) {
            if (engine.request(com.hunterbuddy.lambda.BreakContextFactory.create(pos, automated))) break;
        }

        if (Boolean.TRUE.equals(this.fillFloor.get())) {
            this.fillFloor(playerPos, w);
        }
        // No autoDisable — lambda's Nuker stays active.
    }

    @Override
    public void onActivate() {
        engine.clear();
        syncConfig();
    }

    @Override
    public void onDeactivate() {
        engine.clear();
    }

    /** Pushes the module's own settings into the config the engine reads. */
    private void syncConfig() {
        breakConfig.setRotate(this.rotate.get());
        breakConfig.setSwing(this.swing.get()
            ? com.hunterbuddy.lambda.BreakConfig.SwingMode.StartAndEnd
            : com.hunterbuddy.lambda.BreakConfig.SwingMode.None);
        breakConfig.setSwapMode(this.autoSwitch.get()
            ? com.hunterbuddy.lambda.BreakConfig.SwapMode.Start
            : com.hunterbuddy.lambda.BreakConfig.SwapMode.None);
        breakConfig.setBreakMode(this.breakMode.get());
    }

    @EventHandler
    private void onBlockUpdate(meteordevelopment.meteorclient.events.packets.PacketEvent.Receive event) {
        if (event.packet instanceof net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket packet
            && packet.getState().isAir()) {
            engine.onBlockRemoved(packet.getPos());
        }
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
