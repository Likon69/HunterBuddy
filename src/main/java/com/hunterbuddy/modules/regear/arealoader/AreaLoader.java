package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.arealoader.modes.Circle;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public class AreaLoader extends Module {
    public enum NetherPathMode {
        BARITONE_ELYTRA,
        OTHER
    }

    public enum OverworldFlightMode {
        ROCKETS,
        PITCH40,
        OTHER
    }

    private static final String HIDDEN_COORDINATES = "(hidden)";

    private final AreaModeSlot modeSlot = new AreaModeSlot();
    private final AreaModeSettings modeSettings = new AreaModeSettings(
        settings.getDefaultGroup(), this::replaceMode, this::switchMode);
    private final AreaRectangleSettings rectangleSettings = new AreaRectangleSettings(
        settings.getDefaultGroup(), modeSettings);
    private final AreaZigZagSettings zigZagSettings = new AreaZigZagSettings(
        settings.getDefaultGroup(), modeSettings);
    private final AreaCircleSettings circleSettings = new AreaCircleSettings(
        settings.getDefaultGroup(), modeSettings);
    private final AreaRunSettings runSettings = new AreaRunSettings(
        settings.getDefaultGroup(), modeSettings);
    private final AreaFlightSettings flightSettings = new AreaFlightSettings(settings, modeSettings);
    private final AreaNetherSettings netherSettings = new AreaNetherSettings(settings);
    private final AreaDebugSettings debugSettings = new AreaDebugSettings(settings);

    public final Setting<Boolean> hideCoordinates = modeSettings.hideCoordinates;
    public final Setting<AreaLoaderModes> chunkLoadMode = modeSettings.mode;
    public final Setting<BlockPos> startPos = rectangleSettings.startPos;
    public final Setting<BlockPos> targetPos = rectangleSettings.targetPos;
    public final Setting<Integer> rowGap = rectangleSettings.rowGap;
    public final Setting<Integer> zigzagLegLength = zigZagSettings.legLength;
    public final Setting<Integer> zigzagRowGap = zigZagSettings.rowGap;
    public final Setting<BlockPos> circleCenter = circleSettings.center;
    public final Setting<Double> circleLayerRadius = circleSettings.layerRadius;
    public final Setting<Double> circleSteps = circleSettings.steps;
    public final Setting<Circle.SpiralDirection> circleDirection = circleSettings.direction;
    public final Setting<String> saveLocation = runSettings.saveLocation;
    public final Setting<Boolean> pitch40DisableAutoBoundAdjust = flightSettings.pitch40DisableAutoBoundAdjust;
    public final Setting<Boolean> disconnectOnCompletion = runSettings.disconnectOnCompletion;
    public final Setting<Double> boatFlySpeed = runSettings.boatFlySpeed;
    public final Setting<OverworldFlightMode> overworldFlightMode = flightSettings.overworldFlightMode;
    public final Setting<Boolean> pitch40AutoFirework = flightSettings.pitch40AutoFirework;
    public final Setting<Integer> spiralCornerReachDistance = flightSettings.spiralCornerReachDistance;
    public final Setting<Integer> spiralMaxOffTrackDistance = flightSettings.spiralMaxOffTrackDistance;
    public final Setting<NetherPathMode> netherPathMode = netherSettings.netherPathMode;
    public final Setting<Integer> netherWaypointDistance = netherSettings.netherWaypointDistance;
    public final Setting<Integer> netherWaypointReachDistance = netherSettings.netherWaypointReachDistance;
    public final Setting<Boolean> debugMode = debugSettings.debugMode;
    public final Setting<Boolean> showGoalWaypoint = debugSettings.showGoalWaypoint;

    public AreaLoader() {
        super(
            HunterBuddyAddon.HUNT_CATEGORY,
            "arealoader",
            "Loads chunks in a rectangle toward a point or by spiralling endlessly from the player."
                + " Useful with stash finders or map-saving mods."
        );
    }

    public AreaLoaderMode currentMode() {
        return modeSlot.current();
    }

    public String coords(int x, int z) {
        if (hideCoordinates.get()) return HIDDEN_COORDINATES;
        return "(" + x + ", " + z + ")";
    }

    public String coords(double x, double z) {
        if (hideCoordinates.get()) return HIDDEN_COORDINATES;
        return String.format("(%.1f, %.1f)", x, z);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return AreaLoaderWidget.build(theme, this);
    }

    @Override
    public void onActivate() {
        currentMode().onActivate();
    }

    @Override
    public void onDeactivate() {
        currentMode().onDeactivate();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (isRunnable()) currentMode().onTick();
    }

    void clearSelectedMode() {
        boolean wasActive = disableIfActive();
        currentMode().clear();
        if (wasActive) info("Module disabled. Re-enable it to start fresh.");
    }

    void clearAllModes() {
        boolean wasActive = disableIfActive();
        currentMode().clearAll();
        if (wasActive) info("Module disabled. Re-enable it to start fresh with any mode.");
    }

    private boolean disableIfActive() {
        boolean wasActive = isActive();
        if (wasActive) toggle();
        return wasActive;
    }

    private boolean isRunnable() {
        return isActive()
            && mc.player != null
            && mc.world != null
            && mc.interactionManager != null
            && mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL;
    }

    private void replaceMode(AreaLoaderModes type) {
        modeSlot.replaceWith(type);
    }

    private void switchMode(AreaLoaderModes type) {
        boolean wasActive = isActive() && mc.player != null && mc.world != null;
        if (wasActive) currentMode().onDeactivate();
        modeSlot.replaceWith(type);
        if (wasActive) currentMode().onActivate();
    }
}
