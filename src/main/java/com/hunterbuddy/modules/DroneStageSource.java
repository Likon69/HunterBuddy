package com.hunterbuddy.modules;

import com.hunterbuddy.render.DroneClimbDetector;
import com.hunterbuddy.render.DroneScene;
import com.hunterbuddy.render.DroneSceneReader;
import com.hunterbuddy.render.DroneStage;
import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class DroneStageSource {
    private static final double TARGET_HOLD_SECONDS = 0.6;
    private static final double COMBAT_TAIL_SECONDS = 3.0;
    private static final double CORNER_HEIGHT = 3.3;
    private static final String COMBAT_REASON = "combat";

    private double targetSeconds;
    private double tailSeconds;
    private final DroneClimbDetector climbDetector = new DroneClimbDetector();

    public void reset() {
        climbDetector.reset();
        targetSeconds = 0.0;
        tailSeconds = 0.0;
    }

    public boolean running() {
        SpawnerDetector detector = Modules.get().get(SpawnerDetector.class);
        return detector != null && detector.isBusyFarming();
    }

    public DroneStage read(World world, LivingEntity player, Vec3d focus, double dt, float tickProgress, boolean combatEnabled) {
        SpawnerDetector detector = Modules.get().get(SpawnerDetector.class);
        KillAuraPlus aura = Modules.get().get(KillAuraPlus.class);
        boolean busy = detector != null && detector.isBusyFarming();
        DroneScene scene = busy ? DroneSceneReader.read(detector.getAutoStateName()) : DroneScene.CRUISE;

        targetSeconds = aura != null && aura.hasActiveTarget() ? targetSeconds + dt : 0.0;
        boolean pausedForCombat = busy && detector.isAutoPaused() && COMBAT_REASON.equals(detector.getAutoPauseReason());
        boolean raw = combatEnabled && (targetSeconds >= TARGET_HOLD_SECONDS || pausedForCombat);
        tailSeconds = raw ? COMBAT_TAIL_SECONDS : Math.max(0.0, tailSeconds - dt);
        boolean combat = tailSeconds > 0.0;

        Entity target = combat && aura != null ? aura.target() : null;
        Vec3d interest = target == null ? null : target.getLerpedPos(tickProgress).add(0.0, target.getHeight() * 0.5, 0.0);

        boolean climbing = climbDetector.update(world, player, focus, scene, dt);

        return new DroneStage(scene, corners(detector, scene), interest, combat, climbing);
    }

    private static List<Vec3d> corners(SpawnerDetector detector, DroneScene scene) {
        if (detector == null || scene != DroneScene.ROOM) return List.of();

        SpawnerDetector.Site site = detector.getAutoSite();
        SpawnerDetector.Room room = site == null ? null : site.room;
        if (room == null) return List.of();

        double y = room.cy() + CORNER_HEIGHT;
        double west = room.cx() - room.k() + 0.5;
        double east = room.cx() + room.k() + 0.5;
        double north = room.cz() - room.p() + 0.5;
        double south = room.cz() + room.p() + 0.5;
        return List.of(new Vec3d(west, y, north), new Vec3d(east, y, north), new Vec3d(east, y, south), new Vec3d(west, y, south));
    }
}
