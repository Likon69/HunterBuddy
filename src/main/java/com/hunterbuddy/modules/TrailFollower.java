package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import com.hunterbuddy.modules.regear.util.Utils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayDeque;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

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
               .description("The amount of MS without a chunk found to trigger circling.")
            .defaultValue(5000.0)
            .min(1000.0)
            .sliderMax(10000.0)
            .build()
      );
   public final Setting<Double> circlingDegPerTick = this.sgAdvanced
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("Circling-degrees-per-tick")
               .description("The amount of degrees to change per tick while circling.")
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
            .sliderMax(60000.0)
            .build()
      );
   public final Setting<Double> maxTrailDeviation = this.sgAdvanced
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("max-trail-deviation")
               .description("Maximum allowed angle (in degrees) from the original trail direction. Helps avoid switching to intersecting trails.")
            .defaultValue(180.0)
            .min(1.0)
            .sliderMax(270.0)
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
   private TrailFollower.FollowMode followMode;
   private boolean followingTrail = false;
   private ArrayDeque<Vec3d> trail = new ArrayDeque<>();
   private ArrayDeque<Vec3d> possibleTrail = new ArrayDeque<>();
   private long lastFoundTrailTime;
   private long lastFoundPossibleTrailTime;
   private double pathDistanceActual = (Double)this.pathDistance.get();
   private boolean started = false;
   private double initialYaw = 0.0;
   private boolean hasInitialDirection = false;
   private int chunksFoundSinceStart = 0;
   private Cache<Long, Byte> seenChunksCache = Caffeine.newBuilder()
      .maximumSize(((Integer)this.chunkCacheLength.get()).intValue())
      .expireAfterWrite(Duration.ofMinutes(5L)).build();
   private double targetYaw;
   private int baritoneSetGoalTicks = 0;
   Vec3d posDebug;

   public TrailFollower() {
      super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "hb-trail-follower", "Automatically follows trails in all dimensions.");
   }

   void resetTrail() {
      this.baritoneSetGoalTicks = 0;
      this.followingTrail = false;
      this.trail = new ArrayDeque<>();
      this.possibleTrail = new ArrayDeque<>();
      this.hasInitialDirection = false;
      this.chunksFoundSinceStart = 0;
   }

   public void onActivate() {
      this.resetTrail();
      XaeroPlus.EVENT_BUS.register(this);
      if (this.started) {
         if (this.mc.player != null && this.mc.world != null) {
            RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
            if ((Boolean)this.oppositeDimension.get()) {
               if (currentDimension.equals(World.END)) {
                  this.info("There is no opposite dimension to the end. Disabling TrailFollower", new Object[0]);
                  this.toggle();
                  return;
               }

               if (currentDimension.equals(World.NETHER)) {
                  this.info("Following overworld trails from the nether is not supported yet, sorry. Disabling TrailFollower", new Object[0]);
                  this.toggle();
                  return;
               }
            }

            if (this.flightMethod.get() != TrailFollower.FollowMode.AUTO) {
               this.followMode = (TrailFollower.FollowMode)this.flightMethod.get();
            } else if (!currentDimension.equals(World.NETHER)) {
               this.followMode = TrailFollower.FollowMode.YAWLOCK;
               this.info("You are in the overworld or end, basic yaw mode will be used.", new Object[0]);
            } else {
               try {
                  Class.forName("baritone.api.BaritoneAPI");
                  this.followMode = TrailFollower.FollowMode.BARITONE;
                  this.info("You are in the nether, baritone mode will be used.", new Object[0]);
               } catch (ClassNotFoundException e) {
                  this.info("Baritone is required to trail follow in the nether. Disabling TrailFollower", new Object[0]);
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
                     if ((Boolean)this.pitch40Firework.get()) {
                        Setting<Boolean> setting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-firework");
                        if (setting != null) {
                           this.info(
                              "Auto Firework enabled, if you want to change the velocity threshold or the firework cooldown check the settings under Pitch40.",
                              new Object[0]
                           );
                           this.oldAutoFireworkValue = (Boolean)setting.get();
                           setting.set(true);
                        }
                     }

                     Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-bound-adjust");
                     if (autoBoundAdjustSetting != null) {
                        this.oldAutoBoundAdjustValue = (Boolean)autoBoundAdjustSetting.get();
                        autoBoundAdjustSetting.set(false);
                     }
                  }
               } else if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.ROCKETS) {
                  RocketFly rocketFly = (RocketFly)Modules.get().get(RocketFly.class);
                  if (!rocketFly.isActive()) {
                     rocketFly.toggle();
                  }
               }
            }

            this.initialYaw = this.getActualYaw(this.mc.player.getYaw());
            this.hasInitialDirection = true;
            Vec3d offset = new Vec3d(
                  Math.sin(-this.mc.player.getYaw() * Math.PI / 180.0), 0.0, Math.cos(-this.mc.player.getYaw() * Math.PI / 180.0)
               )
               .normalize()
               .multiply((Double)this.pathDistance.get());
            Vec3d targetPos = this.mc.player.getEntityPos().add(offset);

            for (int i = 0; i < ((Integer)this.maxTrailLength.get()).intValue() * this.startDirectionWeighting.get(); i++) {
               this.trail.add(targetPos);
            }

            this.targetYaw = this.initialYaw;
         } else {
            this.toggle();
         }
      }
   }

   public void onDeactivate() {
      this.started = false;
      this.seenChunksCache = Caffeine.newBuilder()
         .maximumSize(((Integer)this.chunkCacheLength.get()).intValue())
         .expireAfterWrite(Duration.ofMinutes(5L)).build();
      XaeroPlus.EVENT_BUS.unregister(this);
      this.trail.clear();
      if (this.followMode != null) {
         switch (this.followMode) {
            case BARITONE:
               BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
               break;
            case YAWLOCK:
               if (this.mc.world == null || this.mc.world.getRegistryKey().equals(World.NETHER)) {
                  return;
               }

               if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.ROCKETS) {
                  RocketFly rocketFly = (RocketFly)Modules.get().get(RocketFly.class);
                  if (rocketFly != null) {
                     rocketFly.resetYLock();
                     if (rocketFly.isActive()) {
                        rocketFly.toggle();
                     }
                  }
               } else if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40) {
                  Class<? extends Module> pitch40Util = Pitch40.class;
                  Module pitch40UtilModule = Modules.get().get(pitch40Util);
                  if (pitch40UtilModule != null) {
                     if (pitch40UtilModule.isActive()) {
                        pitch40UtilModule.toggle();
                     }

                     Setting<Boolean> autoFireworkSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-firework");
                     if (autoFireworkSetting != null) {
                        autoFireworkSetting.set(this.oldAutoFireworkValue);
                     }

                     Setting<Boolean> autoBoundAdjustSetting = (Setting<Boolean>) pitch40UtilModule.settings.get("auto-bound-adjust");
                     if (autoBoundAdjustSetting != null) {
                        autoBoundAdjustSetting.set(this.oldAutoBoundAdjustValue);
                     }
                  }
               }
         }
      }
   }

   private void circle() {
      if (this.followMode != TrailFollower.FollowMode.BARITONE) {
         this.mc.player.setYaw(this.getActualYaw((float)(this.mc.player.getYaw() + (Double)this.circlingDegPerTick.get())));
         if (this.mc.player.age % 100 == 0) {
            this.log(
               "Circling to look for new chunks, abandoning trail in "
                  + ((Double)this.trailTimeout.get() - (System.currentTimeMillis() - this.lastFoundTrailTime)) / 1000.0
                  + " seconds."
            );
         }
      }
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (!this.started) {
            this.started = true;
            this.onActivate();
         }

         if (this.followingTrail && System.currentTimeMillis() - this.lastFoundTrailTime > (Double)this.trailTimeout.get()) {
            this.resetTrail();
            this.log("Trail timed out, stopping.");
            switch ((TrailFollower.TrailEndBehavior)this.trailEndBehavior.get()) {
               case DISABLE:
                  this.toggle();
                  break;
               case FLY_TOWARDS_YAW:
                  this.targetYaw = (Double)this.trailEndYaw.get();
                  break;
               case DISCONNECT:
                  this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[TrailFollower] Trail timed out.")));
            }
         }

         if (this.followingTrail && System.currentTimeMillis() - this.lastFoundTrailTime > (Double)this.chunkFoundTimeout.get()) {
            this.circle();
         } else {
            switch (this.followMode) {
               case BARITONE:
                  if (this.baritoneSetGoalTicks > 0) {
                     this.baritoneSetGoalTicks--;
                  } else if (this.baritoneSetGoalTicks == 0) {
                     this.baritoneSetGoalTicks = (Integer)this.baritoneUpdateTicks.get();
                     if (this.mc.world.getRegistryKey().equals(World.NETHER)) {
                        if (!this.trail.isEmpty()) {
                           Vec3d baritoneTarget;
                           if (this.netherPathMode.get() == TrailFollower.NetherPathMode.AVERAGE) {
                              Vec3d averagePos = this.calculateForwardWeightedAverage(this.trail);
                              Vec3d directionVec = averagePos.subtract(this.mc.player.getEntityPos()).normalize();
                              Vec3d predictedPos = this.mc.player.getEntityPos().add(directionVec.multiply(10.0));
                              double calculatedYaw = RotationUtils.getYaw(predictedPos);
                              double decayedWeight = this.getDecayedInitialWeight();
                              if (decayedWeight > 0.01) {
                                 this.targetYaw = this.blendYaw(calculatedYaw, this.initialYaw, decayedWeight);
                              } else {
                                 this.targetYaw = calculatedYaw;
                              }

                              baritoneTarget = Utils.positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                           } else {
                              Vec3d lastPos = this.trail.getLast();
                              baritoneTarget = lastPos;
                           }

                           BaritoneAPI.getProvider()
                              .getPrimaryBaritone()
                              .getCustomGoalProcess()
                              .setGoalAndPath(new GoalXZ((int)baritoneTarget.x, (int)baritoneTarget.z));
                        }
                     } else {
                        Vec3d targetPos = Utils.positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                        BaritoneAPI.getProvider()
                           .getPrimaryBaritone()
                           .getCustomGoalProcess()
                           .setGoalAndPath(new GoalXZ((int)targetPos.x, (int)targetPos.z));
                        this.targetYaw = RotationUtils.getYaw(targetPos);
                     }

                     if ((Boolean)this.autoElytra.get()
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
                     .setYaw(Utils.smoothRotation(this.getActualYaw(this.mc.player.getYaw()), this.targetYaw, (Double)this.rotateScaling.get()));
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.debug.get()) {
         Vec3d targetPos = Utils.positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, 10.0);
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
         RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
         WorldChunk chunk = event.chunk();
         ChunkPos chunkPos = chunk.getPos();
         long chunkLong = chunkPos.toLong();
         if (this.seenChunksCache.getIfPresent(chunkLong) == null) {
            ChunkPos chunkDelta = new ChunkPos(
               chunkPos.x - this.mc.player.getChunkPos().x, chunkPos.z - this.mc.player.getChunkPos().z
            );
            if ((Boolean)this.oppositeDimension.get()) {
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
                  if (System.currentTimeMillis() - this.lastFoundPossibleTrailTime > (Integer)this.chunkConsiderationWindow.get() * 1000) {
                     this.possibleTrail.clear();
                  }

                  if (this.hasInitialDirection) {
                     double chunkAngleToPlayer = RotationUtils.getYaw(pos);
                     double angleDiffFromInitial = Utils.angleDifference(this.initialYaw, chunkAngleToPlayer);
                     if (Math.abs(angleDiffFromInitial) > (Double)this.forwardConeAngle.get()) {
                        return;
                     }
                  }

                  this.possibleTrail.add(pos);
                  this.lastFoundPossibleTrailTime = System.currentTimeMillis();
                  if (this.possibleTrail.size() > (Integer)this.chunksBeforeStarting.get()) {
                     this.log("Trail found, starting to follow.");
                     this.followingTrail = true;
                     this.lastFoundTrailTime = System.currentTimeMillis();
                     this.chunksFoundSinceStart = this.possibleTrail.size();
                     this.trail.addAll(this.possibleTrail);
                     this.possibleTrail.clear();
                  }
               } else {
                  double chunkAngle = RotationUtils.getYaw(pos);
                  double angleDiff = Utils.angleDifference(this.targetYaw, chunkAngle);
                  if (!this.followingTrail || !(Math.abs(angleDiff) > (Double)this.maxTrailDeviation.get())) {
                     this.lastFoundTrailTime = System.currentTimeMillis();

                     while (this.trail.size() >= this.maxTrailLength.get()) {
                        this.trail.pollFirst();
                     }

                     if (angleDiff > 0.0 && angleDiff < 90.0 && this.directionWeighting.get() == TrailFollower.DirectionWeighting.LEFT) {
                        for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                           this.trail.pollFirst();
                           this.trail.add(pos);
                        }

                        this.trail.add(pos);
                     } else if (angleDiff < 0.0 && angleDiff > -90.0 && this.directionWeighting.get() == TrailFollower.DirectionWeighting.RIGHT) {
                        for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                           this.trail.pollFirst();
                           this.trail.add(pos);
                        }

                        this.trail.add(pos);
                     } else {
                        this.trail.add(pos);
                     }

                     this.chunksFoundSinceStart++;
                     if (!this.trail.isEmpty()) {
                        if (this.followMode == TrailFollower.FollowMode.YAWLOCK) {
                           Vec3d averagePos = this.calculateForwardWeightedAverage(this.trail);
                           Vec3d positionVec = averagePos.subtract(this.mc.player.getEntityPos()).normalize();
                           Vec3d targetPos = this.mc.player.getEntityPos().add(positionVec.multiply(10.0));
                           double calculatedYaw = RotationUtils.getYaw(targetPos);
                           double decayedWeight = this.getDecayedInitialWeight();
                           if (decayedWeight > 0.01) {
                              this.targetYaw = this.blendYaw(calculatedYaw, this.initialYaw, decayedWeight);
                           } else {
                              this.targetYaw = calculatedYaw;
                           }
                        } else {
                           Vec3d lastTrailPoint = this.trail.getLast();
                           double calculatedYaw = RotationUtils.getYaw(lastTrailPoint);
                           double decayedWeight = this.getDecayedInitialWeight();
                           if (decayedWeight > 0.01) {
                              this.targetYaw = this.blendYaw(calculatedYaw, this.initialYaw, decayedWeight);
                           } else {
                              this.targetYaw = calculatedYaw;
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
      PaletteNewChunks paletteNewChunks = (PaletteNewChunks)ModuleManager.getModule(PaletteNewChunks.class);
      boolean is119NewChunk = paletteNewChunks.isNewChunk(chunkPos.x, chunkPos.z, currentDimension);
      boolean is112OldChunk = ((OldChunks)ModuleManager.getModule(OldChunks.class)).isOldChunk(chunkPos.x, chunkPos.z, currentDimension);
      boolean isHighlighted = is119NewChunk || paletteNewChunks.isInverseNewChunk(chunkPos.x, chunkPos.z, currentDimension);
      return isHighlighted && (!is119NewChunk && !(Boolean)this.only112.get() || is112OldChunk);
   }

   private Vec3d calculateAveragePosition(ArrayDeque<Vec3d> positions) {
      double sumX = 0.0;
      double sumZ = 0.0;

      for (Vec3d pos : positions) {
         sumX += pos.x;
         sumZ += pos.z;
      }

      return new Vec3d(sumX / positions.size(), 0.0, sumZ / positions.size());
   }

   private Vec3d calculateForwardWeightedAverage(ArrayDeque<Vec3d> positions) {
      if (positions.isEmpty()) {
         return this.mc.player.getEntityPos();
      }

      if ((Double)this.forwardWeightStrength.get() <= 0.01) {
         return this.calculateAveragePosition(positions);
      }

      double sumX = 0.0;
      double sumZ = 0.0;
      double totalWeight = 0.0;
      Vec3d playerPos = this.mc.player.getEntityPos();

      for (Vec3d pos : positions) {
         double chunkYaw = RotationUtils.getYaw(pos);
         double angleDiff = Math.abs(Utils.angleDifference(this.targetYaw, chunkYaw));
         double alignmentFactor = 1.0 - angleDiff / 180.0;
         double weight = 1.0 - (Double)this.forwardWeightStrength.get() + (Double)this.forwardWeightStrength.get() * alignmentFactor;
         double dist = playerPos.distanceTo(pos);
         double distWeight = 1.0 / (1.0 + dist / 256.0);
         double finalWeight = weight * (0.5 + 0.5 * distWeight);
         sumX += pos.x * finalWeight;
         sumZ += pos.z * finalWeight;
         totalWeight += finalWeight;
      }

      return totalWeight <= 0.0 ? this.calculateAveragePosition(positions) : new Vec3d(sumX / totalWeight, 0.0, sumZ / totalWeight);
   }

   private double getDecayedInitialWeight() {
      if (this.hasInitialDirection && !((Double)this.startDirectionWeighting.get() <= 0.0)) {
         int decayChunks = (Integer)this.maxTrailLength.get() * 2;
         double decayFactor = Math.max(0.0, 1.0 - (double)this.chunksFoundSinceStart / decayChunks);
         return (Double)this.startDirectionWeighting.get() * decayFactor;
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
      this.info(message, new Object[0]);
      if (!((String)this.webhookLink.get()).isEmpty()) {
         Utils.sendWebhook((String)this.webhookLink.get(), "hb-trail-follower", message, null, this.mc.player.getGameProfile().name());
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
