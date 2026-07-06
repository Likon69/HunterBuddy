package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class BounceLab extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    private final Setting<Boolean> requireGliding = sgGeneral.add(new BoolSetting.Builder()
        .name("require-gliding")
        .description("Only record bounces while elytra gliding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logFloorBounces = sgGeneral.add(new BoolSetting.Builder()
        .name("log-floor")
        .description("Log floor collisions (vertical hit while moving down).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logCeilingBounces = sgGeneral.add(new BoolSetting.Builder()
        .name("log-ceiling")
        .description("Log ceiling collisions (vertical hit while moving up).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logWallBounces = sgGeneral.add(new BoolSetting.Builder()
        .name("log-wall")
        .description("Log wall collisions (horizontal hit).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatSummary = sgLogging.add(new BoolSetting.Builder()
        .name("chat-summary")
        .description("Print a short chat line for each bounce.")
        .defaultValue(false)
        .build()
    );

    private int bounceCount;
    private File csvFile;
    private int totalCorrections;
    private boolean wasOnGround;
    private boolean wasGliding;
    private Vec3d lastVelocity;
    private Vec3d lastPos;

    public BounceLab() {
        super(HunterBuddyAddon.LAB_CATEGORY, "bounce-lab", "Logs elytra bounce/collision events for timing and Y-momentum analysis.");
    }

    @Override
    public void onActivate() {
        bounceCount = 0;
        totalCorrections = 0;
        wasOnGround = mc.player != null && mc.player.isOnGround();
        wasGliding = mc.player != null && mc.player.isGliding();
        lastVelocity = mc.player != null ? mc.player.getVelocity() : Vec3d.ZERO;
        lastPos = mc.player != null ? mc.player.getEntityPos() : Vec3d.ZERO;
        csvFile = createCsvFile();
        writeHeader();
        if (csvFile != null) info("Logging BounceLab samples to %s", csvFile.getName());
    }

    @Override
    public void onDeactivate() {
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            totalCorrections++;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        boolean onGround = mc.player.isOnGround();
        boolean gliding = mc.player.isGliding();
        boolean verticalCollision = mc.player.verticalCollision;
        boolean horizontalCollision = mc.player.horizontalCollision;
        Vec3d velocity = mc.player.getVelocity();
        Vec3d pos = mc.player.getEntityPos();

        if (requireGliding.get() && !gliding) {
            updateState(onGround, gliding, velocity, pos);
            return;
        }

        boolean floorHit = verticalCollision && velocity.y < 0 && logFloorBounces.get();
        boolean ceilingHit = verticalCollision && velocity.y > 0 && logCeilingBounces.get();
        boolean wallHit = horizontalCollision && logWallBounces.get();

        if (floorHit || ceilingHit || wallHit) {
            StringBuilder type = new StringBuilder();
            if (floorHit) type.append("Floor");
            if (ceilingHit) type.append("Ceiling");
            if (wallHit) type.append(type.length() > 0 ? "+Wall" : "Wall");

            Bounce bounce = new Bounce(
                System.currentTimeMillis(),
                type.toString(),
                pos.x, pos.y, pos.z,
                lastPos.x, lastPos.y, lastPos.z,
                lastVelocity.x, lastVelocity.y, lastVelocity.z,
                velocity.x, velocity.y, velocity.z,
                horizontalSpeedBps(lastVelocity),
                horizontalSpeedBps(velocity),
                wasGliding, gliding,
                wasOnGround, onGround,
                totalCorrections
            );
            bounceCount++;
            writeBounce(bounce);

            if (chatSummary.get()) {
                info("Bounce[%s] pos=(%.1f,%.1f,%.1f) speed %.1f->%.1f glide %s->%s ground %s->%s corr=%d",
                    type, pos.x, pos.y, pos.z,
                    horizontalSpeedBps(lastVelocity), horizontalSpeedBps(velocity),
                    wasGliding, gliding, wasOnGround, onGround, totalCorrections);
            }
        }

        updateState(onGround, gliding, velocity, pos);
    }

    private void updateState(boolean onGround, boolean gliding, Vec3d velocity, Vec3d pos) {
        wasOnGround = onGround;
        wasGliding = gliding;
        lastVelocity = velocity;
        lastPos = pos;
    }

    private double horizontalSpeedBps(Vec3d vel) {
        return Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0;
    }

    private File createCsvFile() {
        try {
            File dir = new File(new File(MeteorClient.FOLDER, "hunterbuddy"), "bounce-lab");
            dir.mkdirs();
            return new File(dir, "bounce-" + System.currentTimeMillis() + ".csv");
        } catch (Exception e) {
            error("Failed to create BounceLab log file: %s", e.getMessage());
            return null;
        }
    }

    private void writeHeader() {
        if (csvFile == null) return;
        try (Writer writer = new FileWriter(csvFile, false)) {
            writer.write("timestamp,type,x,y,z,prev_x,prev_y,prev_z,vel_before_x,vel_before_y,vel_before_z,vel_after_x,vel_after_y,vel_after_z,speed_before_bps,speed_after_bps,gliding_before,gliding_after,onground_before,onground_after,total_corrections\n");
        } catch (IOException e) {
            error("Failed to write BounceLab header: %s", e.getMessage());
        }
    }

    private void writeBounce(Bounce bounce) {
        if (csvFile == null) return;
        try (Writer writer = new FileWriter(csvFile, true)) {
            writer.write(bounce.toCsv());
            writer.write('\n');
        } catch (IOException e) {
            error("Failed to write BounceLab bounce: %s", e.getMessage());
        }
    }

    @Override
    public String getInfoString() {
        return bounceCount + " bounces | " + totalCorrections + " corr";
    }

    private class Bounce {
        final long timestamp;
        final String type;
        final double x, y, z;
        final double prevX, prevY, prevZ;
        final double velBX, velBY, velBZ;
        final double velAX, velAY, velAZ;
        final double speedBeforeBps, speedAfterBps;
        final boolean glidingBefore, glidingAfter;
        final boolean onGroundBefore, onGroundAfter;
        final int totalCorrections;

        Bounce(long timestamp, String type,
               double x, double y, double z,
               double prevX, double prevY, double prevZ,
               double velBX, double velBY, double velBZ,
               double velAX, double velAY, double velAZ,
               double speedBeforeBps, double speedAfterBps,
               boolean glidingBefore, boolean glidingAfter,
               boolean onGroundBefore, boolean onGroundAfter,
               int totalCorrections) {
            this.timestamp = timestamp;
            this.type = type;
            this.x = x; this.y = y; this.z = z;
            this.prevX = prevX; this.prevY = prevY; this.prevZ = prevZ;
            this.velBX = velBX; this.velBY = velBY; this.velBZ = velBZ;
            this.velAX = velAX; this.velAY = velAY; this.velAZ = velAZ;
            this.speedBeforeBps = speedBeforeBps;
            this.speedAfterBps = speedAfterBps;
            this.glidingBefore = glidingBefore;
            this.glidingAfter = glidingAfter;
            this.onGroundBefore = onGroundBefore;
            this.onGroundAfter = onGroundAfter;
            this.totalCorrections = totalCorrections;
        }

        String toCsv() {
            return timestamp + ","
                + type + ","
                + fmt(x) + "," + fmt(y) + "," + fmt(z) + ","
                + fmt(prevX) + "," + fmt(prevY) + "," + fmt(prevZ) + ","
                + fmt(velBX) + "," + fmt(velBY) + "," + fmt(velBZ) + ","
                + fmt(velAX) + "," + fmt(velAY) + "," + fmt(velAZ) + ","
                + fmt(speedBeforeBps) + "," + fmt(speedAfterBps) + ","
                + (glidingBefore ? 1 : 0) + ","
                + (glidingAfter ? 1 : 0) + ","
                + (onGroundBefore ? 1 : 0) + ","
                + (onGroundAfter ? 1 : 0) + ","
                + totalCorrections;
        }

        String fmt(double value) {
            if (Double.isNaN(value)) return "";
            return String.format(java.util.Locale.ROOT, "%.3f", value);
        }
    }
}