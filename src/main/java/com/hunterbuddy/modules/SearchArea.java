package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Walks the player in a chunk-loading pattern, useful for stash hunting
 * with map-saving mods. Three modes:
 * <ul>
 *   <li><b>Rectangle</b> — lawn-mower between start and end positions
 *   <li><b>Spiral</b> — square spiral outward from the current position
 *   <li><b>PolarSpiral</b> — Archimedean spiral outward from a center
 *       (originally a separate {@code PolarSpiralEfly} module, combined
 *       here so all chunk-walking patterns live in one place)
 * </ul>
 *
 * <p>Ported from BepHaxAddon's {@code SearchArea} (Rectangle + Spiral)
 * and tillay-rh-plugins' {@code PolarSpiralEfly} (PolarSpiral yaw logic).
 * Dropped the JSON save/load and the disconnect-on-completion helpers.
 *
 * <p>No network calls, no telemetry, no data exfiltration — only local
 * game-state reads (player position, settings) and writes (player yaw,
 * forward-key, velocity).
 */
public class SearchArea extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPolar = settings.createGroup("Polar Spiral");

    public final Setting<SearchMode> mode = sgGeneral.add(new EnumSetting.Builder<SearchMode>()
        .name("mode")
        .description("The chunk-loading pattern.")
        .defaultValue(SearchMode.Rectangle)
        .onChanged(m -> {
            if (mc.player != null && isActive()) {
                currentMode = switch (m) {
                    case Rectangle -> new RectangleState();
                    case Spiral -> new SpiralState();
                    case PolarSpiral -> new PolarSpiralState();
                };
                goingToStart = true;
            }
        })
        .build()
    );

    public final Setting<BlockPos> startPos = sgGeneral.add(new BlockPosSetting.Builder()
        .name("start-position")
        .description("Start position. Y ignored. Used in Rectangle mode.")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == SearchMode.Rectangle)
        .build()
    );

    public final Setting<BlockPos> targetPos = sgGeneral.add(new BlockPosSetting.Builder()
        .name("end-position")
        .description("End position. Y ignored. Used in Rectangle mode.")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == SearchMode.Rectangle)
        .build()
    );

    public final Setting<Integer> rowGap = sgGeneral.add(new IntSetting.Builder()
        .name("path-gap")
        .description("Chunks between each path row.")
        .defaultValue(12)
        .min(1)
        .sliderRange(1, 32)
        .build()
    );

    public final Setting<Boolean> pauseOnMove = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-move")
        .description("Pause pathing when you manually move (WASD). Move again to resume.")
        .defaultValue(true)
        .build()
    );

    // ---- Polar Spiral settings ----

    public final Setting<Double> polarRadius = sgPolar.add(new DoubleSetting.Builder()
        .name("radius")
        .description("Distance between spiral layers, in chunks.")
        .defaultValue(1.0)
        .min(1.0)
        .max(34.0)
        .sliderRange(1.0, 34.0)
        .visible(() -> mode.get() == SearchMode.PolarSpiral)
        .build()
    );

    public final Setting<Integer> polarSteps = sgPolar.add(new IntSetting.Builder()
        .name("steps")
        .description("How many small turns per full orbit.")
        .defaultValue(64)
        .min(6)
        .max(200)
        .sliderRange(6, 100)
        .visible(() -> mode.get() == SearchMode.PolarSpiral)
        .build()
    );

    public final Setting<PolarDirection> polarDirection = sgPolar.add(new EnumSetting.Builder<PolarDirection>()
        .name("direction")
        .description("Spiral direction.")
        .defaultValue(PolarDirection.Clockwise)
        .visible(() -> mode.get() == SearchMode.PolarSpiral)
        .build()
    );

    public final Setting<Double> polarCenterX = sgPolar.add(new DoubleSetting.Builder()
        .name("center-x")
        .description("Spiral center X. Captured automatically from current position on activation; use the Reset Center button to recapture.")
        .defaultValue(0.0)
        .min(-30000000.0)
        .max(30000000.0)
        .sliderRange(-100000.0, 100000.0)
        .visible(() -> mode.get() == SearchMode.PolarSpiral)
        .build()
    );

    public final Setting<Double> polarCenterZ = sgPolar.add(new DoubleSetting.Builder()
        .name("center-z")
        .description("Spiral center Z. Captured automatically from current position on activation; use the Reset Center button to recapture.")
        .defaultValue(0.0)
        .min(-30000000.0)
        .max(30000000.0)
        .sliderRange(-100000.0, 100000.0)
        .visible(() -> mode.get() == SearchMode.PolarSpiral)
        .build()
    );

    public final Setting<Boolean> polarCaptureOnActivate = sgPolar.add(new BoolSetting.Builder()
        .name("capture-center-on-activate")
        .description("Capture the player's current position as the spiral center when the module is enabled. Disable this to use a manually-set polar center.")
        .defaultValue(true)
        .visible(() -> mode.get() == SearchMode.PolarSpiral)
        .build()
    );

    public enum SearchMode {
        Rectangle,
        Spiral,
        PolarSpiral
    }

    public enum PolarDirection {
        Clockwise,
        CounterClockwise
    }

    private interface IState {
        void onActivate();
        void onTick();
    }

    private class RectangleState implements IState {
        BlockPos initialPos;
        BlockPos currPos;
        BlockPos endPos;
        float yawDirection = 90.0f;
        boolean mainPath = true;
        int lastCompleteRowZ = 0;

        @Override
        public void onActivate() {
            initialPos = startPos.get();
            endPos = targetPos.get();
            currPos = initialPos;
            yawDirection = 90.0f;
            mainPath = true;
            lastCompleteRowZ = (int) mc.player.getZ();
        }

        @Override
        public void onTick() {
            if (goingToStart) {
                if (playerAt(currPos)) {
                    goingToStart = false;
                    mc.player.setVelocity(0, 0, 0);
                } else {
                    faceTowards(currPos.toCenterPos());
                    mc.options.forwardKey.setPressed(true);
                }
                return;
            }
            mc.options.forwardKey.setPressed(true);
            mc.player.setYaw(yawDirection);

            // Reached target end?
            if (mc.player.getBlockPos().getSquaredDistance(endPos.getX(), mc.player.getY(), endPos.getZ()) < 400) {
                mc.options.forwardKey.setPressed(false);
                info("Search area complete. Module disabled.");
                toggle();
                return;
            }

            int blockGap = 16 * rowGap.get();
            if (mainPath) {
                boolean hitEdge = (yawDirection == 90.0f && mc.player.getX() >= Math.max(initialPos.getX(), endPos.getX()))
                    || (yawDirection == -90.0f && mc.player.getX() <= Math.min(initialPos.getX(), endPos.getX()));
                if (hitEdge) {
                    yawDirection = (mc.player.getZ() < endPos.getZ()) ? 0.0f : 180.0f;
                    mainPath = false;
                    mc.player.setVelocity(0, 0, 0);
                }
            } else {
                if (Math.abs(mc.player.getZ() - lastCompleteRowZ) >= blockGap) {
                    lastCompleteRowZ = (int) mc.player.getZ();
                    yawDirection = (initialPos.getX() > endPos.getX()) ? -90.0f : 90.0f;
                    mainPath = true;
                    mc.player.setVelocity(0, 0, 0);
                }
            }
        }
    }

    private class SpiralState implements IState {
        BlockPos initialPos;
        BlockPos currPos;
        float yawDirection = -90.0f;
        boolean mainPath = true;
        int spiralWidth = 0;
        int spiralHeight = 0;

        @Override
        public void onActivate() {
            initialPos = mc.player.getBlockPos();
            currPos = initialPos;
            yawDirection = -90.0f;
            mainPath = true;
            spiralWidth = 0;
            spiralHeight = 0;
        }

        @Override
        public void onTick() {
            if (goingToStart) {
                if (playerAt(currPos)) {
                    goingToStart = false;
                    mc.player.setVelocity(0, 0, 0);
                } else {
                    faceTowards(currPos.toCenterPos());
                    mc.options.forwardKey.setPressed(true);
                }
                return;
            }
            mc.options.forwardKey.setPressed(true);
            mc.player.setYaw(yawDirection);

            int blockGap = 16 * rowGap.get();
            if (mainPath && Math.abs(mc.player.getX() - initialPos.getX()) >= blockGap + spiralWidth) {
                yawDirection += 90.0f;
                initialPos = new BlockPos((int) mc.player.getX(), initialPos.getY(), initialPos.getZ());
                spiralWidth += blockGap;
                mainPath = false;
                mc.player.setVelocity(0, 0, 0);
            } else if (!mainPath && Math.abs(mc.player.getZ() - initialPos.getZ()) >= blockGap + spiralHeight) {
                yawDirection += 90.0f;
                initialPos = new BlockPos(initialPos.getX(), initialPos.getY(), (int) mc.player.getZ());
                spiralHeight += blockGap;
                mainPath = true;
                mc.player.setVelocity(0, 0, 0);
            }
        }
    }

    private class PolarSpiralState implements IState {
        private double numRadiansCounter = 0;
        private double goalX;
        private double goalZ;
        private int flipX = -1;

        @Override
        public void onActivate() {
            // Set flipX BEFORE recomputing the initial goal so the spiral
            // direction matches the user's setting on the first orbit.
            flipX = (polarDirection.get() == PolarDirection.Clockwise) ? -1 : 1;
            // Optionally capture current player position as the spiral center.
            // If polarCaptureOnActivate is false, keep the user-set center.
            if (polarCaptureOnActivate.get() && mc.player != null) {
                polarCenterX.set(mc.player.getX());
                polarCenterZ.set(mc.player.getZ());
            }
            recomputeInitialGoal();
        }

        @Override
        public void onTick() {
            flipX = (polarDirection.get() == PolarDirection.Clockwise) ? -1 : 1;

            double scale = polarRadius.get() * 16;
            double radiansPerStep = (2 * Math.PI) / polarSteps.get();
            double cx = polarCenterX.get();
            double cz = polarCenterZ.get();

            double playerX = mc.player.getX();
            double playerZ = mc.player.getZ();
            double dx = playerX - goalX;
            double dz = playerZ - goalZ;
            if (dx * dx + dz * dz < 1) {
                numRadiansCounter += radiansPerStep;
                goalX = scale * numRadiansCounter * Math.cos(numRadiansCounter) * flipX + cx;
                goalZ = scale * numRadiansCounter * Math.sin(numRadiansCounter) + cz;
            }

            // Face the next spiral point and walk forward.
            faceTowards(new Vec3d(goalX, mc.player.getY(), goalZ));
            mc.options.forwardKey.setPressed(true);
        }

        private void recomputeInitialGoal() {
            // Ensure flipX reflects the current direction setting (defensive —
            // onActivate also sets it, but recomputeInitialGoal may be called
            // from the "Reset center" button while the module is active).
            flipX = (polarDirection.get() == PolarDirection.Clockwise) ? -1 : 1;
            double scale = polarRadius.get() * 16;
            double radiansPerStep = (2 * Math.PI) / polarSteps.get();
            double cx = polarCenterX.get();
            double cz = polarCenterZ.get();

            double playerAngle = Math.atan2(mc.player.getZ() - cz, mc.player.getX() - cx);
            double nextAngle = Math.ceil(playerAngle / radiansPerStep) * radiansPerStep;

            double prevDiffSq = Double.MAX_VALUE;
            for (int i = 0; i < 1000; i++) {
                double currentAngle = nextAngle + i * (2 * Math.PI);
                double spiralX = cx + scale * currentAngle * flipX * Math.cos(currentAngle);
                double spiralZ = cz + scale * currentAngle * Math.sin(currentAngle);
                double ddx = mc.player.getX() - spiralX;
                double ddz = mc.player.getZ() - spiralZ;
                double diffSq = ddx * ddx + ddz * ddz;
                if (diffSq > prevDiffSq) break;
                goalX = spiralX;
                goalZ = spiralZ;
                prevDiffSq = diffSq;
            }
            numRadiansCounter = Math.atan2(goalZ - cz, goalX - cx);
        }
    }

    private IState currentMode = new RectangleState();
    private boolean goingToStart = true;

    public SearchArea() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "hb-search-area-HB",
            "Walks the player in a chunk-loading pattern (Rectangle / Spiral / PolarSpiral). Useful with stash finder / map mods.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton reset = list.add(theme.button("Reset state")).widget();
        reset.action = () -> {
            if (mc.player != null) {
                goingToStart = true;
                currentMode.onActivate();
            }
        };
        if (mode.get() == SearchMode.PolarSpiral) {
            WButton recapture = list.add(theme.button("Reset polar center to current position")).widget();
            recapture.action = () -> {
                if (mc.player != null) {
                    polarCenterX.set(mc.player.getX());
                    polarCenterZ.set(mc.player.getZ());
                    if (currentMode instanceof PolarSpiralState s) {
                        s.recomputeInitialGoal();
                    }
                }
            };
        }
        return list;
    }

    @Override
    public void onActivate() {
        currentMode = switch (mode.get()) {
            case Rectangle -> new RectangleState();
            case Spiral -> new SpiralState();
            case PolarSpiral -> new PolarSpiralState();
        };
        currentMode.onActivate();
        goingToStart = true;
    }

    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Pause on manual movement
        if (pauseOnMove.get() && !goingToStart
            && (mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.jumpKey.isPressed())) {
            mc.options.forwardKey.setPressed(false);
            return;
        }

        currentMode.onTick();
    }

    private boolean playerAt(BlockPos pos) {
        return Math.sqrt(mc.player.getBlockPos().getSquaredDistance(pos.getX(), mc.player.getY(), pos.getZ())) < 5;
    }

    private void faceTowards(Vec3d target) {
        // MC yaw 0 = +Z facing. Facing vector at yaw ψ = (-sin ψ, 0, cos ψ).
        // To face direction (dx, dz): sin ψ = -dx/|d|, cos ψ = dz/|d|
        // → ψ = atan2(-dx, dz).
        double dx = target.x - mc.player.getX();
        double dz = target.z - mc.player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYaw(yaw);
    }
}