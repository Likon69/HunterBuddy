package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.TrailHeading;
import com.hunterbuddy.util.TrailScan;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.Utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayDeque;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.math.Vec3d;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import xaeroplus.settings.Settings;

public class TrailFollower extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<Integer> maxTrailLength = this.sgGeneral
        .add(
            new Builder().name("max-trail-length")
                .description(
                    "The number of trail points to keep for the average. Adjust to change how quickly the average will change. More does not necessarily equal better because if the list is too long it will contain chunks behind you."
                ).defaultValue(20)
                .sliderRange(1, 100)
                .build()
        );
    public final Setting<Integer> chunksBeforeStarting = this.sgGeneral
        .add(
            new Builder().name("chunks-before-starting")
                .description("Useful for afking looking for a trail. The amount of chunks before it gets detected as a trail.")
                .defaultValue(10)
                .sliderRange(1, 50)
                .build()
        );
    public final Setting<Integer> chunkConsiderationWindow = this.sgGeneral
        .add(
            new Builder().name("chunk-timeframe")
                .description("The amount of time in seconds that the chunks must be found in before starting.")
                .defaultValue(5)
                .sliderRange(1, 20)
                .build()
        );
    public final Setting<TrailFollower.TrailEndBehavior> trailEndBehavior = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<TrailFollower.TrailEndBehavior>()
                .name("trail-end-behavior")
                .description("What to do when the trail ends.")
                .defaultValue(TrailFollower.TrailEndBehavior.DISABLE)
                .build()
        );
    public final Setting<Double> trailEndYaw = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("trail-end-yaw")
                .description("The direction to go after the trail is abandoned.")
                .defaultValue(0.0)
                .sliderRange(0.0, 359.9)
                .visible(() -> this.trailEndBehavior.get() == TrailFollower.TrailEndBehavior.FLY_TOWARDS_YAW)
                .build()
        );
    public final Setting<TrailFollower.OverworldFlightMode> overworldFlightMode = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<TrailFollower.OverworldFlightMode>()
                .name("overworld-flight-mode")
                .description(
                    "Choose how TrailFollower flies in Overworld. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail."
                ).defaultValue(TrailFollower.OverworldFlightMode.PITCH40)
                .build()
        );
    public final Setting<TrailFollower.NetherPathMode> netherPathMode = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<TrailFollower.NetherPathMode>()
                .name("nether-path-mode")
                .description(
                    "Choose how TrailFollower does baritone pathing in Nether. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail."
                ).defaultValue(TrailFollower.NetherPathMode.AVERAGE)
                .build()
        );
    public final Setting<Boolean> pitch40Firework = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-firework")
                .description("Uses a firework automatically if your velocity is too low.")
                .defaultValue(true)
                .visible(() -> this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40)
                .build()
        );
    public final Setting<Double> rotateScaling = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("rotate-scaling")
                .description("Scaling of how fast the yaw changes. 1 = instant, 0 = doesn't change")
                .defaultValue(0.15)
                .sliderRange(0.0, 1.0)
                .build()
        );
    public final Setting<Integer> resumeWindow = this.sgGeneral.add(new Builder()
        .name("resume-window")
        .description("Seconds to keep a trail heading for resuming after a pause. Set to 0 to disable.")
        .defaultValue(3600).min(0).sliderRange(0, 7200).build());
    public final Setting<Double> resumeMaxDistance = this.sgGeneral.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
        .name("resume-max-distance")
        .description("Maximum distance in blocks from the saved position to resume a trail heading.")
        .defaultValue(512.0).min(0.0).sliderMax(2048.0).build());
    public final Setting<Boolean> oppositeDimension = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("opposite-dimension")
                .description("Follows trails from the opposite dimension (Requires that you've already loaded the other dimension with XP).")
                .defaultValue(false)
                .build()
        );
    public final Setting<Boolean> autoElytra = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("auto-start-baritone-elytra")
                .description("Starts baritone elytra for you.")
                .defaultValue(true)
                .build()
        );
    private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced", false);
    public final Setting<Double> pathDistance = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("path-distance")
                .description("The distance to add trail positions in the direction the player is facing. (Ignored when following overworld from nether)")
                .defaultValue(500.0)
                .sliderRange(100.0, 2000.0)
                .onChanged(value -> this.pathDistanceActual = value).build()
        );
    public final Setting<TrailFollower.FollowMode> flightMethod = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<TrailFollower.FollowMode>()
                .name("flight-method")
                .description("Decided how the goals will be used. Leave this on AUTO unless you want to use yaw lock in the nether for example.")
                .defaultValue(TrailFollower.FollowMode.AUTO)
                .build()
        );
    public final Setting<Double> startDirectionWeighting = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("start-direction-weight")
                .description("Initial bias toward the direction you're facing when enabling. Decays as trail becomes established. 0 = no bias, 1 = strong bias.")
                .defaultValue(0.7)
                .min(0.0)
                .sliderMax(1.0)
                .build()
        );
    public final Setting<Double> forwardConeAngle = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("forward-cone-angle")
                .description(
                    "During initial detection, only consider chunks within this angle of your facing direction. 90 = hemisphere ahead, 180 = all around."
                ).defaultValue(120.0)
                .min(45.0)
                .sliderMax(180.0)
                .build()
        );
    public final Setting<Double> forwardWeightStrength = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("forward-weight-strength")
                .description("How much to favor chunks aligned with current direction in the average. 0 = equal weight, 1 = strong forward preference.")
                .defaultValue(0.6)
                .min(0.0)
                .sliderMax(1.0)
                .build()
        );
    public final Setting<TrailFollower.DirectionWeighting> directionWeighting = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<TrailFollower.DirectionWeighting>()
                .name("direction-weighting")
                .description(
                    "How the chunks found should be weighted. Useful for path splits. Left will weight chunks to the left of the player higher, right will weigh chunks to the right higher, and none will be in the middle/random. "
                ).defaultValue(TrailFollower.DirectionWeighting.NONE)
                .build()
        );
    public final Setting<Integer> directionWeightingMultiplier = this.sgAdvanced
        .add(
            new Builder().name("direction-weighting-multiplier")
                .description(
                    "The multiplier for how much weight should be given to chunks in the direction specified. Values are capped to be in the range [2, maxTrailLength]."
                ).defaultValue(2)
                .min(2)
                .sliderMax(10)
                .visible(() -> this.directionWeighting.get() != TrailFollower.DirectionWeighting.NONE)
                .build()
        );
    public final Setting<Boolean> only112 = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("follow-only-1.12")
                .description("Will only follow 1.12 chunks and will ignore other ones.")
                .defaultValue(false)
                .build()
        );
    public final Setting<Double> chunkFoundTimeout = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("chunk-found-timeout")
                .description(
                    "The amount of MS without a trail chunk ahead of you before scanning for the trail. Raise on laggy servers so normal chunk droughts don't trigger the scan."
                ).defaultValue(8000.0)
                .min(1000.0)
                .sliderMax(20000.0)
                .build()
        );
    public final Setting<TrailScan.Mode> lostTrailScan = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.EnumSetting.Builder<TrailScan.Mode>()
                .name("lost-trail-scan")
                .description(
                    "How to look for the trail once chunks stop arriving. WEAVE swings left and right of the trail direction while moving on, SPIN turns in place, HOLD keeps the trail direction. Baritone mode treats SPIN as HOLD."
                ).defaultValue(TrailScan.Mode.WEAVE)
                .build()
        );
    public final Setting<Double> weaveWidth = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("weave-width")
                .description(
                    "Initial swing width in degrees on each side of the trail direction. It grows to 90 the longer no chunk arrives ahead of you."
                ).defaultValue(35.0)
                .min(10.0)
                .sliderMax(90.0)
                .visible(() -> this.lostTrailScan.get() == TrailScan.Mode.WEAVE)
                .build()
        );
    public final Setting<Double> circlingDegPerTick = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("Circling-degrees-per-tick")
                .description("How fast the lost-trail scan moves, in degrees per tick: the turning speed in SPIN, the weaving speed in WEAVE.")
                .defaultValue(2.0)
                .min(1.0)
                .sliderMax(20.0)
                .build()
        );
    public final Setting<Double> trailTimeout = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("trail-timeout")
                .description("The amount of MS without a chunk found to stop following the trail.")
                .defaultValue(30000.0)
                .min(10000.0)
                .sliderMax(180000.0)
                .build()
        );
    public final Setting<Double> maxTrailDeviation = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("max-trail-deviation")
                .description("Chunks farther than this many degrees from the trail heading are not counted as this trail, so a crossing trail cannot take over.")
                .defaultValue(180.0)
                .min(1.0)
                .sliderMax(270.0)
                .build()
        );
    public final Setting<Boolean> ignoreBehind = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("ignore-behind")
                .description("Skips chunks that load behind you (server lag), so they cannot turn you back the way you came.")
                .defaultValue(true)
                .build()
        );
    public final Setting<Double> behindAngle = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("behind-angle")
                .description("A chunk further than this many degrees from the trail direction counts as behind you.")
                .defaultValue(110.0)
                .min(60.0)
                .sliderMax(180.0)
                .visible(this.ignoreBehind::get)
                .build()
        );
    public final Setting<Double> headingTurnLimit = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("heading-turn-limit")
                .description("Most degrees per second the trail direction may turn. Keeps a burst of sideways chunks from whipping it around.")
                .defaultValue(45.0)
                .min(5.0)
                .sliderMax(180.0)
                .build()
        );
    public final Setting<Integer> chunkCacheLength = this.sgAdvanced
        .add(
            new Builder().name("chunk-cache-length")
                .description("The amount of chunks to keep in the cache. (Won't be applied until deactivating)")
                .defaultValue(100000)
                .sliderRange(0, 10000000)
                .build()
        );
    public final Setting<String> webhookLink = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                .name("webhook-link")
                .description("Will send all updates to the webhook link. Leave blank to disable.")
                .defaultValue("")
                .build()
        );
    public final Setting<Integer> baritoneUpdateTicks = this.sgAdvanced
        .add(
            new Builder().name("baritone-path-update-ticks")
                .description("The amount of ticks between updates to the baritone goal. Low values may cause high instability.")
                .defaultValue(100)
                .sliderRange(20, 600)
                .build()
        );
    public final Setting<Boolean> debug = this.sgAdvanced
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("debug")
                .description("Debug mode.")
                .defaultValue(false)
                .build()
        );
    private boolean oldAutoFireworkValue;
    private boolean oldAutoBoundAdjustValue;
    private boolean pitch40ToggledByUs;
    private boolean rocketFlyToggledByUs;
    private boolean autoFireworkChangedByUs;
    private TrailFollower.FollowMode followMode;
    private boolean followingTrail = false;
    private ArrayDeque<Vec3d> trail = new ArrayDeque<>();
    private ArrayDeque<Vec3d> possibleTrail = new ArrayDeque<>();
    private long lastFoundTrailTime;
    private long lastAheadChunkTime;
    private long lastFoundPossibleTrailTime;
    private double pathDistanceActual = this.pathDistance.get();
    private boolean started = false;
    private double initialYaw = 0.0;
    private boolean hasInitialDirection = false;
    private int chunksFoundSinceStart = 0;
    private Cache<Long, Byte> seenChunksCache = Caffeine.newBuilder()
        .maximumSize(this.chunkCacheLength.get().intValue())
        .expireAfterWrite(Duration.ofMinutes(5L))
        .build();
    private double targetYaw;
    private final TrailHeading heading = new TrailHeading();
    private final TrailScan scan = new TrailScan();
    private double resumeYaw;
    private double resumeDesiredYaw;
    private int resumeChunks;
    private RegistryKey<World> resumeDimension;
    private Vec3d resumePos;
    private long resumeAt;
    private boolean resumeValid;
    private boolean resumeAwaitingChunk;
    private RegistryKey<World> activeDimension;
    private int baritoneSetGoalTicks = 0;
    Vec3d posDebug;

    public TrailFollower() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "TrailFollower", "Automatically follows trails in all dimensions.");
    }

    public float headingYaw() { return (float) (followingTrail ? heading.steady() : targetYaw); }

    void resetTrail() {
        this.posDebug = null;
        this.resumeAwaitingChunk = false;
        this.baritoneSetGoalTicks = 0;
        this.followingTrail = false;
        this.trail = new ArrayDeque<>();
        this.possibleTrail = new ArrayDeque<>();
        this.hasInitialDirection = false;
        this.chunksFoundSinceStart = 0;
        this.scan.reset();
    }

    @Override
    public void onActivate() {
        this.resetTrail();
        XaeroPlus.EVENT_BUS.unregister(this);
        XaeroPlus.EVENT_BUS.register(this);
        if (this.started) {
            if (this.mc.player != null && this.mc.world != null) {
                RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
                this.activeDimension = currentDimension;
                if (this.oppositeDimension.get()) {
                    if (currentDimension.equals(World.END)) {
                        this.info("There is no opposite dimension to the end. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }

                    if (currentDimension.equals(World.NETHER)) {
                        this.info("Following overworld trails from the nether is not supported yet, sorry. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                }

                if (this.flightMethod.get() != TrailFollower.FollowMode.AUTO) {
                    this.followMode = this.flightMethod.get();
                } else if (!currentDimension.equals(World.NETHER)) {
                    this.followMode = TrailFollower.FollowMode.YAWLOCK;
                    this.info("You are in the overworld or end, basic yaw mode will be used.");
                } else {
                    try {
                        Class.forName("baritone.api.BaritoneAPI");
                        this.followMode = TrailFollower.FollowMode.BARITONE;
                        this.info("You are in the nether, baritone mode will be used.");
                    } catch (ClassNotFoundException e) {
                        this.info("Baritone is required to trail follow in the nether. Disabling TrailFollower");
                        this.toggle();
                        return;
                    }
                }

                if (this.followMode == TrailFollower.FollowMode.YAWLOCK && !this.mc.world.getRegistryKey().equals(World.NETHER)) {
                    if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40) {
                        Class<? extends Module> pitch40Util = Pitch40.class;
                        Module pitch40UtilModule = Modules.get().get(pitch40Util);
                        if (!pitch40UtilModule.isActive()) {
                            pitch40UtilModule.toggle();
                            this.pitch40ToggledByUs = true;
                            if (this.pitch40Firework.get()) {
                                Setting<Boolean> setting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-firework");
                                if (setting != null) {
                                    this.info(
                                        "Pitch40 auto firework is on. Its speed threshold and firework delay are in the Pitch40 settings."
                                    );
                                    this.oldAutoFireworkValue = setting.get();
                                    setting.set(true);
                                    this.autoFireworkChangedByUs = true;
                                }
                            }

                            Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-bound-adjust");
                            if (autoBoundAdjustSetting != null) {
                                this.oldAutoBoundAdjustValue = autoBoundAdjustSetting.get();
                                autoBoundAdjustSetting.set(false);
                            }
                        }
                    } else if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.ROCKETS) {
                        RocketFly rocketFly = (RocketFly) Modules.get().get(RocketFly.class);
                        if (!rocketFly.isActive()) {
                            rocketFly.toggle();
                            this.rocketFlyToggledByUs = true;
                        }
                    }
                }

                long now = System.currentTimeMillis();
                long resumeWindowMs = this.resumeWindow.get() * 1000L;
                if (this.resumeValid && resumeWindowMs > 0L && now - this.resumeAt > resumeWindowMs) {
                    this.resumeValid = false;
                }
                boolean resuming = this.resumeValid && resumeWindowMs > 0L
                    && currentDimension.equals(this.resumeDimension)
                    && this.mc.player.getEntityPos().distanceTo(this.resumePos) <= this.resumeMaxDistance.get()
                    && now - this.resumeAt <= resumeWindowMs
                    && Math.abs(MathHelper.wrapDegrees(this.mc.player.getYaw() - this.resumeYaw)) <= 90.0;
                this.initialYaw = resuming ? this.resumeYaw : this.getActualYaw(this.mc.player.getYaw());
                this.hasInitialDirection = true;
                double seedYaw = resuming ? this.resumeYaw : this.mc.player.getYaw();
                Vec3d offset = new Vec3d(
                        Math.sin(-seedYaw * Math.PI / 180.0), 0.0, Math.cos(-seedYaw * Math.PI / 180.0)
                    )
                    .normalize()
                    .multiply(this.pathDistance.get());
                Vec3d targetPos = this.mc.player.getEntityPos().add(offset);

                for (int i = 0; i < this.maxTrailLength.get().intValue() * this.startDirectionWeighting.get(); i++) {
                    this.trail.add(targetPos);
                }

                this.targetYaw = this.initialYaw;
                if (resuming) {
                    this.heading.reset(this.resumeYaw);
                    this.heading.setWanted(this.resumeDesiredYaw);
                    this.followingTrail = true;
                    this.chunksFoundSinceStart = this.resumeChunks;
                    this.lastFoundTrailTime = this.lastAheadChunkTime = now;
                    this.scan.stop();
                    this.resumeAwaitingChunk = true;
                    this.log("trail heading resumed " + this.resumeYaw);
                }
                try {
                    boolean changed = false;
                    if (!Settings.REGISTRY.paletteNewChunksEnabledSetting.get()) {
                        Settings.REGISTRY.paletteNewChunksEnabledSetting.setValue(true);
                        changed = true;
                    }
                    if (this.only112.get() && !Settings.REGISTRY.oldChunksEnabledSetting.get()) {
                        Settings.REGISTRY.oldChunksEnabledSetting.setValue(true);
                        changed = true;
                    }
                    if (changed) this.info("Enabled XaeroPlus trail highlights.");
                } catch (Exception e) {
                    this.warning("Could not enable XaeroPlus trail highlights.");
                }
            } else {
                this.toggle();
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (this.followingTrail && this.mc.player != null && this.mc.world != null) {
            this.resumeYaw = this.heading.steady();
            this.resumeDesiredYaw = this.heading.wanted();
            this.resumeChunks = this.chunksFoundSinceStart;
            this.resumeDimension = this.mc.world.getRegistryKey();
            this.resumePos = this.mc.player.getEntityPos();
            this.resumeAt = System.currentTimeMillis();
            this.resumeValid = true;
        }
        this.started = false;
        this.seenChunksCache = Caffeine.newBuilder().maximumSize(this.chunkCacheLength.get().intValue()).expireAfterWrite(Duration.ofMinutes(5L)).build();
        XaeroPlus.EVENT_BUS.unregister(this);
        this.trail.clear();
        if (this.rocketFlyToggledByUs) {
            RocketFly rocketFly = (RocketFly) Modules.get().get(RocketFly.class);
            if (rocketFly != null) {
                rocketFly.resetYLock();
                if (rocketFly.isActive()) {
                    rocketFly.toggle();
                }
            }
        }
        if (this.pitch40ToggledByUs) {
            Class<? extends Module> pitch40Util = Pitch40.class;
            Module pitch40UtilModule = Modules.get().get(pitch40Util);
            if (pitch40UtilModule != null) {
                if (pitch40UtilModule.isActive()) {
                    pitch40UtilModule.toggle();
                }

                Setting<Boolean> autoFireworkSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-firework");
                if (this.autoFireworkChangedByUs && autoFireworkSetting != null) {
                    autoFireworkSetting.set(this.oldAutoFireworkValue);
                }

                Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-bound-adjust");
                if (autoBoundAdjustSetting != null) {
                    autoBoundAdjustSetting.set(this.oldAutoBoundAdjustValue);
                }
            }
        }
        if (this.followMode == TrailFollower.FollowMode.BARITONE) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
        }
        this.pitch40ToggledByUs = false;
        this.rocketFlyToggledByUs = false;
        this.autoFireworkChangedByUs = false;
    }

    private void searchForTrail() {
        long now = System.currentTimeMillis();
        this.targetYaw = this.scan.next(
            this.lostTrailScan.get(),
            this.followMode == TrailFollower.FollowMode.BARITONE,
            this.heading.steady(),
            this.circlingDegPerTick.get(),
            this.weaveWidth.get(),
            now - this.lastAheadChunkTime,
            this.chunkFoundTimeout.get(),
            this.trailTimeout.get()
        );

        if (this.mc.player.age % 100 == 0) {
            this.log(
                "Trail lost, scanning for it. Giving up in "
                    + (this.trailTimeout.get() - (now - this.lastFoundTrailTime)) / 1000.0
                    + " seconds."
            );
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player != null && this.mc.world != null) {
            if (this.started && !this.oppositeDimension.get() && !this.mc.world.getRegistryKey().equals(this.activeDimension)) {
                this.followingTrail = false;
                this.onDeactivate();
                this.resumeValid = false;
                this.resetTrail();
                return;
            }
            if (!this.started) {
                if (this.mc.player.age < 20 || !this.isActive()) return;
                this.started = true;
                this.onActivate();
                if (!this.isActive()) {
                    return;
                }
            }

            long now = System.currentTimeMillis();
            if (this.resumeAwaitingChunk) this.lastFoundTrailTime = this.lastAheadChunkTime = now;
            long sinceLastChunk = now - this.lastFoundTrailTime;
            if (!this.resumeAwaitingChunk && this.followingTrail && sinceLastChunk > this.trailTimeout.get()) {
                this.resumeValid = false;
                this.resetTrail();
                this.log("Trail timed out, stopping.");
                switch ((TrailFollower.TrailEndBehavior)this.trailEndBehavior.get()) {
                    case DISABLE:
                        this.toggle();
                        break;
                    case FLY_TOWARDS_YAW:
                        this.targetYaw = this.trailEndYaw.get();
                        break;
                    case DISCONNECT:
                        this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[TrailFollower] Trail timed out.")));
                }

                if (!this.isActive()) {
                    return;
                }
            }

            if (!this.resumeAwaitingChunk && this.followingTrail && now - this.lastAheadChunkTime > this.chunkFoundTimeout.get()) {
                this.searchForTrail();
            } else {
                this.scan.stop();
                if (this.followingTrail) {
                    this.heading.turn(this.headingTurnLimit.get() / 20.0);
                    this.targetYaw = this.heading.steady();
                }
            }

            switch (this.followMode) {
                case BARITONE:
                    if (this.baritoneSetGoalTicks > 0) {
                        this.baritoneSetGoalTicks--;
                    } else if (this.baritoneSetGoalTicks == 0) {
                        this.baritoneSetGoalTicks = this.baritoneUpdateTicks.get();
                        if (this.mc.world.getRegistryKey().equals(World.NETHER)) {
                            if (!this.trail.isEmpty()) {
                                Vec3d baritoneTarget;
                                if (this.netherPathMode.get() == TrailFollower.NetherPathMode.AVERAGE) {
                                    baritoneTarget = this.offsetAlongYaw(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                                } else {
                                    baritoneTarget = this.trail.getLast();
                                }

                                BaritoneAPI.getProvider()
                                    .getPrimaryBaritone()
                                    .getCustomGoalProcess()
                                    .setGoalAndPath(new GoalXZ((int)baritoneTarget.x, (int)baritoneTarget.z));
                            }
                        } else {
                            Vec3d targetPos = this.offsetAlongYaw(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                            BaritoneAPI.getProvider()
                                .getPrimaryBaritone()
                                .getCustomGoalProcess()
                                .setGoalAndPath(new GoalXZ((int)targetPos.x, (int)targetPos.z));
                        }

                        if (this.autoElytra.get()
                            && BaritoneHelper.hasElytraProcess()
                            && BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() == null) {
                            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
                        }
                    }
                    break;
                case YAWLOCK:
                    this.mc
                        .player
                        .setYaw(this.smoothYaw(this.getActualYaw(this.mc.player.getYaw()), this.targetYaw, this.rotateScaling.get()));
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.started && this.debug.get()) {
            Vec3d targetPos = this.offsetAlongYaw(this.mc.player.getEntityPos(), this.targetYaw, 10.0);
            event.renderer
                .line(
                    this.mc.player.getX(),
                    this.mc.player.getY(),
                    this.mc.player.getZ(),
                    targetPos.x,
                    targetPos.y,
                    targetPos.z,
                    new Color(255, 0, 0)
                );
            if (this.posDebug != null) {
                event.renderer
                    .line(
                        this.mc.player.getX(),
                        this.mc.player.getY(),
                        this.mc.player.getZ(),
                        this.posDebug.x,
                        targetPos.y,
                        this.posDebug.z,
                        new Color(0, 0, 255)
                    );
            }
        }
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event) {
        if (!event.seenChunk()) {
            if (this.mc.player != null && this.mc.world != null) {
                if (!this.started) return;
                RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
                WorldChunk chunk = event.chunk();
                ChunkPos chunkPos = chunk.getPos();
                long chunkLong = chunkPos.toLong();
                if (this.seenChunksCache.getIfPresent(chunkLong) == null) {
                    ChunkPos chunkDelta = new ChunkPos(
                        chunkPos.x - this.mc.player.getChunkPos().x, chunkPos.z - this.mc.player.getChunkPos().z
                    );
                    if (this.oppositeDimension.get()) {
                        if (currentDimension.equals(World.OVERWORLD)) {
                            chunkPos = new ChunkPos(
                                this.mc.player.getChunkPos().x / 8 + chunkDelta.x,
                                this.mc.player.getChunkPos().z / 8 + chunkDelta.z
                            );
                            currentDimension = World.NETHER;
                        } else if (currentDimension.equals(World.NETHER)) {
                            chunkPos = new ChunkPos(
                                this.mc.player.getChunkPos().x * 8 + chunkDelta.x,
                                this.mc.player.getChunkPos().z * 8 + chunkDelta.z
                            );
                            currentDimension = World.OVERWORLD;
                        }
                    }

                    if (this.isValidChunk(chunkPos, currentDimension)) {
                        this.seenChunksCache.put(chunkLong, (byte)127);
                        Vec3d pos = chunk.getPos().getCenterAtY(0).toCenterPos();
                        this.posDebug = pos;
                        if (!this.followingTrail) {
                            if (System.currentTimeMillis() - this.lastFoundPossibleTrailTime > this.chunkConsiderationWindow.get() * 1000) {
                                this.possibleTrail.clear();
                            }

                            if (this.hasInitialDirection) {
                                double chunkAngleToPlayer = Rotations.getYaw(pos);
                                double angleDiffFromInitial = MathHelper.wrapDegrees(this.initialYaw - chunkAngleToPlayer);
                                if (Math.abs(angleDiffFromInitial) > this.forwardConeAngle.get()) {
                                    return;
                                }
                            }

                            this.possibleTrail.add(pos);
                            this.lastFoundPossibleTrailTime = System.currentTimeMillis();
                            if (this.possibleTrail.size() > this.chunksBeforeStarting.get()) {
                                this.log("Trail found, starting to follow.");
                                this.followingTrail = true;
                                this.lastFoundTrailTime = System.currentTimeMillis();
                                this.lastAheadChunkTime = this.lastFoundTrailTime;
                                this.chunksFoundSinceStart = this.possibleTrail.size();
                                if (!this.hasInitialDirection) {
                                    this.initialYaw = this.targetYaw;
                                    this.hasInitialDirection = true;
                                }

                                this.heading.reset(this.targetYaw);
                                this.scan.stop();
                                this.trail.addAll(this.possibleTrail);
                                this.possibleTrail.clear();
                            }
                        } else {
                            double chunkAngle = Rotations.getYaw(pos);
                            double offset = this.heading.offset(chunkAngle);
                            TrailHeading.Verdict verdict = this.heading.judge(
                                chunkAngle, this.maxTrailDeviation.get(), this.ignoreBehind.get(), this.behindAngle.get()
                            );
                            if (verdict != TrailHeading.Verdict.OUTSIDE) {
                                long now = System.currentTimeMillis();
                                this.lastFoundTrailTime = now;
                                if (verdict != TrailHeading.Verdict.BEHIND) {
                                    if (verdict == TrailHeading.Verdict.AHEAD) {
                                        this.lastAheadChunkTime = now;
                                    }

                                    while (this.trail.size() >= this.maxTrailLength.get()) {
                                        this.trail.pollFirst();
                                    }

                                    if (offset > 0.0 && offset < 90.0 && this.directionWeighting.get() == TrailFollower.DirectionWeighting.LEFT) {
                                        for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                                            this.trail.pollFirst();
                                            this.trail.add(pos);
                                        }

                                        this.trail.add(pos);
                                    } else if (offset < 0.0 && offset > -90.0 && this.directionWeighting.get() == TrailFollower.DirectionWeighting.RIGHT) {
                                        for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                                            this.trail.pollFirst();
                                            this.trail.add(pos);
                                        }

                                        this.trail.add(pos);
                                    } else {
                                        this.trail.add(pos);
                                    }

                                    this.chunksFoundSinceStart++;
                                    this.resumeAwaitingChunk = false;
                                    if (!this.trail.isEmpty()) {
                                        Vec3d averagePos = this.calculateForwardWeightedAverage(this.trail);
                                        if (averagePos != null) {
                                            Vec3d positionVec = averagePos.subtract(this.mc.player.getEntityPos()).normalize();
                                            Vec3d targetPos = this.mc.player.getEntityPos().add(positionVec.multiply(10.0));
                                            double calculatedYaw = Rotations.getYaw(targetPos);
                                            double decayedWeight = this.getDecayedInitialWeight();
                                            if (decayedWeight > 0.01) {
                                                this.heading.setWanted(this.blendYaw(calculatedYaw, this.initialYaw, decayedWeight));
                                            } else {
                                                this.heading.setWanted(calculatedYaw);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isValidChunk(ChunkPos chunkPos, RegistryKey<World> currentDimension) {
        PaletteNewChunks paletteNewChunks = ModuleManager.getModule(PaletteNewChunks.class);
        boolean is119NewChunk = paletteNewChunks.isNewChunk(chunkPos.x, chunkPos.z, currentDimension);
        boolean is112OldChunk = ModuleManager.getModule(OldChunks.class).isOldChunk(chunkPos.x, chunkPos.z, currentDimension);
        boolean isHighlighted = is119NewChunk || paletteNewChunks.isInverseNewChunk(chunkPos.x, chunkPos.z, currentDimension);
        return isHighlighted && (!is119NewChunk && !this.only112.get() || is112OldChunk);
    }

    private Vec3d calculateForwardWeightedAverage(ArrayDeque<Vec3d> positions) {
        if (positions.isEmpty()) {
            return null;
        }

        double sumX = 0.0;
        double sumZ = 0.0;
        double totalWeight = 0.0;
        Vec3d playerPos = this.mc.player.getEntityPos();

        for (Vec3d pos : positions) {
            double w = TrailHeading.weight(
                Math.abs(this.heading.offset(Rotations.getYaw(pos))), this.forwardWeightStrength.get(), playerPos.distanceTo(pos)
            );
            if (w <= 0) continue;

            sumX += pos.x * w;
            sumZ += pos.z * w;
            totalWeight += w;
        }

        return totalWeight <= 0.0 ? null : new Vec3d(sumX / totalWeight, 0.0, sumZ / totalWeight);
    }

    private Vec3d offsetAlongYaw(Vec3d pos, double yaw, double distance) {
        double rad = Math.toRadians(yaw);
        return new Vec3d(pos.x - Math.sin(rad) * distance, pos.y, pos.z + Math.cos(rad) * distance);
    }

    private float smoothYaw(double current, double target, double scaling) {
        return (float) (current + MathHelper.wrapDegrees(target - current) * scaling);
    }

    private double getDecayedInitialWeight() {
        if (this.hasInitialDirection && !(this.startDirectionWeighting.get() <= 0.0)) {
            int decayChunks = this.maxTrailLength.get() * 2;
            double decayFactor = Math.max(0.0, 1.0 - (double)this.chunksFoundSinceStart / decayChunks);
            return this.startDirectionWeighting.get() * decayFactor;
        } else {
            return 0.0;
        }
    }

    private float getActualYaw(float yaw) {
        return (yaw % 360.0F + 360.0F) % 360.0F;
    }

    private double blendYaw(double yaw1, double yaw2, double weight) {
        yaw1 = (yaw1 % 360.0 + 360.0) % 360.0;
        yaw2 = (yaw2 % 360.0 + 360.0) % 360.0;
        double diff = yaw2 - yaw1;
        if (diff > 180.0) {
            diff -= 360.0;
        }

        if (diff < -180.0) {
            diff += 360.0;
        }

        double result = yaw1 + diff * weight;
        return (result % 360.0 + 360.0) % 360.0;
    }

    private void log(String message) {
        this.info(message);
        if (!this.webhookLink.get().isEmpty()) {
            String link = this.webhookLink.get();
            String sender = this.mc.player.getGameProfile().name();
            meteordevelopment.meteorclient.utils.network.MeteorExecutor.execute(() -> Utils.sendWebhook(link, "TrailFollower", message, null, sender));
        }
    }

    public enum DirectionWeighting {
        LEFT,
        NONE,
        RIGHT;
    }

    public enum FollowMode {
        AUTO,
        BARITONE,
        YAWLOCK;
    }

    public enum NetherPathMode {
        AVERAGE,
        OTHER;
    }

    public enum OverworldFlightMode {
        ROCKETS,
        PITCH40,
        OTHER;
    }

    public enum TrailEndBehavior {
        DISABLE,
        FLY_TOWARDS_YAW,
        DISCONNECT;
    }
}
