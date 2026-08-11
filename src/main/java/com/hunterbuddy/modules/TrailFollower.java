package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import com.hunterbuddy.HunterBuddyAddon;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;

import java.time.Duration;
import java.util.ArrayDeque;

import static com.hunterbuddy.modules.regear.util.Utils.angleDifference;
import static com.hunterbuddy.modules.regear.util.Utils.positionInDirection;
import static com.hunterbuddy.modules.regear.util.Utils.sendWebhook;
import static com.hunterbuddy.modules.regear.util.Utils.smoothRotation;
import static com.hunterbuddy.modules.regear.util.Utils.yawToDirection;

public class TrailFollower extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> maxTrailLength = sgGeneral.add(new IntSetting.Builder()
        .name("max-trail-length")
        .description("The number of trail points to keep for the average. Adjust to change how quickly the average will change. More does not necessarily equal better because if the list is too long it will contain chunks behind you.")
        .defaultValue(20)
        .sliderRange(1, 100)
        .build()
    );

    public final Setting<Integer> chunksBeforeStarting = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-before-starting")
        .description("Useful for afking looking for a trail. The amount of chunks before it gets detected as a trail.")
        .defaultValue(10)
        .sliderRange(1, 50)
        .build()
    );

    public final Setting<Integer> chunkConsiderationWindow = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-timeframe")
        .description("The amount of time in seconds that the chunks must be found in before starting.")
        .defaultValue(5)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<TrailEndBehavior> trailEndBehavior = sgGeneral.add(new EnumSetting.Builder<TrailEndBehavior>()
        .name("trail-end-behavior")
        .description("What to do when the trail ends.")
        .defaultValue(TrailEndBehavior.DISABLE)
        .build()
    );

    public final Setting<Double> trailEndYaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("trail-end-yaw")
        .description("The direction to go after the trail is abandoned.")
        .defaultValue(0.0)
        .sliderRange(0.0, 359.9)
        .visible(() -> trailEndBehavior.get() == TrailEndBehavior.FLY_TOWARDS_YAW)
        .build()
    );

    // changed to an enum dropdown for fly selection
    public enum OverworldFlightMode {
        VANILLA,
        PITCH40,
        OTHER
    }

    public enum NetherPathMode {
        AVERAGE,
        OTHER
    }

    public final Setting<OverworldFlightMode> overworldFlightMode = sgGeneral.add(new EnumSetting.Builder<OverworldFlightMode>()
        .name("overworld-flight-mode")
        .description("Choose how TrailFollower flies in Overworld. PITCH40 auto-toggles our Pitch40 module. OTHER just sets yaw (no auto-flight).")
        .defaultValue(OverworldFlightMode.PITCH40)
        .build()
    );

    public final Setting<NetherPathMode> netherPathMode = sgGeneral.add(new EnumSetting.Builder<NetherPathMode>()
        .name("nether-path-mode")
        .description("Choose how TrailFollower does baritone pathing in Nether. If OTHER is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail.")
        .defaultValue(NetherPathMode.AVERAGE)
        .build()
    );

    public final Setting<Boolean> pitch40Firework = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Enables auto-firework on our Pitch40 module (used when overworld-flight-mode is PITCH40).")
        .defaultValue(true)
        .visible(() -> overworldFlightMode.get() == OverworldFlightMode.PITCH40)
        .build()
    );

    public final Setting<Double> rotateScaling = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotate-scaling")
        .description("Scaling of how fast the yaw changes. 1 = instant, 0 = doesn't change")
        .defaultValue(0.1)
        .sliderRange(0.0, 1.0)
        .build()
    );

    public final Setting<Boolean> oppositeDimension = sgGeneral.add(new BoolSetting.Builder()
        .name("opposite-dimension")
        .description("Follows trails from the opposite dimension (Requires that you've already loaded the other dimension with XP).")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> autoElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-start-baritone-elytra")
        .description("Starts baritone elytra for you.")
        .defaultValue(false)
        .build()
    );

    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);

    public final Setting<Double> pathDistance = sgAdvanced.add(new DoubleSetting.Builder()
        .name("path-distance")
        .description("The distance to add trail positions in the direction the player is facing. (Ignored when following overworld from nether)")
        .defaultValue(500)
        .sliderRange(100, 2000)
        .onChanged(value -> pathDistanceActual = value)
        .build()
    );

    public final Setting<FollowMode> flightMethod = sgAdvanced.add(new EnumSetting.Builder<FollowMode>()
        .name("flight-method")
        .description("Decided how the goals will be used. Leave this on AUTO unless you want to use yaw lock in the nether for example.")
        .defaultValue(FollowMode.AUTO)
        .build()
    );

    public final Setting<Double> startDirectionWeighting = sgAdvanced.add(new DoubleSetting.Builder()
        .name("start-direction-weight")
        .description("Initial bias toward the direction you're facing when enabling. Decays as trail becomes established. 0 = no bias, 1 = strong bias.")
        .defaultValue(0.7)
        .min(0)
        .sliderMax(1)
        .build()
    );

    public final Setting<Double> forwardConeAngle = sgAdvanced.add(new DoubleSetting.Builder()
        .name("forward-cone-angle")
        .description("During initial detection, only consider chunks within this angle of your facing direction. 90 = hemisphere ahead, 180 = all around.")
        .defaultValue(120.0)
        .min(45.0)
        .sliderMax(180.0)
        .build()
    );

    public final Setting<Double> forwardWeightStrength = sgAdvanced.add(new DoubleSetting.Builder()
        .name("forward-weight-strength")
        .description("How much to favor chunks aligned with current direction in the average. 0 = equal weight, 1 = strong forward preference.")
        .defaultValue(0.6)
        .min(0.0)
        .sliderMax(1.0)
        .build()
    );

    public final Setting<DirectionWeighting> directionWeighting = sgAdvanced.add(new EnumSetting.Builder<DirectionWeighting>()
        .name("direction-weighting")
        .description("How the chunks found should be weighted. Useful for path splits. Left will weight chunks to the left of the player higher, right will weigh chunks to the right higher, and none will be in the middle/random. ")
        .defaultValue(DirectionWeighting.NONE)
        .build()
    );

    public final Setting<Integer> directionWeightingMultiplier = sgAdvanced.add(new IntSetting.Builder()
        .name("direction-weighting-multiplier")
        .description("The multiplier for how much weight should be given to chunks in the direction specified. Values are capped to be in the range [2, maxTrailLength].")
        .defaultValue(2)
        .min(2)
        .sliderMax(10)
        .visible(() -> directionWeighting.get() != DirectionWeighting.NONE)
        .build()
    );

    public final Setting<Boolean> only112 = sgAdvanced.add(new BoolSetting.Builder()
        .name("follow-only-1.12")
        .description("Will only follow 1.12 chunks and will ignore other ones.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> minAimDistance = sgAdvanced.add(new DoubleSetting.Builder()
        .name("min-aim-distance")
        .description("Chunks closer than this never steer the yaw. A trail point is consumed (removed from the aim average) once the player gets within this distance of it.")
        .defaultValue(100.0)
        .min(32.0)
        .sliderRange(32.0, 256.0)
        .build()
    );

    public final Setting<Double> circleReturnRadius = sgAdvanced.add(new DoubleSetting.Builder()
        .name("circle-return-radius")
        .description("When lost, fly back to the last found chunk and start the search spiral once within this distance of it. Also the spiral's starting radius.")
        .defaultValue(64.0)
        .min(16.0)
        .sliderRange(16.0, 192.0)
        .build()
    );

    public final Setting<Double> circleRadiusGrowth = sgAdvanced.add(new DoubleSetting.Builder()
        .name("circle-radius-growth")
        .description("How fast the search spiral widens, in blocks per second, while circling for the next chunk.")
        .defaultValue(12.0)
        .min(2.0)
        .sliderRange(2.0, 64.0)
        .build()
    );

    public final Setting<Double> chunkFoundTimeout = sgAdvanced.add(new DoubleSetting.Builder()
        .name("chunk-found-timeout")
        .description("The amount of MS with nothing left to aim at before flying back to the last chunk and circling there.")
        .defaultValue(1000 * 5)
        .min(1000)
        .sliderMax(1000 * 10)
        .build()
    );

    public final Setting<Double> circlingDegPerTick = sgAdvanced.add(new DoubleSetting.Builder()
        .name("Circling-degrees-per-tick")
        .description("The amount of degrees to change per tick while circling.")
        .defaultValue(2.0)
        .min(1.0)
        .sliderMax(20.0)
        .build()
    );

    public final Setting<Double> trailTimeout = sgAdvanced.add(new DoubleSetting.Builder()
        .name("trail-timeout")
        .description("The amount of MS without a chunk found to stop following the trail.")
        .defaultValue(1000 * 30)
        .min(1000 * 10)
        .sliderMax(1000 * 60)
        .build()
    );

    public final Setting<Double> maxTrailDeviation = sgAdvanced.add(new DoubleSetting.Builder()
        .name("max-trail-deviation")
        .description("Maximum allowed angle (in degrees) from the original trail direction. Helps avoid switching to intersecting trails.")
        .defaultValue(180.0)
        .min(1.0)
        .sliderMax(270.0)
        .build()
    );

    public final Setting<Integer> chunkCacheLength = sgAdvanced.add(new IntSetting.Builder()
        .name("chunk-cache-length")
        .description("The amount of chunks to keep in the cache. (Won't be applied until deactivating)")
        .defaultValue(100_000)
        .sliderRange(0, 10_000_000)
        .build()
    );

    public final Setting<String> webhookLink = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-link")
        .description("Will send all updates to the webhook link. Leave blank to disable.")
        .defaultValue("")
        .build()
    );

    public final Setting<Integer> baritoneUpdateTicks = sgAdvanced.add(new IntSetting.Builder()
        .name("baritone-path-update-ticks")
        .description("The amount of ticks between updates to the baritone goal. Low values may cause high instability.")
        .defaultValue(5 * 20) // 5 seconds
        .sliderRange(20, 30 * 20)
        .build()
    );

    public final Setting<Boolean> debug = sgAdvanced.add(new BoolSetting.Builder()
        .name("debug")
        .description("Debug mode.")
        .defaultValue(false)
        .build()
    );

    private boolean oldAutoFireworkValue;
    private boolean pitch40FireworkModified;

    private FollowMode followMode;

    private boolean followingTrail = false;

    private ArrayDeque<Vec3d> trail = new ArrayDeque<>();
    private ArrayDeque<Vec3d> possibleTrail = new ArrayDeque<>();

    private long lastFoundTrailTime;
    private long lastFoundPossibleTrailTime;

    /** The heading you were facing when the module came on, and whether it has been taken yet. */
    private double initialYaw = 0.0;
    private boolean hasInitialDirection = false;

    /** How far the trail is established, which is what decays the initial bias away. */
    private int chunksFoundSinceStart = 0;

    /**
     * What the module is currently doing about the trail.
     *
     * <p>FOLLOW is flying it. RETURN and CIRCLE are the answer to running out of anything to aim
     * at: go back to the last chunk we actually found and wait there, rather than circling at
     * whatever spot we happened to drift to.
     */
    private SearchState state = SearchState.FOLLOW;

    /** World position of the last valid chunk accepted, near or far. The place worth waiting at. */
    private Vec3d lastChunkPos;

    /** When the aim deque last gained a point, and when it last went empty. */
    private long lastTrailAddAt;
    private long trailEmptySince;

    /** When circling started, so the abandon clock counts only time spent circling. */
    private long circleEnteredAt;

    /** Where round the spiral we are, and how long we have been on it. */
    private double circleAngle;
    private int ticksInCircle;

    private double pathDistanceActual = pathDistance.get();

    private Cache<Long, Byte> seenChunksCache = Caffeine.newBuilder()
        .maximumSize(chunkCacheLength.get())
        .expireAfterWrite(Duration.ofMinutes(5))
        .build();

    public TrailFollower()
    {
        super(HunterBuddyAddon.HUNT_CATEGORY, "TrailFollower", "Automatically follows trails in all dimensions. Forward-weighted average with a decaying start-direction bias and a detection cone.");
    }

    void resetTrail()
    {
        baritoneSetGoalTicks = 0;
        followingTrail = false;
        trail = new ArrayDeque<>();
        possibleTrail = new ArrayDeque<>();
        hasInitialDirection = false;
        chunksFoundSinceStart = 0;
        state = SearchState.FOLLOW;
        lastChunkPos = null;
        lastTrailAddAt = 0L;
        trailEmptySince = 0L;
        circleEnteredAt = 0L;
        circleAngle = 0.0;
        ticksInCircle = 0;
    }

    /** Horizontal only: trail points sit at Y=0 and the player does not. */
    private static double horizontalDistance(Vec3d a, Vec3d b)
    {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Drops every trail point we have now got close to.
     *
     * <p>This is the whole fix for the yaw thrashing. A point fifty blocks away is worthless as a
     * target — walk twenty blocks sideways and the angle to it swings thirty degrees — and on a
     * sparse 1.12 trail almost every stored point ends up that close, because filling the deque
     * takes a minute and we cover two thousand blocks in that time. Consuming them leaves only
     * distant, unreached points in the average, which is the geometry a dense 1.19 trail has for
     * free. Consumption is permanent: a point we have already flown past cannot become a target
     * again, so turning back can never aim us at ground we have already covered.
     */
    private void consumeReachedPoints()
    {
        Vec3d me = mc.player.getEntityPos();
        double min = minAimDistance.get();

        trail.removeIf(p -> horizontalDistance(p, me) < min);

        if (trail.isEmpty())
        {
            if (trailEmptySince == 0L) trailEmptySince = System.currentTimeMillis();
        }
        else trailEmptySince = 0L;
    }

    boolean started = false;

    @Override
    public void onActivate()
    {
        resetTrail();
        XaeroPlus.EVENT_BUS.register(this);

        if (started)
        {
            if (mc.player != null && mc.world != null)
            {
                RegistryKey<World> currentDimension = mc.world.getRegistryKey();
                if (oppositeDimension.get())
                {
                    if (currentDimension.equals(World.END))
                    {
                        info("There is no opposite dimension to the end. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                    else if (currentDimension.equals(World.NETHER))
                    {
                        info("Following overworld trails from the nether is not supported yet, sorry. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                }
                if (flightMethod.get() != FollowMode.AUTO)
                {
                    followMode = flightMethod.get();
                }
                else
                {
                    if (!currentDimension.equals(World.NETHER))
                    {
                        followMode = FollowMode.YAWLOCK;
                        info("You are in the overworld or end, basic yaw mode will be used.");
                    }
                    else
                    {
                        try {
                            Class.forName("baritone.api.BaritoneAPI");
                            followMode = FollowMode.BARITONE;
                            info("You are in the nether, baritone mode will be used.");
                        } catch (ClassNotFoundException e) {
                            info("Baritone is required to trail follow in the nether. Disabling TrailFollower");
                            this.toggle();
                            return;
                        }
                    }
                }

                if (followMode == FollowMode.YAWLOCK && !mc.world.getRegistryKey().equals(World.NETHER)) {
                    if (overworldFlightMode.get() == OverworldFlightMode.PITCH40) {
                        // Use our existing Pitch40 module (equivalent of JEFF's Pitch40Util)
                        Class<? extends Module> pitch40Module = Pitch40.class;
                        Module pitch40ModuleInstance = Modules.get().get(pitch40Module);
                        if (!pitch40ModuleInstance.isActive()) {
                            pitch40ModuleInstance.toggle();
                            if (pitch40Firework.get()) {
                                Setting<Boolean> setting = ((Setting<Boolean>) pitch40ModuleInstance.settings.get("auto-firework"));
                                info("Auto Firework enabled on Pitch40 module. Adjust settings under the Pitch40 module.");
                                oldAutoFireworkValue = setting.get();
                                pitch40FireworkModified = true;
                                setting.set(true);
                            }
                        }
                    } else if (overworldFlightMode.get() == OverworldFlightMode.VANILLA) {
                        // Use our existing RocketFly module (equivalent of JEFF's AFKVanillaFly)
                        Class<? extends Module> rocketFlyModule = RocketFly.class;
                        Module rocketFlyInstance = Modules.get().get(rocketFlyModule);
                        if (rocketFlyInstance != null && !rocketFlyInstance.isActive()) {
                            rocketFlyInstance.toggle();
                        }
                    }
                }
                initialYaw = getActualYaw(mc.player.getYaw());
                hasInitialDirection = true;

                // set original pos to pathDistance blocks in the direction the player is facing
                Vec3d offset = (new Vec3d(Math.sin(-mc.player.getYaw() * Math.PI / 180), 0, Math.cos(-mc.player.getYaw() * Math.PI / 180)).normalize()).multiply(pathDistance.get());
                Vec3d targetPos = mc.player.getEntityPos().add(offset);
                for (int i = 0; i < (maxTrailLength.get() * startDirectionWeighting.get()); i++)
                {
                    trail.add(targetPos);
                }
                targetYaw = initialYaw;
            }
            else
            {
                this.toggle();
            }
            started = true;
        }
    }

    @Override
    public void onDeactivate()
    {
        started = false;
        // do this at the end to free memory
        seenChunksCache = Caffeine.newBuilder()
            .maximumSize(chunkCacheLength.get())
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
        XaeroPlus.EVENT_BUS.unregister(this);
        trail.clear();
        // If follow mode was never set due to baritone not being present, etc.
        if (followMode == null) return;
        switch (followMode)
        {
            case BARITONE:
            {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
                break;
            }
            case YAWLOCK: {
                if (mc.world == null || mc.world.getRegistryKey().equals(World.NETHER)) return;
                if (overworldFlightMode.get() == OverworldFlightMode.PITCH40) {
                    Class<? extends Module> pitch40Module = Pitch40.class;
                    Module pitch40ModuleInstance = Modules.get().get(pitch40Module);
                    if (pitch40ModuleInstance.isActive()) {
                        pitch40ModuleInstance.toggle();
                    }
                    if (pitch40FireworkModified) {
                        Setting<Boolean> autoFireworkSetting = (Setting<Boolean>) pitch40ModuleInstance.settings.get("auto-firework");
                        if (autoFireworkSetting != null) autoFireworkSetting.set(oldAutoFireworkValue);
                        pitch40FireworkModified = false;
                    }
                } else if (overworldFlightMode.get() == OverworldFlightMode.VANILLA) {
                    RocketFly rocketFlyInstance = Modules.get().get(RocketFly.class);
                    if (rocketFlyInstance != null) {
                        rocketFlyInstance.resetYLock();
                        if (rocketFlyInstance.isActive()) rocketFlyInstance.toggle();
                    }
                }
                break;
            }
        }
    }

    private double targetYaw;

    private int baritoneSetGoalTicks = 0;

    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        if (!started)
        {
            started = true;
            onActivate();
        }
        if (mc.player == null || mc.world == null) return;

        consumeReachedPoints();

        long now = System.currentTimeMillis();

        // The abandon clock runs only while circling. Flying through a gap, or flying back to the
        // anchor, is the module working — it used to count as the trail being over.
        if (followingTrail
            && state == SearchState.CIRCLE
            && now - Math.max(circleEnteredAt, lastFoundTrailTime) > trailTimeout.get())
        {
            resetTrail();
            log("Trail timed out, stopping.");
            switch (trailEndBehavior.get())
            {
                case DISABLE:
                {
                    this.toggle();
                    break;
                }
                case FLY_TOWARDS_YAW:
                {
                    targetYaw = trailEndYaw.get();
                    break;
                }
                case DISCONNECT:
                {
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[TrailFollower] Trail timed out.")));
                    break;
                }
            }
        }
        // Starvation is having nothing left to aim at, not merely nothing arriving. Until the
        // timeout runs out we fly straight on the heading we kept, which is what carries us over
        // a gap in chunk loading.
        if (followingTrail
            && state == SearchState.FOLLOW
            && trail.isEmpty()
            && lastChunkPos != null
            && now - Math.max(lastTrailAddAt, trailEmptySince) > chunkFoundTimeout.get())
        {
            state = SearchState.RETURN;
            log("Nothing left to aim at, returning to the last chunk "
                + (int) horizontalDistance(lastChunkPos, mc.player.getEntityPos()) + " blocks away.");
        }

        if (followingTrail && state != SearchState.FOLLOW && lastChunkPos != null)
        {
            double distAnchor = horizontalDistance(lastChunkPos, mc.player.getEntityPos());

            if (state == SearchState.RETURN)
            {
                // Smoothed like any other heading — this can be a real turn back on ourselves.
                targetYaw = Rotations.getYaw(lastChunkPos);

                if (distAnchor <= circleReturnRadius.get())
                {
                    state = SearchState.CIRCLE;
                    circleEnteredAt = now;
                    ticksInCircle = 0;
                    circleAngle = getActualYaw(mc.player.getYaw());
                    log("Back at the last chunk, spiralling out for the rest of the trail.");
                }
            }
            else
            {
                // A spiral, not a circle. A fixed ring searches the same ground forever: if the
                // next chunk of trail sits just outside it, it is never flown over and never
                // loads, so we orbit until the server happens to send it. Widening the ring a
                // little each second means the search eventually crosses wherever the trail went,
                // and crosses it sooner the further it has already had to look.
                ticksInCircle++;
                circleAngle += circlingDegPerTick.get();

                double radius = circleReturnRadius.get() + circleRadiusGrowth.get() * (ticksInCircle / 20.0);
                Vec3d target = lastChunkPos.add(yawToDirection(circleAngle).multiply(radius));

                targetYaw = Rotations.getYaw(target);

                if (mc.player.age % 100 == 0)
                {
                    long left = (long) ((trailTimeout.get() - (now - Math.max(circleEnteredAt, lastFoundTrailTime))) / 1000);
                    log("Spiralling at " + (int) radius + " blocks from the last chunk, abandoning trail in " + left + " seconds.");
                }
            }
        }

        if (debug.get() && followingTrail && mc.player.age % 20 == 0)
        {
            long since = now - lastFoundTrailTime;
            int distAnchor = lastChunkPos == null
                ? -1
                : (int) horizontalDistance(lastChunkPos, mc.player.getEntityPos());

            int spiral = state == SearchState.CIRCLE
                ? (int) (circleReturnRadius.get() + circleRadiusGrowth.get() * (ticksInCircle / 20.0))
                : -1;

            HunterBuddyAddon.LOG.info("[HB][TF] tick pos=({},{}) yaw={} sinceChunk={}ms trail={} state={} distAnchor={} spiral={}",
                (int) mc.player.getX(), (int) mc.player.getZ(),
                (int) mc.player.getYaw(), since, trail.size(), state, distAnchor, spiral);
        }

        switch (followMode)
        {
            case BARITONE:
            {
                if (baritoneSetGoalTicks > 0)
                {
                    baritoneSetGoalTicks--;
                }
                else if (baritoneSetGoalTicks == 0)
                {
                    baritoneSetGoalTicks = baritoneUpdateTicks.get();
                    if (mc.world.getRegistryKey().equals(World.NETHER)) {

                        if (!trail.isEmpty()) {
                            Vec3d baritoneTarget;
                            if (netherPathMode.get() == NetherPathMode.AVERAGE) {
                                Vec3d averagePos = calculateForwardWeightedAverage(trail);
                                Vec3d directionVec = averagePos.subtract(mc.player.getEntityPos()).normalize();
                                Vec3d predictedPos = mc.player.getEntityPos().add(directionVec.multiply(10));
                                double calculatedYaw = Rotations.getYaw(predictedPos);
                                double decayedWeight = getDecayedInitialWeight();

                                targetYaw = decayedWeight > 0.01
                                    ? blendYaw(calculatedYaw, initialYaw, decayedWeight)
                                    : calculatedYaw;

                                baritoneTarget = positionInDirection(mc.player.getEntityPos(), targetYaw, pathDistanceActual);
                            } else {
                                Vec3d lastPos = trail.getLast();
                                baritoneTarget = lastPos;
                            }

                            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                                .setGoalAndPath(new GoalXZ((int) baritoneTarget.x, (int) baritoneTarget.z));
                        }
                    } else {
                        // use average path for overworld
                        Vec3d targetPos = positionInDirection(mc.player.getEntityPos(), targetYaw, pathDistanceActual);
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) targetPos.x, (int) targetPos.z));

                        targetYaw = Rotations.getYaw(targetPos); // smooth rotation target
                    }
                    if (autoElytra.get() && (BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null))
                    {
                        BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
                    }
                }
                break;
            }
            case YAWLOCK: {
                mc.player.setYaw(smoothRotation(getActualYaw(mc.player.getYaw()), targetYaw, rotateScaling.get()));
                break;
            }
        }

    }

    Vec3d posDebug;

    @EventHandler
    private void onRender(Render3DEvent event)
    {
        if (!debug.get()) return;
        Vec3d targetPos = positionInDirection(mc.player.getEntityPos(), targetYaw, 10);
        // target line
        event.renderer.line(mc.player.getX(), mc.player.getY(), mc.player.getZ(), targetPos.x, targetPos.y, targetPos.z, new Color(255, 0, 0));
        // chunk
        if (posDebug != null) event.renderer.line(mc.player.getX(), mc.player.getY(), mc.player.getZ(), posDebug.x, targetPos.y, posDebug.z, new Color(0, 0, 255));
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event)
    {
        if (event.seenChunk()) return;
        RegistryKey<World> currentDimension = mc.world.getRegistryKey();
        WorldChunk chunk = event.chunk();
        ChunkPos chunkPos = chunk.getPos();
        long chunkLong = chunkPos.toLong();

        // if found in the cache then ignore the chunk
        if (seenChunksCache.getIfPresent(chunkLong) != null) return;

        ChunkPos chunkDelta = new ChunkPos(chunkPos.x - mc.player.getChunkPos().x, chunkPos.z - mc.player.getChunkPos().z);

        if (oppositeDimension.get())
        {
            if (currentDimension.equals(World.OVERWORLD))
            {
                chunkPos = new ChunkPos(mc.player.getChunkPos().x / 8 + chunkDelta.x, mc.player.getChunkPos().z / 8 + chunkDelta.z);
                currentDimension = World.NETHER;
            }
            else if (currentDimension.equals(World.NETHER))
            {
                chunkPos = new ChunkPos(mc.player.getChunkPos().x * 8 + chunkDelta.x, mc.player.getChunkPos().z * 8 + chunkDelta.z);
                currentDimension = World.OVERWORLD;
            }
        }
        // Check that the chunk is actually mapped, and that it is an old chunk
        if (!isValidChunk(chunkPos, currentDimension)) return;

        seenChunksCache.put(chunkLong, Byte.MAX_VALUE);

        Vec3d pos = chunk.getPos().getCenterAtY(0).toCenterPos();
        posDebug = pos;

        if (!followingTrail)
        {
            if (System.currentTimeMillis() - lastFoundPossibleTrailTime > chunkConsiderationWindow.get() * 1000)
            {
                possibleTrail.clear();
            }

            // While looking for a trail, only chunks roughly the way you are facing count. Without
            // this, a trail that happens to pass behind you starts the follow and turns you round.
            if (hasInitialDirection)
            {
                double chunkAngleToPlayer = Rotations.getYaw(pos);
                double angleDiffFromInitial = angleDifference(initialYaw, chunkAngleToPlayer);
                if (Math.abs(angleDiffFromInitial) > forwardConeAngle.get()) return;
            }

            possibleTrail.add(pos);
            lastFoundPossibleTrailTime = System.currentTimeMillis();
            if (possibleTrail.size() > chunksBeforeStarting.get())
            {
                log("Trail found, starting to follow.");
                followingTrail = true;
                lastFoundTrailTime = System.currentTimeMillis();
                chunksFoundSinceStart = possibleTrail.size();
                lastChunkPos = possibleTrail.getLast();
                lastTrailAddAt = lastFoundTrailTime;
                trail.addAll(possibleTrail);
                possibleTrail.clear();
            }
            return;
        }

        // add chunks to the list
        double chunkAngle = Rotations.getYaw(pos);
        double angleDiff = angleDifference(targetYaw, chunkAngle);

        // While returning or circling, targetYaw points at the anchor, so this guard would be
        // measuring against a heading that has nothing to do with the trail.
        if (followingTrail && state == SearchState.FOLLOW && Math.abs(angleDiff) > maxTrailDeviation.get())
        {
            return;
        }

        // The trail is alive whatever the distance — a near chunk is evidence, just not a target.
        double chunkDistance = horizontalDistance(pos, mc.player.getEntityPos());
        boolean aimable = chunkDistance >= minAimDistance.get();

        lastFoundTrailTime = System.currentTimeMillis();
        chunksFoundSinceStart++;
        lastChunkPos = pos;

        if (debug.get())
        {
            HunterBuddyAddon.LOG.info("[HB][TF] chunk +({},{}) angleVsYaw={} trail={} dist={} aim={}",
                (int) (pos.x - mc.player.getX()), (int) (pos.z - mc.player.getZ()),
                (int) angleDiff, trail.size() + (aimable ? 1 : 0), (int) chunkDistance, aimable);
        }

        if (!aimable) return;

        lastTrailAddAt = lastFoundTrailTime;
        trailEmptySince = 0L;

        if (state != SearchState.FOLLOW)
        {
            state = SearchState.FOLLOW;
            log("Trail picked up again, following.");
        }

        while(trail.size() >= maxTrailLength.get())
        {
            trail.pollFirst();
        }

        if (angleDiff > 0 && angleDiff < 90 && directionWeighting.get() == DirectionWeighting.LEFT)
        {
            // add extra chunks to increase the weighting
            for (int i = 0; i < directionWeightingMultiplier.get() - 1; i++)
            {
                trail.pollFirst();
                trail.add(pos);
            }
            trail.add(pos);
        }
        else if (angleDiff < 0 && angleDiff > -90 && directionWeighting.get() == DirectionWeighting.RIGHT)
        {
            for (int i = 0; i < directionWeightingMultiplier.get() - 1; i++)
            {
                trail.pollFirst();
                trail.add(pos);
            }
            trail.add(pos);
        }
        else
        {
            trail.add(pos);
        }

        if (!trail.isEmpty()) {
            double calculatedYaw;

            if (followMode == FollowMode.YAWLOCK) {
                Vec3d averagePos = calculateForwardWeightedAverage(trail);
                Vec3d positionVec = averagePos.subtract(mc.player.getEntityPos()).normalize();
                Vec3d targetPos = mc.player.getEntityPos().add(positionVec.multiply(10));
                calculatedYaw = Rotations.getYaw(targetPos);
            } else {
                Vec3d lastTrailPoint = trail.getLast();
                calculatedYaw = Rotations.getYaw(lastTrailPoint);
            }

            double decayedWeight = getDecayedInitialWeight();
            targetYaw = decayedWeight > 0.01
                ? blendYaw(calculatedYaw, initialYaw, decayedWeight)
                : calculatedYaw;

            if (debug.get())
            {
                HunterBuddyAddon.LOG.info("[HB][TF] aim trail={} found={} calcYaw={} initYaw={} weight={} -> yaw={}",
                    trail.size(), chunksFoundSinceStart, (int) calculatedYaw, (int) initialYaw,
                    String.format("%.2f", decayedWeight), (int) targetYaw);
            }
        }
    }

    private boolean isValidChunk(ChunkPos chunkPos, RegistryKey<World> currentDimension)
    {
        PaletteNewChunks paletteNewChunks = ModuleManager.getModule(PaletteNewChunks.class);
        boolean is119NewChunk = paletteNewChunks
            .isNewChunk(
                chunkPos.x,
                chunkPos.z,
                currentDimension
            );

        boolean is112OldChunk = ModuleManager.getModule(OldChunks.class)
            .isOldChunk(
                chunkPos.x,
                chunkPos.z,
                currentDimension
            );

        boolean isHighlighted = is119NewChunk || paletteNewChunks
            .isInverseNewChunk(
                chunkPos.x,
                chunkPos.z,
                currentDimension
            );

        return isHighlighted && ((!is119NewChunk && !only112.get()) || is112OldChunk);
    }

    private Vec3d calculateAveragePosition(ArrayDeque<Vec3d> positions)
    {
        double sumX = 0, sumZ = 0;
        for (Vec3d pos : positions) {
            sumX += pos.x;
            sumZ += pos.z;
        }
        return new Vec3d(sumX / positions.size(), 0, sumZ / positions.size());
    }

    /**
     * The trail's centre of mass, but with the chunks ahead of us counting for more than the ones
     * off to the side, and the near ones for more than the far ones.
     *
     * <p>A plain average treats a chunk behind your shoulder exactly like the one you are flying
     * towards. Weighting by alignment keeps a side branch from pulling the heading off the trail
     * you are actually on, without letting any single chunk decide the answer on its own.
     */
    private Vec3d calculateForwardWeightedAverage(ArrayDeque<Vec3d> positions)
    {
        if (positions.isEmpty()) return mc.player.getEntityPos();
        if (forwardWeightStrength.get() <= 0.01) return calculateAveragePosition(positions);

        double sumX = 0.0;
        double sumZ = 0.0;
        double totalWeight = 0.0;
        Vec3d playerPos = mc.player.getEntityPos();

        for (Vec3d pos : positions)
        {
            double chunkYaw = Rotations.getYaw(pos);
            double angleDiff = Math.abs(angleDifference(targetYaw, chunkYaw));
            double alignmentFactor = 1.0 - angleDiff / 180.0;
            double weight = 1.0 - forwardWeightStrength.get() + forwardWeightStrength.get() * alignmentFactor;
            double dist = playerPos.distanceTo(pos);
            double distWeight = 1.0 / (1.0 + dist / 256.0);
            double finalWeight = weight * (0.5 + 0.5 * distWeight);

            sumX += pos.x * finalWeight;
            sumZ += pos.z * finalWeight;
            totalWeight += finalWeight;
        }

        return totalWeight <= 0.0
            ? calculateAveragePosition(positions)
            : new Vec3d(sumX / totalWeight, 0.0, sumZ / totalWeight);
    }

    /**
     * How much the direction you were facing on enable still counts for.
     *
     * <p>Full weight at the start, when a handful of chunks is not yet evidence of anything, and
     * gone by the time twice a trail's worth has been found and the trail speaks for itself.
     */
    private double getDecayedInitialWeight()
    {
        if (!hasInitialDirection || startDirectionWeighting.get() <= 0.0) return 0.0;

        int decayChunks = maxTrailLength.get() * 2;
        double decayFactor = Math.max(0.0, 1.0 - (double) chunksFoundSinceStart / decayChunks);

        return startDirectionWeighting.get() * decayFactor;
    }

    /** Interpolates between two headings the short way round, so 350 and 10 meet at 0, not 180. */
    private double blendYaw(double yaw1, double yaw2, double weight)
    {
        yaw1 = (yaw1 % 360.0 + 360.0) % 360.0;
        yaw2 = (yaw2 % 360.0 + 360.0) % 360.0;

        double diff = yaw2 - yaw1;
        if (diff > 180.0) diff -= 360.0;
        if (diff < -180.0) diff += 360.0;

        double result = yaw1 + diff * weight;
        return (result % 360.0 + 360.0) % 360.0;
    }

    private float getActualYaw(float yaw)
    {
        return (yaw % 360 + 360) % 360;
    }

    private void log(String message)
    {
        info(message);
        if (!webhookLink.get().isEmpty())
        {
            sendWebhook(webhookLink.get(), "TrailFollower", message, null, mc.player.getGameProfile().name());
        }
    }

    public enum FollowMode
    {
        AUTO,
        BARITONE,
        YAWLOCK
    }

    public enum DirectionWeighting
    {
        LEFT,
        NONE,
        RIGHT
    }

    public enum TrailEndBehavior
    {
        DISABLE,
        FLY_TOWARDS_YAW,
        DISCONNECT
    }

    private enum SearchState
    {
        FOLLOW,
        RETURN,
        CIRCLE
    }
}