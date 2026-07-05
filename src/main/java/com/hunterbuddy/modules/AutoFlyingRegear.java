package com.hunterbuddy.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.PlacementUtils;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;

import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

public class AutoFlyingRegear extends Module {
   private final SettingGroup sgTriggers = this.settings.createGroup("Triggers");
   private final SettingGroup sgPlatform = this.settings.createGroup("Platform");
   private final SettingGroup sgHotbar = this.settings.createGroup("Hotbar Slots");
   private final SettingGroup sgRestock = this.settings.createGroup("Restock");
   private final SettingGroup sgDelays = this.settings.createGroup("Delays");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> minRockets = this.sgTriggers
      .add(
         new Builder().name("min-rockets").description("Minimum rockets in inventory to trigger regear").defaultValue(64)
            .min(0)
            .sliderMax(256)
            .build()
      );
   private final Setting<Integer> minElytras = this.sgTriggers
      .add(
         new Builder().name("min-elytras").description("Minimum number of valid elytras to trigger regear").defaultValue(2)
            .min(1)
            .max(10)
            .build()
      );
   private final Setting<Integer> goalElytras = this.sgTriggers
      .add(
         new Builder().name("goal-elytras").description("Target number of valid elytras after regearing").defaultValue(6)
            .min(2)
            .max(20)
            .build()
      );
   private final Setting<Integer> elytraDurabilityThreshold = this.sgTriggers
      .add(
         new Builder().name("elytra-durability-%").description("Durability percentage threshold for valid elytra")
               .defaultValue(30)
            .min(1)
            .max(99)
            .build()
      );
   private final Setting<Boolean> debugMessages = this.sgTriggers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("debug-messages")
                  .description("Show debug messages in chat")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> createWalls = this.sgPlatform
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("create-walls")
                  .description("Create protective walls around platform")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> encapsule = this.sgPlatform
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("encapsule")
                     .description("Fully enclose with 2-level walls and roof")
                  .defaultValue(true)
               .visible(this.createWalls::get)
            .build()
      );
   private final Setting<Integer> targetYOffset = this.sgPlatform
      .add(
         new Builder().name("target-y-offset")
                  .description("In Overworld/End: blocks below build limit to start platform creation. Ignored in Nether.")
               .defaultValue(10)
            .min(5)
            .max(50)
            .sliderRange(5, 30)
            .build()
      );
   private final Setting<Integer> eChestHotbarSlot = this.sgHotbar
      .add(
         new Builder().name("ender-chest-slot").description("Hotbar slot for ender chest (0-8)").defaultValue(7)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Integer> shulkerHotbarSlot = this.sgHotbar
      .add(
         new Builder().name("shulker-slot").description("Hotbar slot for shulker boxes (0-8)").defaultValue(6)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Integer> obsidianHotbarSlot = this.sgHotbar
      .add(
         new Builder().name("obsidian-slot").description("Hotbar slot for obsidian/blocks (0-8)").defaultValue(5)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Integer> rocketHotbarSlot = this.sgHotbar
      .add(
         new Builder().name("rocket-slot").description("Hotbar slot for firework rockets (0-8)").defaultValue(4)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Boolean> autoReEnable = this.sgRestock
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-re-enable-flight")
                  .description("Automatically re-enable flight modules after restocking")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> swapToChestplate = this.sgRestock
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swap-to-chestplate")
                  .description("Swap to chestplate before dropping down")
               .defaultValue(true)
            .build()
      );
   private final Setting<Integer> placeDelay = this.sgDelays
      .add(
         new Builder().name("place-delay").description("Delay between air-place block placements (ticks)").defaultValue(8)
            .min(0)
            .max(100)
            .sliderMin(1)
            .sliderMax(20)
            .build()
      );
   private final Setting<Integer> clickDelay = this.sgDelays
      .add(
         new Builder().name("click-delay").description("Delay between inventory clicks (ticks)").defaultValue(3)
            .min(0)
            .max(10)
            .build()
      );
   private final Setting<Integer> containerOpenDelay = this.sgDelays
      .add(
         new Builder().name("container-open-delay").description("Delay after opening container (ticks)").defaultValue(10)
            .min(5)
            .max(30)
            .build()
      );
   private final Setting<Integer> breakDelay = this.sgDelays
      .add(
         new Builder().name("break-delay").description("Delay after breaking blocks (ticks)").defaultValue(8)
            .min(0)
            .max(20)
            .build()
      );
   private final Setting<Integer> startupDelay = this.sgDelays
      .add(
         new Builder().name("startup-delay")
                  .description("Ticks to wait in survival mode before checking inventory. Prevents false triggers from queue.")
               .defaultValue(100)
            .min(0)
            .max(600)
            .sliderMin(20)
            .sliderMax(200)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render")
                  .description("Render platform blocks")
               .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> platformColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("platform-color")
               .description("Color of platform blocks")
            .defaultValue(new SettingColor(255, 0, 0, 100))
            .build()
      );
   private AutoFlyingRegear.FlyingRegearState state = AutoFlyingRegear.FlyingRegearState.IDLE;
   private AutoFlyingRegear.FlyingRegearState stateBeforeEating = null;
   private int timer = 0;
   private BlockPos platformCenter = null;
   private final List<BlockPos> placedBlocks = new ArrayList<>();
   private final List<BlockPos> pendingBlocks = new ArrayList<>();
   private int currentBlockIndex = 0;
   private BlockPos shulkerPlacePos = null;
   private BlockPos echestPos = null;
   private boolean processingElytras = true;
   private int transferSlotIndex = 0;
   private int transferStep = 0;
   private ItemStack savedChestplate = ItemStack.EMPTY;
   private final Set<String> processedShulkers = new HashSet<>();
   private int placementAttempts = 0;
   private int shulkerEnderSlot = -1;
   private final int maxPlacementAttempts = 15;
   private int stateTickCounter = 0;
   private AutoFlyingRegear.FlyingRegearState lastState = AutoFlyingRegear.FlyingRegearState.IDLE;
   private final int maxStateTimeout = 600;
   private final Map<String, Module> flightModules = new HashMap<>();
   private final Set<String> disabledModules = new HashSet<>();
   private MlepScaffold mlepScaffold = null;
   private Module mlepMine = null;
   private boolean mlepMineWasActive = false;
   private int scaffoldWaitTicks = 0;
   private float savedYaw = 0.0F;
   private float savedPitch = 0.0F;
   private boolean hadBaritoneGoal = false;
   private String savedBaritoneCommand = null;
   private int startupDelayTicks = 0;
   private boolean startupComplete = false;
   private AutoEat autoEatModule = null;
   private int wallLayer = 0;
   private int wallBuildPhase = 0;
   private BlockPos currentClearingPos = null;
   private int clearingProgress = 0;
   private int shulkerPickupAttempts = 0;
   private boolean shulkerPlacementRetry = false;
   private int cleanupBlockIndex = 0;
   private int cleanupBlockAttempts = 0;
   private List<BlockPos> cleanupBlocks = new ArrayList<>();
   private boolean cleanupInitialized = false;
   private int reEnableStage = 0;
   private final ElytraTakeoff takeoff = new ElytraTakeoff();
   private boolean takeoffInitialized = false;
   private static final double ROTATION_ALIGN_EPS = 3.0;
   private static final double ROTATION_TURN_SPEED = 120.0;
   private static final int TAKEOFF_TIMEOUT_TICKS = 280;

   public AutoFlyingRegear() {
      super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "AutoFlyingRegear", "Automatically creates a platform and restocks rockets/elytras from ender chest");
   }

   public void onActivate() {
      this.state = AutoFlyingRegear.FlyingRegearState.IDLE;
      this.stateBeforeEating = null;
      this.timer = 0;
      this.platformCenter = null;
      this.placedBlocks.clear();
      this.pendingBlocks.clear();
      this.currentBlockIndex = 0;
      this.disabledModules.clear();
      this.shulkerPlacePos = null;
      this.echestPos = null;
      this.processingElytras = true;
      this.transferSlotIndex = 0;
      this.transferStep = 0;
      this.savedChestplate = ItemStack.EMPTY;
      this.placementAttempts = 0;
      this.shulkerEnderSlot = -1;
      this.processedShulkers.clear();
      this.stateTickCounter = 0;
      this.lastState = AutoFlyingRegear.FlyingRegearState.IDLE;
      this.scaffoldWaitTicks = 0;
      this.wallBuildPhase = 0;
      this.savedYaw = 0.0F;
      this.currentClearingPos = null;
      this.clearingProgress = 0;
      this.savedPitch = 0.0F;
      this.hadBaritoneGoal = false;
      this.savedBaritoneCommand = null;
      this.reEnableStage = 0;
      this.takeoff.cancel();
      this.takeoffInitialized = false;
      this.cleanupBlockIndex = 0;
      this.cleanupBlockAttempts = 0;
      this.cleanupBlocks.clear();
      this.cleanupInitialized = false;
      this.shulkerPickupAttempts = 0;
      this.shulkerPlacementRetry = false;
      this.mlepMineWasActive = false;
      this.startupDelayTicks = 0;
      this.startupComplete = false;
      this.mlepScaffold = Modules.get().get(MlepScaffold.class);
      if (this.mlepScaffold == null && (Boolean)this.debugMessages.get()) {
         this.error("MlepScaffold module not found - regear scaffolding will not work!", new Object[0]);
      }
      this.mlepMine = Modules.get().get(MlepMine.class);
      this.autoEatModule = (AutoEat)Modules.get().get(AutoEat.class);
      if (this.mlepMine != null) {
         this.mlepMineWasActive = this.mlepMine.isActive();
      }

      this.flightModules.put("Replenish", Modules.get().get(Replenish.class));
      this.flightModules.put("AreaLoader", Modules.get().get(AreaLoader.class));
      this.flightModules.put("RocketFly", Modules.get().get(RocketFly.class));
      this.flightModules.put("WaypointFollower", Modules.get().get(WaypointFollower.class));
      this.flightModules.put("ElytraBounce", Modules.get().get(ElytraBounce.class));
      this.flightModules.put("Pitch40", Modules.get().get(Pitch40.class));
      this.flightModules.put("TrailFollower", Modules.get().get(TrailFollower.class));
      this.flightModules.put("ElytraRecast", Modules.get().get(ElytraRecast.class));
      this.flightModules.put("ElytraFly", Modules.get().get(ElytraFly.class));
      if ((Boolean)this.debugMessages.get()) {
         this.info("AutoFlyingRegear activated - monitoring inventory", new Object[0]);
      }
   }

   public void onDeactivate() {
      this.reEnableFlightModules();
      if (!this.savedChestplate.isEmpty() && (Boolean)this.swapToChestplate.get()) {
        this.restoreChestplate();
      }

      if (this.mlepScaffold != null && this.mlepScaffold.isActive()) {
         this.mlepScaffold.setRegearMode(false);
         this.mlepScaffold.toggle();
      }

      this.takeoff.cancel();
      this.releaseMovementKeys();
      RotationUtils.getInstance().clearRotations();

      try {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null && baritone.getPathingBehavior().isPathing()) {
            baritone.getPathingBehavior().cancelEverything();
         }
      } catch (Exception var2) {
      }

      this.placedBlocks.clear();
      this.pendingBlocks.clear();
      this.wallLayer = 0;
      this.wallBuildPhase = 0;
      this.cleanupBlocks.clear();
      this.cleanupBlockIndex = 0;
      this.cleanupInitialized = false;
   }

   private boolean isAutoEating() {
      if (this.autoEatModule == null) {
         return false;
      } else {
         return !this.autoEatModule.isActive() ? false : this.autoEatModule.eating;
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.state == AutoFlyingRegear.FlyingRegearState.TAKING_OFF && this.takeoff.isActive()) {
            this.takeoff.tickPre();
         }
         if (this.isAutoEating() && this.state != AutoFlyingRegear.FlyingRegearState.IDLE) {
            if (this.stateBeforeEating == null) {
               this.stateBeforeEating = this.state;
               if ((Boolean)this.debugMessages.get()) {
                  this.info("AutoEat is eating - pausing AutoFlyingRegear operations", new Object[0]);
               }
            }
         } else {
            if (this.stateBeforeEating != null) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("AutoEat finished eating - resuming AutoFlyingRegear", new Object[0]);
               }

               this.stateBeforeEating = null;
            }

            if (this.state != this.lastState) {
               this.lastState = this.state;
               this.stateTickCounter = 0;
            } else {
               this.stateTickCounter++;
            }

            if (this.stateTickCounter > 600) {
               switch (this.state) {
                  case IDLE:
                  case OPENING_ECHEST:
                  case TAKING_SHULKER:
                  case OPENING_SHULKER:
                  case TRANSFERRING_ITEMS:
                  case OPENING_ECHEST_RETURN:
                  case RETURNING_SHULKER:
                  case COMPLETE:
                     break;
                  case SWAP_TO_CHESTPLATE:
                  case DISABLING_MODULES:
                  case CREATING_INITIAL_PLATFORM:
                  case CLEARING_ECHEST_AREA:
                  case ROTATING_FOR_ECHEST:
                  case PLACING_ECHEST:
                  case WAIT_ECHEST_PLACE:
                  case WAIT_SHULKER_TAKEN:
                  case ROTATING_FOR_SHULKER:
                  case PLACING_SHULKER:
                  case WAIT_SHULKER_PLACE:
                  case BREAKING_SHULKER:
                  case WAIT_SHULKER_BREAK:
                  case WAIT_SHULKER_PICKUP:
                  case CHECK_NEXT_SHULKER:
                  case BREAKING_ECHEST:
                  case WAIT_ECHEST_BREAK:
                  case RESTORING_ELYTRA:
                  case CLEANUP:
                  case TAKING_OFF:
                  default:
                     if ((Boolean)this.debugMessages.get()) {
                        this.error("Unexpected state timeout in " + this.state + " after 30 seconds. Force completing...", new Object[0]);
                     }

                     this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
                     this.stateTickCounter = 0;
                     this.timer = 0;
                     return;
                  case CREATING_WALLS:
                  case DROPPING:
                  case CENTERING_ON_PLATFORM:
                  case POSITIONING_FOR_SHULKER:
                     if ((Boolean)this.debugMessages.get()) {
                        this.warning("State timeout in " + this.state + " after 30 seconds. Attempting to continue...", new Object[0]);
                     }

                     IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                     if (baritone != null) {
                        baritone.getPathingBehavior().cancelEverything();
                     }

                     this.releaseMovementKeys();
                     if (this.state == AutoFlyingRegear.FlyingRegearState.CREATING_WALLS) {
                        this.pendingBlocks.clear();
                        this.currentBlockIndex = 0;
                        this.placementAttempts = 0;
                        this.state = AutoFlyingRegear.FlyingRegearState.CLEARING_ECHEST_AREA;
                     } else if (this.state == AutoFlyingRegear.FlyingRegearState.DROPPING) {
                        this.platformCenter = this.mc.player.getBlockPos().down();
                        this.state = AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM;
                     } else if (this.state == AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM) {
                        this.state = AutoFlyingRegear.FlyingRegearState.CREATING_INITIAL_PLATFORM;
                     } else if (this.state == AutoFlyingRegear.FlyingRegearState.POSITIONING_FOR_SHULKER) {
                        this.state = AutoFlyingRegear.FlyingRegearState.ROTATING_FOR_SHULKER;
                     }

                     this.stateTickCounter = 0;
                     this.timer = 5;
                     return;
               }
            }

            if (this.timer > 0) {
               this.timer--;
            } else {
               switch (this.state) {
                  case IDLE:
                     this.handleIdleState();
                     break;
                  case SWAP_TO_CHESTPLATE:
                     this.handleSwapToChestplate();
                     break;
                  case DISABLING_MODULES:
                     this.handleDisablingModules();
                     break;
                  case DROPPING:
                     this.handleDroppingState();
                     break;
                  case CENTERING_ON_PLATFORM:
                     this.handleCenteringOnPlatform();
                     break;
                  case CREATING_INITIAL_PLATFORM:
                     this.handleCreatingInitialPlatform();
                     break;
                  case CREATING_WALLS:
                     this.handleCreatingWalls();
                     break;
                  case CLEARING_ECHEST_AREA:
                     this.handleClearingEchestArea();
                     break;
                  case ROTATING_FOR_ECHEST:
                     this.handleRotatingForEchest();
                     break;
                  case PLACING_ECHEST:
                     this.handlePlacingEchest();
                     break;
                  case WAIT_ECHEST_PLACE:
                     this.handleWaitEchestPlace();
                     break;
                  case OPENING_ECHEST:
                     this.handleOpeningEchest();
                     break;
                  case TAKING_SHULKER:
                     this.handleTakingShulker();
                     break;
                  case WAIT_SHULKER_TAKEN:
                     this.handleWaitShulkerTaken();
                     break;
                  case POSITIONING_FOR_SHULKER:
                     this.handlePositioningForShulker();
                     break;
                  case ROTATING_FOR_SHULKER:
                     this.handleRotatingForShulker();
                     break;
                  case PLACING_SHULKER:
                     this.handlePlacingShulker();
                     break;
                  case WAIT_SHULKER_PLACE:
                     this.handleWaitShulkerPlace();
                     break;
                  case OPENING_SHULKER:
                     this.handleOpeningShulker();
                     break;
                  case TRANSFERRING_ITEMS:
                     this.handleTransferringItems();
                     break;
                  case BREAKING_SHULKER:
                     this.handleBreakingShulker();
                     break;
                  case WAIT_SHULKER_BREAK:
                     this.handleWaitShulkerBreak();
                     break;
                  case WAIT_SHULKER_PICKUP:
                     this.handleWaitShulkerPickup();
                     break;
                  case OPENING_ECHEST_RETURN:
                     this.handleOpeningEchestReturn();
                     break;
                  case RETURNING_SHULKER:
                     this.handleReturningShulker();
                     break;
                  case CHECK_NEXT_SHULKER:
                     this.handleCheckNextShulker();
                     break;
                  case BREAKING_ECHEST:
                     this.handleBreakingEchest();
                     break;
                  case WAIT_ECHEST_BREAK:
                     this.handleWaitEchestBreak();
                     break;
                  case RESTORING_ELYTRA:
                     this.handleRestoringElytra();
                     break;
                  case CLEANUP:
                     this.handleCleanup();
                     break;
                  case TAKING_OFF:
                     this.handleTakingOff();
                     break;
                  case COMPLETE:
                     this.handleComplete();
               }
            }
         }
      }
   }

   @EventHandler
   private void onTickPost(Post event) {
      if (this.mc.player != null && this.mc.world != null && this.state == AutoFlyingRegear.FlyingRegearState.TAKING_OFF && this.takeoff.isActive()) {
         this.takeoff.tickPost();
         if (this.stateTickCounter > TAKEOFF_TIMEOUT_TICKS) {
            this.takeoff.cancel();
            this.finishTakeoffSafely(false, "takeoff timed out");
         }
      }
   }

   private void handleIdleState() {
      if (!this.startupComplete) {
         if (this.mc.interactionManager != null && this.mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL) {
            this.startupDelayTicks++;
            if (this.startupDelayTicks >= (Integer)this.startupDelay.get()) {
               this.startupComplete = true;
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Startup delay complete - now monitoring inventory", new Object[0]);
               }
            }
         } else {
            this.startupDelayTicks = 0;
         }
      } else {
         if (this.shouldTriggerRegear()) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Low on supplies - initiating AutoFlyingRegear sequence", new Object[0]);
            }

            this.savedYaw = this.mc.player.getYaw();
            this.savedPitch = this.mc.player.getPitch();
            if ((Boolean)this.debugMessages.get()) {
               this.info("Saved rotation: yaw=" + this.savedYaw + ", pitch=" + this.savedPitch, new Object[0]);
            }

            try {
               IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
               if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                  if (baritone.getPathingBehavior().getGoal() != null) {
                     String goalString = baritone.getPathingBehavior().getGoal().toString();
                     ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
                     boolean wearingElytra = chestItem.getItem() == Items.ELYTRA;
                     if (goalString.contains("Elytra") || wearingElytra && !this.mc.player.isOnGround()) {
                        this.hadBaritoneGoal = true;
                        this.savedBaritoneCommand = "#elytra";
                        if ((Boolean)this.debugMessages.get()) {
                           this.info("Saved baritone elytra state", new Object[0]);
                        }
                     }
                  }

                  baritone.getPathingBehavior().cancelEverything();
               }
            } catch (Exception e) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Failed to save baritone state: " + e.getMessage(), new Object[0]);
               }
            }

            BlockPos beneath = this.mc.player.getBlockPos().down();
            BlockState blockState = this.mc.world.getBlockState(beneath);
            boolean isNether = this.mc.world.getRegistryKey() == World.NETHER;
            int playerY = (int)Math.floor(this.mc.player.getY());
            if (!blockState.isReplaceable() && blockState.isSolidBlock(this.mc.world, beneath)) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Already on solid ground, skipping drop phase", new Object[0]);
               }

               this.platformCenter = beneath;
               this.state = AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM;
               this.disableAllFlightModules();
               this.timer = 5;
            } else if (!isNether) {
               int buildLimit = this.mc.world.getHeight() + this.mc.world.getBottomY();
               int minHeightForDrop = buildLimit - (Integer)this.targetYOffset.get() - 50;
               if (playerY < minHeightForDrop) {
                  if ((Boolean)this.debugMessages.get()) {
                     this.warning(
                        "Player at Y=" + playerY + " is too low for build limit scaffold (min: " + minHeightForDrop + "). Looking for ground...", new Object[0]
                     );
                  }

                  for (int y = playerY; y > playerY - 20 && y > this.mc.world.getBottomY(); y--) {
                     BlockPos checkPos = new BlockPos(this.mc.player.getBlockPos().getX(), y, this.mc.player.getBlockPos().getZ());
                     BlockState checkState = this.mc.world.getBlockState(checkPos);
                     if (!checkState.isReplaceable() && checkState.isSolidBlock(this.mc.world, checkPos)) {
                        this.platformCenter = checkPos;
                        if ((Boolean)this.debugMessages.get()) {
                           this.info("Found solid ground at Y=" + y + ", using as platform", new Object[0]);
                        }

                        this.state = AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM;
                        this.disableAllFlightModules();
                        this.timer = 5;
                        return;
                     }
                  }

                  if ((Boolean)this.debugMessages.get()) {
                     this.info("No nearby ground found, will scaffold at current position", new Object[0]);
                  }

                  this.disableAllFlightModules();
                  this.beginDropPhase();
               } else {
                  this.beginDropPhase();
               }
            } else {
               this.beginDropPhase();
            }
         }
      }
   }

   private void beginDropPhase() {
      if ((Boolean)this.swapToChestplate.get()) {
         this.state = AutoFlyingRegear.FlyingRegearState.SWAP_TO_CHESTPLATE;
      } else {
         this.state = AutoFlyingRegear.FlyingRegearState.DISABLING_MODULES;
      }
   }

   private void handleSwapToChestplate() {
      ItemStack chestStack = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
      if (chestStack.getItem() == Items.ELYTRA) {
         this.savedChestplate = chestStack.copy();
         FindItemResult chestplate = InvUtils.find(
            itemStack -> {
               Item item = itemStack.getItem();
               return item == Items.NETHERITE_CHESTPLATE
                  || item == Items.DIAMOND_CHESTPLATE
                  || item == Items.IRON_CHESTPLATE
                  || item == Items.GOLDEN_CHESTPLATE
                  || item == Items.CHAINMAIL_CHESTPLATE
                  || item == Items.LEATHER_CHESTPLATE;
            }
         );
         if (chestplate.found()) {
            InvUtils.move().from(chestplate.slot()).toArmor(2);
            if ((Boolean)this.debugMessages.get()) {
               this.info("Swapped to chestplate for safe landing", new Object[0]);
            }

            this.timer = (Integer)this.clickDelay.get();
         } else if ((Boolean)this.debugMessages.get()) {
            this.warning("No chestplate found, proceeding without swap", new Object[0]);
         }
      }

      this.state = AutoFlyingRegear.FlyingRegearState.DISABLING_MODULES;
   }

   private void handleDisablingModules() {
      this.disableAllFlightModules();
      this.prepareScaffoldBlocks();
      this.state = AutoFlyingRegear.FlyingRegearState.DROPPING;
      if ((Boolean)this.debugMessages.get()) {
         this.info("Flight modules disabled - dropping to platform level", new Object[0]);
      }
   }

   private void handleDroppingState() {
      BlockPos beneath = this.mc.player.getBlockPos().down();
      BlockState blockState = this.mc.world.getBlockState(beneath);
      boolean isNether = this.mc.world.getRegistryKey() == World.NETHER;
      if (!blockState.isReplaceable() && blockState.isSolidBlock(this.mc.world, beneath)) {
         this.finishScaffoldLanding(beneath, "Detected solid ground at " + beneath.toShortString() + " - centering");
         return;
      }

      if (this.stateTickCounter > 400) {
         if ((Boolean)this.debugMessages.get()) {
            this.warning("Falling timeout - forcing platform creation", new Object[0]);
         }

         this.finishScaffoldLanding(this.mc.player.getBlockPos(), "Falling timeout - forcing platform");
         return;
      }

      if (this.mc.player.isGliding()) {
         Utils.setPressed(this.mc.options.sneakKey, true);
      }

      if (this.mlepScaffold != null && this.mlepScaffold.isActive()) {
         this.handleActiveScaffold(isNether);
         return;
      }

      int playerY = (int)Math.floor(this.mc.player.getY());
      double yVelocity = this.mc.player.getVelocity().y;
      int buildLimit = 0;
      int activationHeight;
      if (isNether) {
         activationHeight = Integer.MAX_VALUE;
      } else {
         buildLimit = this.mc.world.getHeight() + this.mc.world.getBottomY();
         activationHeight = buildLimit - (Integer)this.targetYOffset.get();
         if (playerY > activationHeight && this.stateTickCounter % 20 == 0 && (Boolean)this.debugMessages.get()) {
            this.info("Falling to platform level... Y=" + playerY + " (target: " + activationHeight + ")", new Object[0]);
         }
      }

      boolean canStartScaffold;
      if (isNether) {
         canStartScaffold = this.hasSpaceBelow() && !this.mc.player.isOnGround() && yVelocity < 0.02;
      } else {
         int minHeightForScaffold = buildLimit - (Integer)this.targetYOffset.get() - 50;
         if (playerY < minHeightForScaffold) {
            if (this.stateTickCounter % 40 == 0 && (Boolean)this.debugMessages.get()) {
               this.info(
                  "Player at Y=" + playerY + " is below valid scaffold range (min: " + minHeightForScaffold + "). Waiting...",
                  new Object[0]
               );
            }

            return;
         }

         canStartScaffold = playerY <= activationHeight
            && this.hasSpaceBelow()
            && !this.mc.player.isOnGround()
            && yVelocity < 0.02;
      }

      if (!canStartScaffold || this.mlepScaffold == null) {
         return;
      }

      if (!this.prepareScaffoldBlocks()) {
         return;
      }

      this.mlepScaffold.setRegearHotbarSlot((Integer)this.obsidianHotbarSlot.get());
      this.mlepScaffold.setRegearMode(true);
      this.mlepScaffold.toggle();
      if ((Boolean)this.debugMessages.get()) {
         this.info("MlepScaffold activated at Y=" + playerY + (isNether ? " (Nether)" : " (below build limit)"), new Object[0]);
      }

      this.scaffoldWaitTicks = 0;
   }

   private void handleActiveScaffold(boolean isNether) {
      this.scaffoldWaitTicks++;
      BlockPos checkPos = this.mc.player.getBlockPos().down();
      int playerY = (int)Math.floor(this.mc.player.getY());

      if (!this.mc.world.getBlockState(checkPos).isReplaceable()) {
         this.finishScaffoldLanding(checkPos, "MlepScaffold placed initial block at " + checkPos.toShortString());
         return;
      }

      if (this.timer == 0 && this.scaffoldWaitTicks % 2 == 0) {
         this.prepareScaffoldBlocks();
         if (this.placeBlockGrim(checkPos)) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("AutoFlyingRegear placed fallback block at " + checkPos.toShortString(), new Object[0]);
            }

            this.timer = 2;
         }
      }

      if (!this.mc.world.getBlockState(checkPos).isReplaceable()) {
         this.finishScaffoldLanding(checkPos, "Landing block placed at " + checkPos.toShortString());
         return;
      }

      if (this.scaffoldWaitTicks % 20 == 0 && (Boolean)this.debugMessages.get()) {
         Vec3d vel = this.mc.player.getVelocity();
         this.info(
            "Scaffolding at Y=" + playerY + ", vy=" + String.format("%.2f", vel.y) + ", wait=" + this.scaffoldWaitTicks,
            new Object[0]
         );
      }

      if (this.scaffoldWaitTicks > 120) {
         this.finishScaffoldLanding(checkPos, "MlepScaffold timeout at Y=" + playerY);
      }
   }

   private void finishScaffoldLanding(BlockPos platform, String debugMessage) {
      if (this.mlepScaffold != null && this.mlepScaffold.isActive()) {
         this.mlepScaffold.setRegearMode(false);
         this.mlepScaffold.toggle();
      }

      RotationUtils.getInstance().clearRotations();
      Utils.setPressed(this.mc.options.sneakKey, false);
      this.platformCenter = platform;
      if ((Boolean)this.debugMessages.get()) {
         this.info(debugMessage, new Object[0]);
      }

      this.state = AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM;
      this.timer = 10;
   }

   private boolean prepareScaffoldBlocks() {
      if (!this.ensureBuildBlocksInHotbar()) {
         return false;
      }

      int hotbarSlot = (Integer)this.obsidianHotbarSlot.get();
      if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != hotbarSlot) {
         ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(hotbarSlot);
      }

      return true;
   }

   private boolean hasSpaceBelow() {
      BlockPos playerPos = this.mc.player.getBlockPos();
      return this.mc.world.getBlockState(playerPos.down()).isReplaceable()
         && this.mc.world.getBlockState(playerPos.down(2)).isReplaceable();
   }

   private void handleCenteringOnPlatform() {
      if (this.stateTickCounter == 1) {
         if (this.mlepScaffold != null && this.mlepScaffold.isActive()) {
            this.mlepScaffold.setRegearMode(false);
            this.mlepScaffold.toggle();
            if ((Boolean)this.debugMessages.get()) {
               this.info("Disabled MlepScaffold for centering", new Object[0]);
            }
         }

         RotationUtils.getInstance().clearRotations();
         Utils.setPressed(this.mc.options.sneakKey, false);

         if ((Boolean)this.debugMessages.get()) {
            this.info("Initiating centering on platform at " + this.platformCenter.toShortString(), new Object[0]);
         }
      }

      Vec3d centerTarget = Vec3d.ofCenter(this.platformCenter);
      Vec3d playerPos = this.mc.player.getEntityPos();
      double deltaX = centerTarget.x - playerPos.x;
      double deltaZ = centerTarget.z - playerPos.z;
      double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      boolean inAir = !this.mc.player.isOnGround() && !this.mc.player.isGliding();
      if (distance < 0.2) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null) {
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCommandManager().execute("cancel");
         }

         this.releaseMovementKeys();
         if ((Boolean)this.debugMessages.get()) {
            this.info("Successfully centered (distance: " + String.format("%.3f", distance) + ")", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.CREATING_INITIAL_PLATFORM;
         this.timer = 10;
      } else if (!inAir && this.stateTickCounter <= 5) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null) {
            baritone.getCommandManager().execute("cancel");
            baritone.getCommandManager()
               .execute(
                  "goto " + this.platformCenter.getX() + " " + (this.platformCenter.getY() + 1) + " " + this.platformCenter.getZ()
               );
            if ((Boolean)this.debugMessages.get()) {
               this.info("Baritone goto center - distance: " + String.format("%.2f", distance), new Object[0]);
            }
         }
      } else {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         boolean baritoneActive = !inAir && baritone != null && baritone.getPathingBehavior().isPathing();
         if (inAir || !baritoneActive && this.stateTickCounter > 20) {
            if ((Boolean)this.debugMessages.get() && this.stateTickCounter % 20 == 0) {
               this.info(
                  inAir ? "In-air centering toward platform (distance: " + String.format("%.2f", distance) + ")"
                     : "Baritone not active, using manual movement",
                  new Object[0]
               );
            }

            float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float playerYaw = this.mc.player.getYaw();
            float yawDiff = MathHelper.wrapDegrees(targetYaw - playerYaw);
            if (Math.abs(yawDiff) > 5.0F) {
               this.mc.player.setYaw(playerYaw + Math.signum(yawDiff) * 5.0F);
            }

            if (inAir) {
               this.releaseMovementKeys();
               Vec3d velocity = this.mc.player.getVelocity();
               double speed = 0.12;
               double vx = Math.signum(deltaX) * Math.min(Math.abs(deltaX), speed);
               double vz = Math.signum(deltaZ) * Math.min(Math.abs(deltaZ), speed);
               this.mc.player.setVelocity(vx, velocity.y, vz);
            } else if (Math.abs(yawDiff) < 30.0F) {
               if (distance > 0.5) {
                  Utils.setPressed(this.mc.options.forwardKey, true);
               } else if (distance > 0.2) {
                  Utils.setPressed(this.mc.options.forwardKey, this.stateTickCounter % 3 == 0);
               } else {
                  Utils.setPressed(this.mc.options.forwardKey, false);
               }

               Utils.setPressed(this.mc.options.sneakKey, distance < 1.0);
            }
         }

         if (this.stateTickCounter > 200) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Centering timeout, proceeding anyway", new Object[0]);
            }

            if (baritone != null) {
               baritone.getCommandManager().execute("cancel");
            }

            this.releaseMovementKeys();
            this.state = AutoFlyingRegear.FlyingRegearState.CREATING_INITIAL_PLATFORM;
            this.timer = 10;
         }
      }
   }

   private void handleCreatingInitialPlatform() {
      if (this.stateTickCounter == 1) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Starting 2x2 platform construction at " + this.platformCenter.toShortString(), new Object[0]);
         }

         this.pendingBlocks.clear();
         this.placedBlocks.clear();
         this.currentBlockIndex = 0;
      }

      if (this.pendingBlocks.isEmpty() && this.timer == 0) {
         BlockPos[] platformBlocks = new BlockPos[]{
            this.platformCenter,
            this.platformCenter.add(1, 0, 0),
            this.platformCenter.add(0, 0, 1),
            this.platformCenter.add(1, 0, 1)
         };

         for (BlockPos pos : platformBlocks) {
            BlockState state = this.mc.world.getBlockState(pos);
            if (!state.isReplaceable() && !state.isAir()) {
               this.placedBlocks.add(pos.toImmutable());
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Block already exists at " + pos.toShortString(), new Object[0]);
               }
            } else {
               this.pendingBlocks.add(pos.toImmutable());
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Need to place block at " + pos.toShortString(), new Object[0]);
               }
            }
         }

         this.currentBlockIndex = 0;
         if (this.pendingBlocks.isEmpty()) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("2x2 platform already complete", new Object[0]);
            }

            if (this.mlepScaffold != null && this.mlepScaffold.isActive()) {
               this.mlepScaffold.toggle();
            }

            this.state = this.createWalls.get() ? AutoFlyingRegear.FlyingRegearState.CREATING_WALLS : AutoFlyingRegear.FlyingRegearState.CLEARING_ECHEST_AREA;
            this.timer = (Integer)this.placeDelay.get();
         } else {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Need to place " + this.pendingBlocks.size() + " blocks for 2x2 platform", new Object[0]);
            }

            this.timer = (Integer)this.placeDelay.get();
         }
      } else if (this.timer <= 0) {
         if (this.currentBlockIndex < this.pendingBlocks.size()) {
            BlockPos pos = this.pendingBlocks.get(this.currentBlockIndex);
            BlockState currentState = this.mc.world.getBlockState(pos);
            if (!currentState.isReplaceable() && !currentState.isAir()) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Block placed at " + pos.toShortString(), new Object[0]);
               }

               if (!this.placedBlocks.contains(pos.toImmutable())) {
                  this.placedBlocks.add(pos.toImmutable());
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.timer = Math.max((Integer)this.placeDelay.get() / 4, 2);
               return;
            }

            if (this.placementAttempts >= 6) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Failed to place block after 6 attempts at " + pos.toShortString() + ", skipping", new Object[0]);
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.timer = (Integer)this.placeDelay.get() * 2;
               return;
            }

            if (this.placeBlockGrim(pos)) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info(
                     "Placing platform block "
                        + (this.currentBlockIndex + 1)
                        + "/"
                        + this.pendingBlocks.size()
                        + " at "
                        + pos.toShortString()
                        + " (attempt "
                        + (this.placementAttempts + 1)
                        + ")",
                     new Object[0]
                  );
               }

               this.placementAttempts++;
               this.timer = (Integer)this.placeDelay.get();
            } else {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Failed to send placement packet, retrying...", new Object[0]);
               }

               this.placementAttempts++;
               this.timer = (Integer)this.placeDelay.get() * 2;
            }
         } else {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Verifying 2x2 platform completion...", new Object[0]);
            }

            BlockPos[] requiredBlocks = new BlockPos[]{
               this.platformCenter,
               this.platformCenter.add(1, 0, 0),
               this.platformCenter.add(0, 0, 1),
               this.platformCenter.add(1, 0, 1)
            };
            boolean allPlaced = true;

            for (BlockPos pos : requiredBlocks) {
               if (this.mc.world.getBlockState(pos).isReplaceable() || this.mc.world.getBlockState(pos).isAir()) {
                  if ((Boolean)this.debugMessages.get()) {
                     this.warning("Missing block at " + pos.toShortString(), new Object[0]);
                  }

                  allPlaced = false;
               }
            }

            if (allPlaced) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("2x2 platform verified complete", new Object[0]);
               }

               if (this.mlepScaffold != null && this.mlepScaffold.isActive()) {
                  this.mlepScaffold.toggle();
               }

               this.pendingBlocks.clear();
               this.currentBlockIndex = 0;
               Vec3d centerTarget = Vec3d.ofCenter(this.platformCenter);
               Vec3d playerPos = this.mc.player.getEntityPos();
               double distance = Math.sqrt(
                  Math.pow(centerTarget.x - playerPos.x, 2.0) + Math.pow(centerTarget.z - playerPos.z, 2.0)
               );
               if (distance > 0.5 && (Boolean)this.createWalls.get()) {
                  if ((Boolean)this.debugMessages.get()) {
                     this.info("Re-centering before walls (distance: " + String.format("%.2f", distance) + ")", new Object[0]);
                  }

                  this.state = AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM;
                  this.timer = 0;
               } else {
                  this.state = this.createWalls.get() ? AutoFlyingRegear.FlyingRegearState.CREATING_WALLS : AutoFlyingRegear.FlyingRegearState.CLEARING_ECHEST_AREA;
                  this.timer = (Integer)this.placeDelay.get();
               }
            } else {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Platform incomplete, restarting...", new Object[0]);
               }

               this.pendingBlocks.clear();
               this.currentBlockIndex = 0;
               this.timer = (Integer)this.placeDelay.get();
            }
         }
      }
   }

   private void handleCreatingWalls() {
      if (this.stateTickCounter == 1) {
         Vec3d centerTarget = Vec3d.ofCenter(this.platformCenter);
         Vec3d playerPos = this.mc.player.getEntityPos();
         double distance = Math.sqrt(
            Math.pow(centerTarget.x - playerPos.x, 2.0) + Math.pow(centerTarget.z - playerPos.z, 2.0)
         );
         if (distance > 0.5) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Not centered for wall construction, re-centering", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM;
            this.timer = 0;
            return;
         }

         if ((Boolean)this.debugMessages.get()) {
            this.info("Starting wall construction" + (this.encapsule.get() ? " (Encapsule mode)" : ""), new Object[0]);
         }

         this.pendingBlocks.clear();
         this.currentBlockIndex = 0;
         this.wallLayer = 0;
         this.wallBuildPhase = 0;
      }

      if (this.pendingBlocks.isEmpty() && this.timer == 0) {
         this.pendingBlocks.addAll(this.collectMissingWallPhaseBlocks());
         if (this.pendingBlocks.isEmpty()) {
            if (this.advanceWallBuildPhase()) {
               return;
            }

            this.state = AutoFlyingRegear.FlyingRegearState.CLEARING_ECHEST_AREA;
            this.timer = (Integer)this.placeDelay.get();
            return;
         }

         this.currentBlockIndex = 0;
         if ((Boolean)this.debugMessages.get()) {
            this.info(
               "Placing "
                  + this.pendingBlocks.size()
                  + " blocks for "
                  + this.getWallPhaseName()
                  + " (grim air-place)",
               new Object[0]
            );
         }

         this.timer = (Integer)this.placeDelay.get();
      } else if (this.timer <= 0) {
         if (this.currentBlockIndex < this.pendingBlocks.size()) {
            BlockPos pos = this.pendingBlocks.get(this.currentBlockIndex);
            BlockState currentState = this.mc.world.getBlockState(pos);
            if (!currentState.isReplaceable() && !currentState.isAir()) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Block placed (" + this.getWallPhaseName() + ") at " + pos.toShortString(), new Object[0]);
               }

               if (!this.placedBlocks.contains(pos.toImmutable())) {
                  this.placedBlocks.add(pos.toImmutable());
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.timer = (Integer)this.placeDelay.get();
               return;
            }

            if (this.placementAttempts >= 10) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Failed to place block after 10 attempts at " + pos.toShortString() + ", skipping", new Object[0]);
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.timer = (Integer)this.placeDelay.get() * 2;
               return;
            }

            if (this.placeStructureBlock(pos)) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info(
                     "Placing "
                        + this.getWallPhaseName()
                        + " block "
                        + (this.currentBlockIndex + 1)
                        + "/"
                        + this.pendingBlocks.size()
                        + " at "
                        + pos.toShortString()
                        + " (attempt "
                        + (this.placementAttempts + 1)
                        + ")",
                     new Object[0]
                  );
               }

               this.placementAttempts++;
               this.timer = (Integer)this.placeDelay.get();
            } else {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("No valid placement target for " + pos.toShortString() + ", retrying...", new Object[0]);
               }

               this.placementAttempts++;
               this.timer = (Integer)this.placeDelay.get() * 2;
            }
         } else {
            this.placementAttempts = 0;
            if (this.advanceWallBuildPhase()) {
               return;
            }

            if ((Boolean)this.debugMessages.get()) {
               this.info(this.encapsule.get() ? "Encapsule construction complete" : "Wall construction complete", new Object[0]);
            }

            this.pendingBlocks.clear();
            this.currentBlockIndex = 0;
            this.state = AutoFlyingRegear.FlyingRegearState.CLEARING_ECHEST_AREA;
            this.timer = (Integer)this.placeDelay.get();
         }
      }
   }

   private BlockPos[] getWallRingPositions(int yOffset) {
      return new BlockPos[]{
         this.platformCenter.add(0, yOffset, 2),
         this.platformCenter.add(1, yOffset, 2),
         this.platformCenter.add(-1, yOffset, 0),
         this.platformCenter.add(-1, yOffset, 1),
         this.platformCenter.add(0, yOffset, -1),
         this.platformCenter.add(1, yOffset, -1),
         this.platformCenter.add(2, yOffset, 0),
         this.platformCenter.add(2, yOffset, 1)
      };
   }

   private BlockPos[] getRoofPositions() {
      return new BlockPos[]{
         this.platformCenter.add(0, 3, 0),
         this.platformCenter.add(1, 3, 0),
         this.platformCenter.add(0, 3, 1),
         this.platformCenter.add(1, 3, 1)
      };
   }

   private List<BlockPos> collectMissingBlocks(BlockPos[] positions) {
      List<BlockPos> missing = new ArrayList<>();

      for (BlockPos pos : positions) {
         BlockState state = this.mc.world.getBlockState(pos);
         if (state.isReplaceable() || state.isAir()) {
            missing.add(pos.toImmutable());
         }
      }

      return missing;
   }

   private List<BlockPos> collectMissingWallPhaseBlocks() {
      return switch (this.wallBuildPhase) {
         case 0 -> this.collectMissingBlocks(this.getWallRingPositions(1));
         case 1 -> this.collectMissingBlocks(this.getWallRingPositions(2));
         case 2 -> this.collectMissingBlocks(this.getRoofPositions());
         default -> new ArrayList<>();
      };
   }

   private String getWallPhaseName() {
      return switch (this.wallBuildPhase) {
         case 0 -> "wall layer 1";
         case 1 -> "wall layer 2";
         case 2 -> "roof";
         default -> "structure";
      };
   }

   private int getMaxWallBuildPhase() {
      if (!(Boolean)this.encapsule.get()) {
         return 0;
      }

      return 2;
   }

   private boolean advanceWallBuildPhase() {
      if (this.wallBuildPhase < this.getMaxWallBuildPhase()) {
         this.wallBuildPhase++;
         this.wallLayer = this.wallBuildPhase;
         this.pendingBlocks.clear();
         this.currentBlockIndex = 0;
         this.placementAttempts = 0;
         this.stateTickCounter = 0;
         if ((Boolean)this.debugMessages.get()) {
            this.info("Moving to " + this.getWallPhaseName(), new Object[0]);
         }

         this.timer = (Integer)this.placeDelay.get();
         return true;
      }

      return false;
   }

   private void handleClearingEchestArea() {
      BlockPos[] clearPositions = new BlockPos[]{
         this.platformCenter.add(0, 1, 0),
         this.platformCenter.add(1, 1, 0),
         this.platformCenter.add(0, 1, 1),
         this.platformCenter.add(1, 1, 1)
      };
      if (this.currentClearingPos != null) {
         BlockState blockState = this.mc.world.getBlockState(this.currentClearingPos);
         if (!blockState.isAir() && !blockState.isReplaceable() && blockState.getBlock() != Blocks.ENDER_CHEST) {
            Vec3d targetVec = Vec3d.ofCenter(this.currentClearingPos);
            float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), targetVec);
            this.mc.player.setYaw(rotations[0]);
            this.mc.player.setPitch(rotations[1]);
            if (this.clearingProgress == 0) {
               this.mc.interactionManager.attackBlock(this.currentClearingPos, Direction.UP);
               this.mc.player.swingHand(Hand.MAIN_HAND);
               this.clearingProgress++;
            } else {
               this.mc.interactionManager.updateBlockBreakingProgress(this.currentClearingPos, Direction.UP);
               this.mc.player.swingHand(Hand.MAIN_HAND);
               this.clearingProgress++;
            }

            if (this.clearingProgress > 600) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Block clearing timeout after 30 seconds at " + this.currentClearingPos.toShortString(), new Object[0]);
               }

               this.currentClearingPos = null;
               this.clearingProgress = 0;
               this.timer = 2;
            }

            this.timer = 0;
         } else {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Block cleared at " + this.currentClearingPos.toShortString(), new Object[0]);
            }

            this.currentClearingPos = null;
            this.clearingProgress = 0;
            this.timer = 2;
         }
      } else {
         for (BlockPos pos : clearPositions) {
            BlockState blockState = this.mc.world.getBlockState(pos);
            if (!blockState.isAir() && !blockState.isReplaceable() && blockState.getBlock() != Blocks.ENDER_CHEST) {
               this.currentClearingPos = pos;
               this.clearingProgress = 0;
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Clearing " + blockState.getBlock().getName().getString() + " at " + pos.toShortString(), new Object[0]);
               }

               return;
            }
         }

         if ((Boolean)this.debugMessages.get()) {
            this.info("2x2 area cleared for ender chest placement", new Object[0]);
         }

         this.currentClearingPos = null;
         this.clearingProgress = 0;
         this.state = AutoFlyingRegear.FlyingRegearState.ROTATING_FOR_ECHEST;
         this.timer = (Integer)this.placeDelay.get();
      }
   }

   private int getBlockBreakTime(BlockState blockState) {
      float hardness = blockState.getHardness(this.mc.world, null);
      if (hardness < 0.0F) {
         return 100;
      } else if (hardness == 0.0F) {
         return 1;
      } else if (hardness < 2.0F) {
         return 10;
      } else if (hardness < 5.0F) {
         return 20;
      } else if (hardness < 10.0F) {
         return 40;
      } else if (hardness < 20.0F) {
         return 60;
      } else {
         return hardness < 50.0F ? 100 : 200;
      }
   }

   private void handleRotatingForEchest() {
      this.echestPos = this.platformCenter.add(1, 1, 1);
      if (this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Ender chest already present", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
         this.timer = (Integer)this.containerOpenDelay.get();
      } else {
         if (this.stateTickCounter == 1) {
            Vec3d targetCenter = Vec3d.ofCenter(this.platformCenter);
            Vec3d playerPos = this.mc.player.getEntityPos();
            double distance = Math.sqrt(
               Math.pow(targetCenter.x - playerPos.x, 2.0) + Math.pow(targetCenter.z - playerPos.z, 2.0)
            );
            if (distance > 0.5) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Player drifted from center before placing enderchest, re-centering", new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM;
               this.timer = 0;
               return;
            }
         }

         if (this.stateTickCounter > 40) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Rotation timeout, placing anyway", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.PLACING_ECHEST;
            this.timer = 2;
         } else {
            BlockHitResult hit = this.resolvePlaceHit(this.echestPos);
            if (hit != null) {
               this.rotateTowards(hit.getPos());
            }

            if (this.isRotationReady()) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Rotation complete for ender chest", new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.PLACING_ECHEST;
               this.timer = 2;
            }
         }
      }
   }

   private void handlePlacingEchest() {
      if (this.placementAttempts > 15) {
         if ((Boolean)this.debugMessages.get()) {
            this.error("Failed to place ender chest after 15 attempts", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.IDLE;
         this.placementAttempts = 0;
      } else {
         this.mc.options.sneakKey.setPressed(false);
         int targetHotbarSlot = (Integer)this.eChestHotbarSlot.get();
         ItemStack currentStack = this.mc.player.getInventory().getStack(targetHotbarSlot);
         if (currentStack.getItem() != Items.ENDER_CHEST) {
            int eChestSlot = InvUtils.find(new Item[]{Items.ENDER_CHEST}).slot();
            if (eChestSlot == -1) {
               if ((Boolean)this.debugMessages.get()) {
                  this.error("No ender chest found in inventory!", new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.IDLE;
            } else {
               InvUtils.move().from(eChestSlot).to(targetHotbarSlot);
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Moving ender chest to hotbar slot " + targetHotbarSlot, new Object[0]);
               }

               this.timer = (Integer)this.clickDelay.get();
            }
         } else {
            if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != targetHotbarSlot) {
               ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(targetHotbarSlot);
            }

            BlockPos groundPos = this.echestPos.down();
            if (!this.mc.world.getBlockState(groundPos).isSolidBlock(this.mc.world, groundPos)) {
               if ((Boolean)this.debugMessages.get()) {
                  this.error("No solid ground for ender chest at " + this.echestPos, new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.IDLE;
            } else if (this.placeBlockAtHotbar(this.echestPos, (Integer)this.eChestHotbarSlot.get())) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Placing ender chest", new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.WAIT_ECHEST_PLACE;
               this.timer = (Integer)this.containerOpenDelay.get();
               this.placementAttempts++;
            } else if ((Boolean)this.debugMessages.get()) {
               this.warning("Failed to place ender chest, retrying...", new Object[0]);
               this.timer = (Integer)this.placeDelay.get();
            }
         }
      }
   }

   private void handleWaitEchestPlace() {
      if (this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Ender chest placed successfully", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
         this.timer = (Integer)this.containerOpenDelay.get();
         this.placementAttempts = 0;
      } else if (this.placementAttempts > 15) {
         if ((Boolean)this.debugMessages.get()) {
            this.error("Ender chest failed to place after 15 attempts", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.IDLE;
         this.placementAttempts = 0;
      } else {
         if ((Boolean)this.debugMessages.get()) {
            this.warning("Ender chest not detected, retrying placement...", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.PLACING_ECHEST;
         this.timer = (Integer)this.placeDelay.get();
      }
   }

   private void handleOpeningEchest() {
      BlockPos standPos = this.echestPos.add(-1, -1, 0);
      Vec3d targetCenter = Vec3d.ofCenter(standPos.up());
      Vec3d playerPos = this.mc.player.getEntityPos();
      double distance = playerPos.distanceTo(targetCenter);
      if (distance > 0.3) {
         if (this.stateTickCounter == 1) {
            try {
               IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
               if (baritone != null) {
                  baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(standPos.up()));
                  if ((Boolean)this.debugMessages.get()) {
                     this.info("Walking to ender chest", new Object[0]);
                  }
               }
            } catch (Exception e) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Failed to use baritone for positioning: " + e.getMessage(), new Object[0]);
               }
            }
         }

         if (this.stateTickCounter < 40) {
            return;
         }
      }

      try {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null && baritone.getPathingBehavior().isPathing()) {
            baritone.getPathingBehavior().cancelEverything();
         }
      } catch (Exception var19) {
      }

      this.mc.options.sneakKey.setPressed(false);
      Vec3d ecCenter = Vec3d.ofCenter(this.echestPos);
      BlockHitResult openHit = new BlockHitResult(ecCenter, Direction.UP, this.echestPos, false);
      if (!this.interactBlockWhenAligned(ecCenter, openHit)) {
         return;
      }

      this.state = AutoFlyingRegear.FlyingRegearState.TAKING_SHULKER;
      this.transferStep = 0;
      this.timer = (Integer)this.containerOpenDelay.get();
      if ((Boolean)this.debugMessages.get()) {
         this.info("Opening ender chest", new Object[0]);
      }
   }

   private void handleTakingShulker() {
      if (this.mc.currentScreen instanceof GenericContainerScreen screen) {
         GenericContainerScreenHandler var16 = (GenericContainerScreenHandler)screen.getScreenHandler();
         String targetType = this.processingElytras ? "elytra" : "rocket";
         int syncId = var16.syncId;
         if (this.transferStep == 0) {
            this.shulkerEnderSlot = -1;

            for (int slot = 0; slot < var16.getRows() * 9; slot++) {
               ItemStack stack = var16.getSlot(slot).getStack();
               if (this.isShulkerBox(stack.getItem())) {
                  ContainerComponent container = (ContainerComponent)stack.get(DataComponentTypes.CONTAINER);
                  if (container != null) {
                     boolean hasTargetItem = false;

                     for (ItemStack contentStack : container.iterateNonEmpty()) {
                        Item item = contentStack.getItem();
                        if (this.processingElytras && item == Items.ELYTRA) {
                           int maxDurability = contentStack.getMaxDamage();
                           int currentDurability = maxDurability - contentStack.getDamage();
                           double percent = (double)currentDurability / maxDurability * 100.0;
                           if (percent >= ((Integer)this.elytraDurabilityThreshold.get()).intValue()) {
                              hasTargetItem = true;
                              break;
                           }
                        } else if (!this.processingElytras && item == Items.FIREWORK_ROCKET) {
                           hasTargetItem = true;
                           break;
                        }
                     }

                     if (hasTargetItem) {
                        String shulkerId = targetType + "_slot_" + slot;
                        if (!this.processedShulkers.contains(shulkerId)) {
                           this.shulkerEnderSlot = slot;
                           this.processedShulkers.add(shulkerId);
                           if ((Boolean)this.debugMessages.get()) {
                              this.info("Found " + targetType + " shulker in ender chest slot " + slot, new Object[0]);
                           }
                           break;
                        }

                        if ((Boolean)this.debugMessages.get()) {
                           this.info("Skipping already processed " + targetType + " shulker at slot " + slot, new Object[0]);
                        }
                     }
                  }
               }
            }

            if (this.shulkerEnderSlot == -1) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("No more unprocessed " + targetType + " shulkers found in ender chest", new Object[0]);
               }

               this.mc.player.closeHandledScreen();
               if (this.processingElytras) {
                  this.processingElytras = false;
                  this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
               } else {
                  this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
               }

               this.timer = (Integer)this.containerOpenDelay.get();
               return;
            }

            this.mc.interactionManager.clickSlot(syncId, this.shulkerEnderSlot, (Integer)this.shulkerHotbarSlot.get(), SlotActionType.SWAP, this.mc.player);
            this.timer = (Integer)this.clickDelay.get() * 2;
            this.transferStep = 1;
         } else if (this.transferStep == 1) {
            ItemStack hotbarStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
            if (!this.isShulkerBox(hotbarStack.getItem())) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Shulker swap failed, retrying...", new Object[0]);
               }

               this.transferStep = 0;
               this.timer = (Integer)this.clickDelay.get();
               return;
            }

            if ((Boolean)this.debugMessages.get()) {
               this.info("Took " + targetType + " shulker from ender chest", new Object[0]);
            }

            this.mc.player.closeHandledScreen();
            this.state = AutoFlyingRegear.FlyingRegearState.WAIT_SHULKER_TAKEN;
            this.timer = (Integer)this.clickDelay.get();
         }
      } else {
         if (this.stateTickCounter > 40) {
            if ((Boolean)this.debugMessages.get()) {
               this.error("Failed to open ender chest", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
            this.timer = (Integer)this.containerOpenDelay.get();
         }
      }
   }

   private void handleWaitShulkerTaken() {
      if (this.mc.currentScreen != null) {
         this.mc.player.closeHandledScreen();
      }

      ItemStack hotbarStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
      if (!this.isShulkerBox(hotbarStack.getItem())) {
         if ((Boolean)this.debugMessages.get()) {
            this.error("Shulker not in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
         this.timer = (Integer)this.containerOpenDelay.get();
      } else {
         String targetType = this.processingElytras ? "elytra" : "rocket";
         if ((Boolean)this.debugMessages.get()) {
            this.info(targetType + " shulker confirmed in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.POSITIONING_FOR_SHULKER;
         this.timer = 0;
         this.placementAttempts = 0;
      }
   }

   private void handlePositioningForShulker() {
      if (this.stateTickCounter == 1) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Positioning for shulker placement", new Object[0]);
         }

         if (this.mlepMine != null && this.mlepMine.isActive()) {
            this.mlepMine.toggle();
            if ((Boolean)this.debugMessages.get()) {
               this.info("Disabled MlepMine for shulker operations", new Object[0]);
            }
         }
      }

      Vec3d targetCenter = Vec3d.ofCenter(this.platformCenter);
      Vec3d playerPos = this.mc.player.getEntityPos();
      double deltaX = targetCenter.x - playerPos.x;
      double deltaZ = targetCenter.z - playerPos.z;
      double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      if (horizontalDistance < 0.2) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null) {
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCommandManager().execute("cancel");
         }

         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         if ((Boolean)this.debugMessages.get()) {
            this.info("Centered for shulker (distance: " + String.format("%.3f", horizontalDistance) + ")", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.ROTATING_FOR_SHULKER;
         this.timer = 10;
      } else if (this.stateTickCounter <= 5) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null) {
            baritone.getCommandManager().execute("cancel");
            baritone.getCommandManager()
               .execute(
                  "goto " + this.platformCenter.getX() + " " + (this.platformCenter.getY() + 1) + " " + this.platformCenter.getZ()
               );
            if ((Boolean)this.debugMessages.get()) {
               this.info("Baritone goto for shulker - distance: " + String.format("%.2f", horizontalDistance), new Object[0]);
            }
         }
      } else {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         boolean baritoneActive = baritone != null && baritone.getPathingBehavior().isPathing();
         if (!baritoneActive && this.stateTickCounter > 20) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Using manual movement for shulker positioning", new Object[0]);
            }

            float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float playerYaw = this.mc.player.getYaw();
            float yawDiff = MathHelper.wrapDegrees(targetYaw - playerYaw);
            if (Math.abs(yawDiff) > 5.0F) {
               this.mc.player.setYaw(playerYaw + Math.signum(yawDiff) * 5.0F);
            }

            if (Math.abs(yawDiff) < 30.0F) {
               if (horizontalDistance > 0.5) {
                  this.mc.options.forwardKey.setPressed(true);
               } else if (horizontalDistance > 0.2) {
                  this.mc.options.forwardKey.setPressed(this.stateTickCounter % 3 == 0);
               }
            }

            this.mc.options.sneakKey.setPressed(horizontalDistance < 1.0);
         }

         if (this.stateTickCounter > 150) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Shulker positioning timeout", new Object[0]);
            }

            if (baritone != null) {
               baritone.getCommandManager().execute("cancel");
            }

            this.mc.options.forwardKey.setPressed(false);
            this.mc.options.sneakKey.setPressed(false);
            this.state = AutoFlyingRegear.FlyingRegearState.ROTATING_FOR_SHULKER;
            this.timer = 10;
         }
      }
   }

   private void handleRotatingForShulker() {
      this.shulkerPlacePos = this.platformCenter.add(1, 1, 0);
      BlockState shulkerPosState = this.mc.world.getBlockState(this.shulkerPlacePos);
      if (!shulkerPosState.isAir() && !shulkerPosState.isReplaceable() && !(shulkerPosState.getBlock() instanceof ShulkerBoxBlock)) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Block at shulker position needs to be cleared: " + shulkerPosState.getBlock().getName().getString(), new Object[0]);
         }

         Vec3d blockCenter = Vec3d.ofCenter(this.shulkerPlacePos);
         if (!this.breakBlockWhenAligned(this.shulkerPlacePos, Direction.UP, blockCenter, this.stateTickCounter == 1)) {
            return;
         }

         if (this.stateTickCounter > 300 && (Boolean)this.debugMessages.get()) {
            this.warning("Block clearing timeout at shulker position", new Object[0]);
         }
      } else if (this.mc.world.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock) {
         if (!this.isShulkerUpright(this.shulkerPlacePos)) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Sideways shulker detected, breaking to re-place upright", new Object[0]);
            }

            this.shulkerPlacementRetry = true;
            this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
            this.timer = 2;
         } else {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Shulker already present", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.OPENING_SHULKER;
            this.timer = (Integer)this.containerOpenDelay.get();
         }
      } else if (this.stateTickCounter > 40) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Rotation timeout, placing anyway", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.PLACING_SHULKER;
         this.timer = 2;
      } else {
         BlockHitResult hit = this.resolveShulkerPlaceHit(this.shulkerPlacePos);
         if (hit != null) {
            this.rotateTowards(hit.getPos());
         }

         if (this.isRotationReady()) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Rotation complete for shulker", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.PLACING_SHULKER;
            this.timer = 2;
         }
      }
   }

   private void handlePlacingShulker() {
      if (this.placementAttempts > 15) {
         if ((Boolean)this.debugMessages.get()) {
            this.error("Failed to place shulker after 15 attempts", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
         this.placementAttempts = 0;
      } else {
         if (this.mlepMine != null && this.mlepMine.isActive()) {
            this.mlepMine.toggle();
            if ((Boolean)this.debugMessages.get()) {
               this.info("Disabled MlepMine to prevent shulker mining", new Object[0]);
            }
         }

         this.mc.options.sneakKey.setPressed(false);
         ItemStack shulkerStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
         if (!this.isShulkerBox(shulkerStack.getItem())) {
            if ((Boolean)this.debugMessages.get()) {
               this.error("No shulker in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
            this.placementAttempts = 0;
         } else {
            if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != (Integer)this.shulkerHotbarSlot.get()) {
               ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot((Integer)this.shulkerHotbarSlot.get());
            }

            BlockPos groundPos = this.shulkerPlacePos.down();
            BlockState shulkerPosState = this.mc.world.getBlockState(this.shulkerPlacePos);
            if (!shulkerPosState.isAir() && !shulkerPosState.isReplaceable()) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Shulker position blocked, going back to rotation/clearing", new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.ROTATING_FOR_SHULKER;
               this.timer = 2;
            } else if (!this.mc.world.getBlockState(groundPos).isSolidBlock(this.mc.world, groundPos)) {
               if ((Boolean)this.debugMessages.get()) {
                  this.error("No solid ground for shulker at " + this.shulkerPlacePos, new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
               this.placementAttempts = 0;
            } else if (this.placeShulkerAtHotbar(this.shulkerPlacePos, (Integer)this.shulkerHotbarSlot.get())) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Placing shulker upright on floor", new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.WAIT_SHULKER_PLACE;
               this.timer = (Integer)this.placeDelay.get();
               this.placementAttempts++;
            } else if ((Boolean)this.debugMessages.get()) {
               this.warning("Failed to place shulker, retrying...", new Object[0]);
               this.timer = (Integer)this.placeDelay.get();
            }
         }
      }
   }

   private void handleWaitShulkerPlace() {
      BlockState placedState = this.mc.world.getBlockState(this.shulkerPlacePos);
      if (placedState.getBlock() instanceof ShulkerBoxBlock) {
         if (!this.isShulkerUpright(this.shulkerPlacePos)) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Shulker placed sideways, breaking to retry upright placement", new Object[0]);
            }

            this.shulkerPlacementRetry = true;
            this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
            this.timer = 2;
            return;
         }

         if ((Boolean)this.debugMessages.get()) {
            this.info("Shulker placed upright successfully", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.OPENING_SHULKER;
         this.timer = (Integer)this.containerOpenDelay.get();
         this.placementAttempts = 0;
      } else if (this.placementAttempts > 15) {
         if ((Boolean)this.debugMessages.get()) {
            this.error("Shulker failed to place after 15 attempts", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
         this.placementAttempts = 0;
      } else {
         if ((Boolean)this.debugMessages.get()) {
            this.warning("Shulker not detected, retrying placement...", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.PLACING_SHULKER;
         this.timer = (Integer)this.placeDelay.get();
      }
   }

   private void handleOpeningShulker() {
      this.mc.options.sneakKey.setPressed(false);
      Vec3d shulkerTop = Vec3d.ofCenter(this.shulkerPlacePos).add(0.0, 0.5, 0.0);
      BlockHitResult hitResult = new BlockHitResult(shulkerTop, Direction.UP, this.shulkerPlacePos, false);
      if (!this.interactBlockWhenAligned(shulkerTop, hitResult)) {
         return;
      }

      this.state = AutoFlyingRegear.FlyingRegearState.TRANSFERRING_ITEMS;
      this.transferSlotIndex = 0;
      this.transferStep = 0;
      this.timer = (Integer)this.containerOpenDelay.get();
      if ((Boolean)this.debugMessages.get()) {
         this.info("Opening shulker", new Object[0]);
      }
   }

   private void handleTransferringItems() {
      if (this.mc.currentScreen instanceof HandledScreen<?> screen) {
         int var13 = this.mc.player.currentScreenHandler.syncId;
         String itemType = this.processingElytras ? "elytras" : "rockets";
         if (this.processingElytras) {
            int currentValidElytras = this.countValidElytras();
            if (currentValidElytras >= (Integer)this.goalElytras.get()) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Reached goal of " + this.goalElytras.get() + " valid elytras, stopping transfer", new Object[0]);
               }

               this.mc.player.closeHandledScreen();
               this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
               this.timer = (Integer)this.breakDelay.get();
               this.transferStep = 0;
               return;
            }
         } else {
            int emptySlots = 0;

            for (int i = 0; i < 36; i++) {
               if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                  emptySlots++;
               }
            }

            if (emptySlots <= 1) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Inventory nearly full (only " + emptySlots + " empty slots), stopping rocket transfer", new Object[0]);
               }

               this.mc.player.closeHandledScreen();
               this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
               this.timer = (Integer)this.breakDelay.get();
               this.transferStep = 0;
               return;
            }
         }

         if (this.transferSlotIndex < 27) {
            Slot slot = this.mc.player.currentScreenHandler.getSlot(this.transferSlotIndex);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
               Item item = stack.getItem();
               if (this.processingElytras && item == Items.ELYTRA) {
                  int maxDurability = stack.getMaxDamage();
                  int currentDurability = maxDurability - stack.getDamage();
                  double percent = (double)currentDurability / maxDurability * 100.0;
                  if (percent >= ((Integer)this.elytraDurabilityThreshold.get()).intValue()) {
                     int currentValidElytras = this.countValidElytras();
                     if (currentValidElytras >= (Integer)this.goalElytras.get()) {
                        if ((Boolean)this.debugMessages.get()) {
                           this.info("Reached goal of " + this.goalElytras.get() + " valid elytras", new Object[0]);
                        }

                        this.mc.player.closeHandledScreen();
                        this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
                        this.timer = (Integer)this.breakDelay.get();
                        this.transferStep = 0;
                        return;
                     }

                     int brokenElytraSlot = this.findBrokenElytraInInventory();
                     if (brokenElytraSlot == -1) {
                        this.mc.interactionManager.clickSlot(var13, this.transferSlotIndex, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                        if ((Boolean)this.debugMessages.get()) {
                           this.info(
                              "Transferred good elytra from shulker slot "
                                 + this.transferSlotIndex
                                 + " (now have "
                                 + (currentValidElytras + 1)
                                 + "/"
                                 + this.goalElytras.get()
                                 + ")",
                              new Object[0]
                           );
                        }

                        this.timer = (Integer)this.clickDelay.get();
                        this.transferStep = 0;
                        this.transferSlotIndex++;
                        return;
                     }

                     if (this.transferStep == 0) {
                        this.mc.interactionManager.clickSlot(var13, this.transferSlotIndex, 0, SlotActionType.PICKUP, this.mc.player);
                        if ((Boolean)this.debugMessages.get()) {
                           this.info("Picking up good elytra from slot " + this.transferSlotIndex, new Object[0]);
                        }

                        this.timer = (Integer)this.clickDelay.get();
                        this.transferStep = 1;
                        return;
                     }

                     if (this.transferStep == 1) {
                        this.mc.interactionManager.clickSlot(var13, brokenElytraSlot, 0, SlotActionType.PICKUP, this.mc.player);
                        if ((Boolean)this.debugMessages.get()) {
                           this.info("Swapping with broken elytra in slot " + brokenElytraSlot, new Object[0]);
                        }

                        this.timer = (Integer)this.clickDelay.get();
                        this.transferStep = 2;
                        return;
                     }

                     if (this.transferStep == 2) {
                        this.mc.interactionManager.clickSlot(var13, this.transferSlotIndex, 0, SlotActionType.PICKUP, this.mc.player);
                        if ((Boolean)this.debugMessages.get()) {
                           this.info("Placed broken elytra back into shulker, gained 1 valid elytra", new Object[0]);
                        }

                        this.timer = (Integer)this.clickDelay.get();
                        this.transferStep = 0;
                        this.transferSlotIndex++;
                        return;
                     }
                  }
               } else if (!this.processingElytras && item == Items.FIREWORK_ROCKET) {
                  this.mc.interactionManager.clickSlot(var13, this.transferSlotIndex, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                  if ((Boolean)this.debugMessages.get()) {
                     this.info("Transferred rockets from shulker slot " + this.transferSlotIndex, new Object[0]);
                  }

                  this.timer = (Integer)this.clickDelay.get();
                  this.transferStep = 0;
                  this.transferSlotIndex++;
                  return;
               }
            }

            this.transferSlotIndex++;
            this.transferStep = 0;
         } else {
            this.mc.player.closeHandledScreen();
            if ((Boolean)this.debugMessages.get()) {
               this.info("Finished transferring " + itemType + " from shulker", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
            this.timer = (Integer)this.breakDelay.get();
            this.transferStep = 0;
         }
      } else {
         if (this.stateTickCounter > 40) {
            if ((Boolean)this.debugMessages.get()) {
               this.error("Failed to open shulker - screen didn't appear", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.OPENING_SHULKER;
            this.timer = (Integer)this.containerOpenDelay.get();
         }
      }
   }

   private int findBrokenElytraInInventory() {
      if (this.mc.player.currentScreenHandler == null) {
         return -1;
      }

      for (int playerSlot = 27; playerSlot < this.mc.player.currentScreenHandler.slots.size(); playerSlot++) {
         Slot slot = this.mc.player.currentScreenHandler.getSlot(playerSlot);
         ItemStack stack = slot.getStack();
         if (stack.getItem() == Items.ELYTRA) {
            int maxDurability = stack.getMaxDamage();
            int currentDurability = maxDurability - stack.getDamage();
            double percent = (double)currentDurability / maxDurability * 100.0;
            if (percent < ((Integer)this.elytraDurabilityThreshold.get()).intValue()) {
               return playerSlot;
            }
         }
      }

      return -1;
   }

   private void handleBreakingShulker() {
      if (this.mlepMine != null && this.mlepMine.isActive()) {
         this.mlepMine.toggle();
         if ((Boolean)this.debugMessages.get()) {
            this.info("Disabled MlepMine to prevent interference", new Object[0]);
         }
      }

      if (this.mc.world.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock) {
         Vec3d shulkerCenter = Vec3d.ofCenter(this.shulkerPlacePos);
         if (!this.breakBlockWhenAligned(this.shulkerPlacePos, Direction.UP, shulkerCenter, true)) {
            return;
         }

         if ((Boolean)this.debugMessages.get()) {
            this.info("Breaking shulker manually", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.WAIT_SHULKER_BREAK;
         this.timer = (Integer)this.breakDelay.get();
         this.placementAttempts = 0;
      } else {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Shulker already broken", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
         this.timer = 0;
      }
   }

   private void handleWaitShulkerBreak() {
      if (!(this.mc.world.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock)) {
         ItemStack hotbarStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
         if (this.shulkerPlacementRetry && this.isShulkerBox(hotbarStack.getItem())) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Sideways shulker broken, re-placing upright from hotbar", new Object[0]);
            }

            this.shulkerPlacementRetry = false;
            this.state = AutoFlyingRegear.FlyingRegearState.ROTATING_FOR_SHULKER;
            this.timer = (Integer)this.placeDelay.get();
            this.placementAttempts = 0;
            return;
         }

         if ((Boolean)this.debugMessages.get()) {
            this.info("Shulker broken - immediately walking to pickup", new Object[0]);
         }

         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null) {
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCustomGoalProcess().setGoal(null);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.WAIT_SHULKER_PICKUP;
         this.timer = 0;
         this.placementAttempts = 0;
      } else {
         Vec3d shulkerCenter = Vec3d.ofCenter(this.shulkerPlacePos);
         if (!this.breakBlockWhenAligned(this.shulkerPlacePos, Direction.UP, shulkerCenter, false)) {
            return;
         }

         if (this.placementAttempts > 150) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Failed to break shulker after extended attempts, skipping return", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
            this.placementAttempts = 0;
         } else {
            this.placementAttempts++;
            this.timer = 0;
         }
      }
   }

   private void handleWaitShulkerPickup() {
      ItemStack hotbarStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
      if (this.isShulkerBox(hotbarStack.getItem())) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Shulker picked up in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
         }

         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null && baritone.getPathingBehavior().isPathing()) {
            baritone.getPathingBehavior().cancelEverything();
         }

         this.shulkerPickupAttempts = 0;
         if (this.shulkerPlacementRetry) {
            this.shulkerPlacementRetry = false;
            this.state = AutoFlyingRegear.FlyingRegearState.ROTATING_FOR_SHULKER;
            this.timer = (Integer)this.placeDelay.get();
            this.placementAttempts = 0;
         } else {
            this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST_RETURN;
            this.timer = (Integer)this.containerOpenDelay.get();
            this.placementAttempts = 0;
         }
      } else {
         for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (this.isShulkerBox(stack.getItem())) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Shulker found in slot " + i + ", moving to hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
               }

               InvUtils.move().from(i).to((Integer)this.shulkerHotbarSlot.get());
               this.timer = (Integer)this.clickDelay.get() * 2;
               this.shulkerPickupAttempts = 0;
               IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
               if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                  baritone.getPathingBehavior().cancelEverything();
               }

               return;
            }
         }

         if (this.stateTickCounter == 1 && this.shulkerPlacePos != null) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
               baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.shulkerPlacePos));
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Walking to shulker item at " + this.shulkerPlacePos.toShortString(), new Object[0]);
               }
            }
         } else if (this.stateTickCounter > 20 && this.stateTickCounter % 40 == 0 && this.shulkerPlacePos != null) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null && !baritone.getPathingBehavior().isPathing()) {
               baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.shulkerPlacePos));
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Re-navigating to shulker", new Object[0]);
               }
            }
         }

         if (this.stateTickCounter > 40 && this.stateTickCounter % 20 == 0) {
            int emptySlots = 0;

            for (int i = 0; i < 36; i++) {
               if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                  emptySlots++;
               }
            }

            if (emptySlots == 0) {
               for (int i = 0; i < 36; i++) {
                  ItemStack stack = this.mc.player.getInventory().getStack(i);
                  if (stack.getItem() == Items.FIREWORK_ROCKET && stack.getCount() >= 32) {
                     InvUtils.drop().slot(i);
                     if ((Boolean)this.debugMessages.get()) {
                        this.info("Dropped rockets from slot " + i + " to make room for shulker", new Object[0]);
                     }

                     this.timer = 10;
                     return;
                  }
               }

               for (int i = 0; i < 36; i++) {
                  ItemStack stack = this.mc.player.getInventory().getStack(i);
                  if (!stack.isEmpty()
                     && !this.isShulkerBox(stack.getItem())
                     && stack.getItem() != Items.ELYTRA
                     && stack.getItem() != Items.ENDER_CHEST
                     && stack.getItem() != Items.OBSIDIAN) {
                     InvUtils.drop().slot(i);
                     if ((Boolean)this.debugMessages.get()) {
                        this.info("Dropped " + stack.getItem() + " to make room for shulker", new Object[0]);
                     }

                     this.timer = 10;
                     return;
                  }
               }
            }
         }

         if (this.stateTickCounter > 120) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Shulker pickup timeout (120 ticks) - shulker not found, moving to next", new Object[0]);
            }

            this.shulkerPickupAttempts = 0;

            try {
               IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
               if (baritone != null && baritone.getPathingBehavior().isPathing()) {
                  baritone.getPathingBehavior().cancelEverything();
               }
            } catch (Exception var5) {
            }

            this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
            this.timer = 0;
         }
      }
   }

   private void handleOpeningEchestReturn() {
      BlockPos standPos = this.echestPos.add(-1, -1, 0);
      Vec3d targetCenter = Vec3d.ofCenter(standPos.up());
      Vec3d playerPos = this.mc.player.getEntityPos();
      double distance = playerPos.distanceTo(targetCenter);
      if (distance > 0.3) {
         if (this.stateTickCounter == 1) {
            try {
               IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
               if (baritone != null) {
                  baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(standPos.up()));
                  if ((Boolean)this.debugMessages.get()) {
                     this.info("Walking back to ender chest", new Object[0]);
                  }
               }
            } catch (Exception e) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Failed to use baritone for positioning: " + e.getMessage(), new Object[0]);
               }
            }
         }

         if (this.stateTickCounter < 40) {
            return;
         }
      }

      try {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null && baritone.getPathingBehavior().isPathing()) {
            baritone.getPathingBehavior().cancelEverything();
         }
      } catch (Exception var19) {
      }

      this.mc.options.sneakKey.setPressed(false);
      Vec3d ecCenter = Vec3d.ofCenter(this.echestPos);
      BlockHitResult openHit = new BlockHitResult(ecCenter, Direction.UP, this.echestPos, false);
      if (!this.interactBlockWhenAligned(ecCenter, openHit)) {
         return;
      }

      this.state = AutoFlyingRegear.FlyingRegearState.RETURNING_SHULKER;
      this.transferStep = 0;
      this.timer = (Integer)this.containerOpenDelay.get();
      if ((Boolean)this.debugMessages.get()) {
         this.info("Opening ender chest to return shulker", new Object[0]);
      }
   }

   private void handleReturningShulker() {
      if (this.mc.currentScreen instanceof GenericContainerScreen screen) {
         GenericContainerScreenHandler handler = (GenericContainerScreenHandler)screen.getScreenHandler();
         int syncId = handler.syncId;
         if (this.transferStep == 0) {
            ItemStack hotbarStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
            if (!this.isShulkerBox(hotbarStack.getItem())) {
               for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
                  ItemStack stack = this.mc.player.getInventory().getStack(i);
                  if (this.isShulkerBox(stack.getItem())) {
                     if ((Boolean)this.debugMessages.get()) {
                        this.info("Shulker found in slot " + i + ", moving to hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
                     }

                     InvUtils.move().from(i).to((Integer)this.shulkerHotbarSlot.get());
                     this.timer = (Integer)this.clickDelay.get() * 2;
                     return;
                  }
               }

               if ((Boolean)this.debugMessages.get()) {
                  this.warning("No shulker in inventory to return", new Object[0]);
               }

               this.mc.player.closeHandledScreen();
               this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
               this.timer = 0;
               return;
            }

            int playerInvStartSlot = handler.getRows() * 9;
            int shulkerSlotInScreen = playerInvStartSlot + (Integer)this.shulkerHotbarSlot.get() + 27;
            this.mc.interactionManager.clickSlot(syncId, shulkerSlotInScreen, 0, SlotActionType.QUICK_MOVE, this.mc.player);
            if ((Boolean)this.debugMessages.get()) {
               this.info("Returning shulker to ender chest via shift-click", new Object[0]);
            }

            this.timer = (Integer)this.clickDelay.get() * 2;
            this.transferStep = 1;
         } else if (this.transferStep == 1) {
            ItemStack hotbarStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
            if (this.isShulkerBox(hotbarStack.getItem())) {
               if (this.stateTickCounter > 60) {
                  if ((Boolean)this.debugMessages.get()) {
                     this.warning("Could not return shulker to ender chest (may be full), proceeding anyway", new Object[0]);
                  }

                  this.mc.player.closeHandledScreen();
                  this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
                  this.timer = (Integer)this.clickDelay.get();
                  return;
               }

               this.transferStep = 0;
               this.timer = (Integer)this.clickDelay.get();
               return;
            }

            if ((Boolean)this.debugMessages.get()) {
               this.info("Shulker successfully returned to ender chest", new Object[0]);
            }

            this.mc.player.closeHandledScreen();
            this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
            this.timer = (Integer)this.clickDelay.get();
         }
      } else {
         if (this.stateTickCounter > 40) {
            if ((Boolean)this.debugMessages.get()) {
               this.error("Failed to open ender chest for shulker return", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.CHECK_NEXT_SHULKER;
            this.timer = 0;
         }
      }
   }

   private void handleCheckNextShulker() {
      if (this.processingElytras) {
         this.processingElytras = false;
         if ((Boolean)this.debugMessages.get()) {
            this.info("Elytra restocking complete, now getting rockets", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
         this.timer = (Integer)this.containerOpenDelay.get();
      } else {
         int emptySlots = 0;

         for (int i = 0; i < 36; i++) {
            if (this.mc.player.getInventory().getStack(i).isEmpty()) {
               emptySlots++;
            }
         }

         if (emptySlots > 1) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Still have " + emptySlots + " empty slots, looking for more rocket shulkers", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
            this.timer = (Integer)this.containerOpenDelay.get();
         } else {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Inventory full (only " + emptySlots + " empty slots) - keeping ender chest for future use", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
            this.timer = 0;
         }
      }
   }

   private void handleBreakingEchest() {
      if (this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
         Vec3d echestCenter = Vec3d.ofCenter(this.echestPos);
         if (!this.breakBlockWhenAligned(this.echestPos, Direction.UP, echestCenter, this.placementAttempts == 0)) {
            return;
         }

         if (this.placementAttempts == 0) {
            this.placementAttempts = 1;
         }

         if ((Boolean)this.debugMessages.get()) {
            this.info("Breaking ender chest", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.WAIT_ECHEST_BREAK;
         this.timer = 0;
      } else {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Ender chest already broken", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
         this.timer = 0;
      }
   }

   private void handleWaitEchestBreak() {
      if (this.mc.world.getBlockState(this.echestPos).getBlock() != Blocks.ENDER_CHEST) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Ender chest broken successfully", new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
         this.timer = 0;
         this.placementAttempts = 0;
      } else {
         Vec3d echestCenter = Vec3d.ofCenter(this.echestPos);
         if (!this.breakBlockWhenAligned(this.echestPos, Direction.UP, echestCenter, false)) {
            return;
         }

         if (this.stateTickCounter > 100) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Failed to break ender chest after 5 seconds, giving up", new Object[0]);
            }

            this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
            this.placementAttempts = 0;
         }
      }
   }

   private void handleRestoringElytra() {
      if ((Boolean)this.swapToChestplate.get() && !this.savedChestplate.isEmpty()) {
         this.restoreChestplate();
         if ((Boolean)this.debugMessages.get()) {
            this.info("Restored elytra", new Object[0]);
         }
      }

      this.state = AutoFlyingRegear.FlyingRegearState.CLEANUP;
      this.timer = 5;
      this.cleanupBlockIndex = 0;
   }

   private void handleCleanup() {
      if (!this.cleanupInitialized) {
         this.cleanupInitialized = true;
         if ((Boolean)this.debugMessages.get()) {
            this.info("Starting cleanup of all blocks above floor for flight clearance", new Object[0]);
         }

         this.cleanupBlocks.clear();
         this.cleanupBlockAttempts = 0;
         if (this.platformCenter != null) {
            Set<BlockPos> floorBlocks = new HashSet<>();
            floorBlocks.add(this.platformCenter.toImmutable());
            floorBlocks.add(this.platformCenter.add(1, 0, 0).toImmutable());
            floorBlocks.add(this.platformCenter.add(0, 0, 1).toImmutable());
            floorBlocks.add(this.platformCenter.add(1, 0, 1).toImmutable());

            for (BlockPos pos : this.placedBlocks) {
               if (!floorBlocks.contains(pos.toImmutable())) {
                  BlockState blockState = this.mc.world.getBlockState(pos);
                  if (!blockState.isAir() && !blockState.isReplaceable()) {
                     this.cleanupBlocks.add(pos.toImmutable());
                  }
               }
            }

            BlockPos[] wallPositionsY1 = new BlockPos[]{
               this.platformCenter.add(-1, 1, 0),
               this.platformCenter.add(-1, 1, 1),
               this.platformCenter.add(0, 1, -1),
               this.platformCenter.add(1, 1, -1),
               this.platformCenter.add(2, 1, 0),
               this.platformCenter.add(2, 1, 1),
               this.platformCenter.add(0, 1, 2),
               this.platformCenter.add(1, 1, 2),
               this.platformCenter.add(0, 1, 0),
               this.platformCenter.add(1, 1, 0),
               this.platformCenter.add(0, 1, 1),
               this.platformCenter.add(1, 1, 1)
            };

            for (BlockPos pos : wallPositionsY1) {
               BlockPos immutablePos = pos.toImmutable();
               if (!this.cleanupBlocks.contains(immutablePos)) {
                  BlockState blockState = this.mc.world.getBlockState(pos);
                  if (!blockState.isAir() && !blockState.isReplaceable()) {
                     this.cleanupBlocks.add(immutablePos);
                  }
               }
            }

            for (BlockPos basePos : wallPositionsY1) {
               BlockPos upperWall = basePos.up();
               BlockPos immutablePos = upperWall.toImmutable();
               if (!this.cleanupBlocks.contains(immutablePos)) {
                  BlockState blockState = this.mc.world.getBlockState(upperWall);
                  if (!blockState.isAir() && !blockState.isReplaceable()) {
                     this.cleanupBlocks.add(immutablePos);
                  }
               }
            }

            BlockPos[] roofPositions = new BlockPos[]{
               this.platformCenter.add(0, 3, 0),
               this.platformCenter.add(1, 3, 0),
               this.platformCenter.add(0, 3, 1),
               this.platformCenter.add(1, 3, 1)
            };

            for (BlockPos pos : roofPositions) {
               BlockPos immutablePos = pos.toImmutable();
               if (!this.cleanupBlocks.contains(immutablePos)) {
                  BlockState blockState = this.mc.world.getBlockState(pos);
                  if (!blockState.isAir() && !blockState.isReplaceable()) {
                     this.cleanupBlocks.add(immutablePos);
                  }
               }
            }
         }

         if (this.cleanupBlocks.isEmpty()) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("No blocks to clean up", new Object[0]);
            }

            this.cleanupInitialized = false;
            this.beginTakeoff();
            return;
         }

         if ((Boolean)this.debugMessages.get()) {
            this.info("Found " + this.cleanupBlocks.size() + " blocks to clean up for flight clearance", new Object[0]);
         }

         this.cleanupBlockIndex = 0;
      }

      if (this.timer <= 0) {
         if (this.cleanupBlockIndex < this.cleanupBlocks.size()) {
            BlockPos pos = this.cleanupBlocks.get(this.cleanupBlockIndex);
            BlockState blockState = this.mc.world.getBlockState(pos);
            if (blockState.isAir()) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size() + " already broken", new Object[0]);
               }

               this.cleanupBlockIndex++;
               this.cleanupBlockAttempts = 0;
               this.timer = 1;
               return;
            }

            if (this.cleanupBlockAttempts > 150) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Cleanup timeout on block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size() + ", skipping", new Object[0]);
               }

               this.cleanupBlockIndex++;
               this.cleanupBlockAttempts = 0;
               this.timer = 2;
               return;
            }

            FindItemResult pickaxe = InvUtils.find(itemStack -> itemStack.isIn(ItemTags.PICKAXES));
            if (pickaxe.found() && pickaxe.isHotbar() && ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != pickaxe.slot()) {
               ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(pickaxe.slot());
            }

            Vec3d playerPos = this.mc.player.getEntityPos();
            Vec3d blockCenter = Vec3d.ofCenter(pos);
            double dx = playerPos.x - blockCenter.x;
            double dy = playerPos.y + this.mc.player.getEyeHeight(this.mc.player.getPose()) - blockCenter.y;
            double dz = playerPos.z - blockCenter.z;
            Direction breakDirection;
            if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) {
               breakDirection = dx > 0.0 ? Direction.EAST : Direction.WEST;
            } else if (Math.abs(dz) > Math.abs(dy)) {
               breakDirection = dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
            } else {
               breakDirection = dy > 0.0 ? Direction.UP : Direction.DOWN;
            }

            boolean startBreak = this.cleanupBlockAttempts == 0;
            if (!this.breakBlockWhenAligned(pos, breakDirection, blockCenter, startBreak)) {
               return;
            }

            if (startBreak && (Boolean)this.debugMessages.get()) {
               this.info("Started breaking block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size(), new Object[0]);
            }

            this.cleanupBlockAttempts++;
            this.timer = 0;
         } else {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Cleanup complete, removed " + this.cleanupBlocks.size() + " blocks", new Object[0]);
            }

            this.cleanupBlocks.clear();
            this.cleanupBlockAttempts = 0;
            this.cleanupBlockIndex = 0;
            this.cleanupInitialized = false;
            this.beginTakeoff();
         }
      }
   }

   private void beginTakeoff() {
      this.takeoffInitialized = false;
      this.state = AutoFlyingRegear.FlyingRegearState.TAKING_OFF;
      this.timer = 0;
   }

   public boolean isTakingOff() {
      return this.isActive() && this.state == AutoFlyingRegear.FlyingRegearState.TAKING_OFF;
   }

   private void handleTakingOff() {
      if (!this.takeoffInitialized) {
         this.takeoffInitialized = true;
         this.initTakeoff();
         this.startTakeoffSequence();
      }
   }

   private void initTakeoff() {
      this.releaseMovementKeys();
      if ((Boolean)this.debugMessages.get()) {
         this.info("Preparing for takeoff", new Object[0]);
      }

      ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
      if (chestItem.getItem() != Items.ELYTRA) {
         if ((Boolean)this.swapToChestplate.get() && !this.savedChestplate.isEmpty()) {
            this.restoreChestplate();
            if ((Boolean)this.debugMessages.get()) {
               this.info("Restored elytra for takeoff", new Object[0]);
            }
         } else if ((Boolean)this.debugMessages.get()) {
            this.warning("No elytra equipped!", new Object[0]);
         }
      }

      this.mc.player.setYaw(this.savedYaw);
      this.mc.player.setPitch(8.0F);
      this.ensureRocketsInHotbar();
      int targetSlot = (Integer)this.rocketHotbarSlot.get();
      if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != targetSlot) {
         ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(targetSlot);
      }
   }

   private void startTakeoffSequence() {
      this.takeoff.start((Integer)this.rocketHotbarSlot.get(), new ElytraTakeoff.Listener() {
         @Override
         public void onTakeoffSuccess(double speedBps) {
            AutoFlyingRegear.this.finishTakeoff(true);
            if ((Boolean)AutoFlyingRegear.this.debugMessages.get()) {
               AutoFlyingRegear.this.info("Takeoff complete at " + String.format("%.1f", speedBps) + " b/s", new Object[0]);
            }
         }

         @Override
         public void onTakeoffFailed(String reason) {
            AutoFlyingRegear.this.finishTakeoffSafely(false, reason);
         }

         @Override
         public void onTakeoffDebug(String message) {
            if ((Boolean)AutoFlyingRegear.this.debugMessages.get()) {
               AutoFlyingRegear.this.info(message, new Object[0]);
            }
         }
      });
   }

   private void enableFlightModulesForTakeoff() {
      if (!(Boolean)this.autoReEnable.get() || this.reEnableStage >= 2) {
         return;
      }

      this.reEnableFlightModules();
      if (this.reEnableStage < 2) {
         this.reEnableFlightModules();
      }
   }

   private void finishTakeoff(boolean flightConfirmed) {
      this.finishTakeoffSafely(flightConfirmed, null);
   }

   private void finishTakeoffSafely(boolean flightConfirmed, String reason) {
      this.releaseMovementKeys();
      if (flightConfirmed) {
         this.mc.player.setYaw(this.savedYaw);
         this.mc.player.setPitch(this.savedPitch);
      }

      this.enableFlightModulesForTakeoff();

      if ((Boolean)this.debugMessages.get()) {
         if (flightConfirmed) {
            Vec3d velocity = this.mc.player.getVelocity();
            double speed = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z) * 20.0;
            this.info("Takeoff complete - flying at " + String.format("%.1f", speed) + " b/s", new Object[0]);
         } else if (reason != null) {
            this.warning("Takeoff failed: " + reason + " - flight modules enabled for recovery", new Object[0]);
         } else {
            this.info("Takeoff finished", new Object[0]);
         }
      }

      this.takeoff.cancel();
      this.takeoffInitialized = false;
      this.state = AutoFlyingRegear.FlyingRegearState.COMPLETE;
      this.timer = 5;
   }

   private void handleComplete() {
      if (this.stateTickCounter == 1) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("AutoFlyingRegear complete! Rockets: " + this.countRockets() + " Elytras: " + this.countValidElytras(), new Object[0]);
         }

         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         this.mc.player.setYaw(this.savedYaw);
         this.mc.player.setPitch(this.savedPitch);
         if ((Boolean)this.debugMessages.get()) {
            this.info("Restored rotation: yaw=" + this.savedYaw + ", pitch=" + this.savedPitch, new Object[0]);
         }

         this.ensureRocketsInHotbar();
      }

      if ((Boolean)this.autoReEnable.get()) {
         if (this.reEnableStage == 0 && this.stateTickCounter >= 5) {
            this.reEnableFlightModules();
            return;
         }

         if (this.reEnableStage == 1 && this.timer == 0) {
            this.reEnableFlightModules();
            this.timer = 5;
            return;
         }

         if (this.reEnableStage == 2 && this.timer == 0) {
            if (this.hadBaritoneGoal && this.savedBaritoneCommand != null) {
               try {
                  IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                  if (baritone != null) {
                     this.mc.player.networkHandler.sendChatMessage(this.savedBaritoneCommand);
                     if ((Boolean)this.debugMessages.get()) {
                        this.info("Restored baritone elytra mode", new Object[0]);
                     }
                  }
               } catch (Exception e) {
                  if ((Boolean)this.debugMessages.get()) {
                     this.warning("Failed to restore baritone state: " + e.getMessage(), new Object[0]);
                  }
               }
            }

            this.reEnableStage = 3;
            this.timer = 5;
            return;
         }

         if (this.reEnableStage < 3) {
            return;
         }
      }

      if (!(Boolean)this.autoReEnable.get() && this.stateTickCounter == 10 && this.hadBaritoneGoal && this.savedBaritoneCommand != null) {
         try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
               this.mc.player.networkHandler.sendChatMessage(this.savedBaritoneCommand);
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Restored baritone elytra mode", new Object[0]);
               }
            }
         } catch (Exception e) {
            if ((Boolean)this.debugMessages.get()) {
               this.warning("Failed to restore baritone state: " + e.getMessage(), new Object[0]);
            }
         }
      }

      if (this.stateTickCounter == 12 && this.mlepMineWasActive && this.mlepMine != null && !this.mlepMine.isActive()) {
         this.mlepMine.toggle();
         if ((Boolean)this.debugMessages.get()) {
            this.info("Re-enabled MlepMine", new Object[0]);
         }
      }

      if (this.stateTickCounter > 15) {
         this.state = AutoFlyingRegear.FlyingRegearState.IDLE;
         this.placedBlocks.clear();
         this.pendingBlocks.clear();
         this.platformCenter = null;
         this.processingElytras = true;
         this.timer = 0;
         this.shulkerEnderSlot = -1;
         this.transferStep = 0;
         this.transferSlotIndex = 0;
         this.placementAttempts = 0;
         this.stateTickCounter = 0;
         this.hadBaritoneGoal = false;
         this.savedBaritoneCommand = null;
         this.reEnableStage = 0;
         this.processedShulkers.clear();
         this.mlepMineWasActive = false;
         this.cleanupBlockIndex = 0;
         this.cleanupBlockAttempts = 0;
      }
   }

   private boolean shouldTriggerRegear() {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         GameMode gameMode = this.mc.interactionManager.getCurrentGameMode();
         if (gameMode != GameMode.SURVIVAL) {
            return false;
         }

         int rockets = this.countRockets();
         int validElytras = this.countValidElytras();
         return rockets < (Integer)this.minRockets.get() || validElytras < (Integer)this.minElytras.get();
      } else {
         return false;
      }
   }

   private int countRockets() {
      if (this.mc.player == null) {
         return 0;
      }

      int count = 0;

      for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.FIREWORK_ROCKET) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private int countValidElytras() {
      if (this.mc.player == null) {
         return 0;
      }

      int count = 0;

      for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.ELYTRA) {
            int maxDurability = stack.getMaxDamage();
            int currentDurability = maxDurability - stack.getDamage();
            double percent = (double)currentDurability / maxDurability * 100.0;
            if (percent >= ((Integer)this.elytraDurabilityThreshold.get()).intValue()) {
               count++;
            }
         }
      }

      return count;
   }

   private void ensureRocketsInHotbar() {
      if (this.mc.player != null) {
         int targetSlot = (Integer)this.rocketHotbarSlot.get();
         ItemStack hotbarStack = this.mc.player.getInventory().getStack(targetSlot);
         if (hotbarStack.getItem() == Items.FIREWORK_ROCKET) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Rockets already in hotbar slot " + targetSlot, new Object[0]);
            }
         } else {
            int bestRocketSlot = -1;
            int bestRocketCount = 0;

            for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
               if (i != targetSlot && i < 36) {
                  ItemStack stack = this.mc.player.getInventory().getStack(i);
                  if (stack.getItem() == Items.FIREWORK_ROCKET && stack.getCount() > bestRocketCount) {
                     bestRocketSlot = i;
                     bestRocketCount = stack.getCount();
                  }
               }
            }

            if (bestRocketSlot != -1) {
               InvUtils.move().from(bestRocketSlot).to(targetSlot);
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Moved rockets from slot " + bestRocketSlot + " to hotbar slot " + targetSlot, new Object[0]);
               }
            } else if ((Boolean)this.debugMessages.get()) {
               this.warning("No rockets found in inventory to move to hotbar slot " + targetSlot, new Object[0]);
            }
         }
      }
   }

   private void disableAllFlightModules() {
      for (Entry<String, Module> entry : this.flightModules.entrySet()) {
         Module module = entry.getValue();
         if (module != null && module.isActive()) {
            module.toggle();
            this.disabledModules.add(entry.getKey());
            if ((Boolean)this.debugMessages.get()) {
               this.info("Disabled " + entry.getKey(), new Object[0]);
            }
         }
      }
   }

   private void reEnableFlightModules() {
      if (this.reEnableStage == 0) {
         if (this.disabledModules.contains("TrailFollower")) {
            Module module = this.flightModules.get("TrailFollower");
            if (module != null && !module.isActive()) {
               module.toggle();
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Re-enabled TrailFollower (priority)", new Object[0]);
               }
            }
         }

         if (this.disabledModules.contains("WaypointFollower")) {
            Module module = this.flightModules.get("WaypointFollower");
            if (module != null && !module.isActive()) {
               module.toggle();
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Re-enabled WaypointFollower (priority)", new Object[0]);
               }
            }
         }

         this.disabledModules.remove("TrailFollower");
         this.disabledModules.remove("WaypointFollower");
         this.reEnableStage = 1;
         this.timer = 5;
      } else {
         for (String moduleName : new ArrayList<>(this.disabledModules)) {
            Module module = this.flightModules.get(moduleName);
            if (module != null && !module.isActive()) {
               module.toggle();
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Re-enabled " + moduleName, new Object[0]);
               }
            }
         }

         this.disabledModules.clear();
         this.reEnableStage = 2;
      }
   }

   private void restoreChestplate() {
      if (!this.savedChestplate.isEmpty()) {
         for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ELYTRA) {
               InvUtils.move().from(i).toArmor(2);
               this.savedChestplate = ItemStack.EMPTY;
               return;
            }
         }
      }
   }

   private boolean ensureBuildBlocksInHotbar() {
      int targetHotbarSlot = (Integer)this.obsidianHotbarSlot.get();
      ItemStack currentStack = this.mc.player.getInventory().getStack(targetHotbarSlot);
      if (currentStack.getItem() instanceof BlockItem && !currentStack.isEmpty()) {
         return true;
      }

      FindItemResult blocks = InvUtils.find(itemStack -> {
         if (itemStack.getItem() instanceof BlockItem blockItem) {
            if (itemStack.getItem() == Items.ENDER_CHEST) {
               return false;
            }

            Block block = blockItem.getBlock();
            return block.getDefaultState().isSolidBlock(this.mc.world, BlockPos.ORIGIN);
         } else {
            return false;
         }
      });
      if (!blocks.found()) {
         if ((Boolean)this.debugMessages.get()) {
            this.error("No solid blocks found in inventory!", new Object[0]);
         }

         return false;
      }

      if (blocks.slot() != targetHotbarSlot) {
         InvUtils.move().from(blocks.slot()).to(targetHotbarSlot);
         this.timer = (Integer)this.clickDelay.get();
         return false;
      }

      return true;
   }

   private void releaseMovementKeys() {
      Utils.setPressed(this.mc.options.forwardKey, false);
      Utils.setPressed(this.mc.options.backKey, false);
      Utils.setPressed(this.mc.options.leftKey, false);
      Utils.setPressed(this.mc.options.rightKey, false);
      Utils.setPressed(this.mc.options.sneakKey, false);
      Utils.setPressed(this.mc.options.jumpKey, false);
      Utils.setPressed(this.mc.options.useKey, false);
   }

   private BlockHitResult getPlaceHitForBlock(BlockPos target) {
      Vec3d eye = this.mc.player.getEyePos();
      BlockHitResult best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction d : Direction.values()) {
         BlockPos against = target.offset(d);
         BlockState state = this.mc.world.getBlockState(against);
         if (!state.isReplaceable() && !state.getCollisionShape(this.mc.world, against).isEmpty()) {
            Direction side = d.getOpposite();
            Vec3d normal = Vec3d.of(side.getVector());
            Vec3d hitVec = Vec3d.ofCenter(against).add(normal.multiply(0.5));
            if (!(eye.subtract(hitVec).dotProduct(normal) <= 0.0)) {
               double dist = eye.squaredDistanceTo(hitVec);
               if (!(dist > 20.25) && dist < bestDist) {
                  bestDist = dist;
                  best = new BlockHitResult(hitVec, side, against, false);
               }
            }
         }
      }

      return best;
   }

   private BlockHitResult resolvePlaceHit(BlockPos pos) {
      BlockHitResult hit = this.getPlaceHitForBlock(pos);
      return hit != null ? hit : PlacementUtils.resolvePlaceHit(pos, PlacementUtils.DEFAULT_REACH);
   }

   private boolean canPlaceStructure(BlockPos pos) {
      if (!this.mc.world.getBlockState(pos).isReplaceable()) {
         return false;
      }

      return this.mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, net.minecraft.block.ShapeContext.absent());
   }

   private boolean isRoofPosition(BlockPos pos) {
      if (this.platformCenter == null) {
         return false;
      }

      if (pos.getY() != this.platformCenter.getY() + 3) {
         return false;
      }

      int dx = pos.getX() - this.platformCenter.getX();
      int dz = pos.getZ() - this.platformCenter.getZ();
      return dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
   }

   private BlockHitResult getGrimStructureHit(BlockPos pos) {
      Vec3d center = Vec3d.ofCenter(pos);
      if (this.isRoofPosition(pos)) {
         return new BlockHitResult(center.add(0.0, -0.001, 0.0), Direction.UP, pos, true);
      }

      return new BlockHitResult(center.add(0.0, -0.001, 0.0), Direction.DOWN, pos, true);
   }

   private BlockHitResult resolveStructurePlaceHit(BlockPos pos) {
      if (this.placementAttempts % 2 == 1) {
         return this.getGrimStructureHit(pos);
      }

      Vec3d eye = this.mc.player.getEyePos();
      BlockHitResult best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction d : Direction.values()) {
         BlockPos against = pos.offset(d);
         BlockState state = this.mc.world.getBlockState(against);
         if (!state.isReplaceable() && !state.getCollisionShape(this.mc.world, against).isEmpty()) {
            Direction side = d.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(against).add(Vec3d.of(side.getVector()).multiply(0.5));
            double dist = eye.squaredDistanceTo(hitVec);
            if (!(dist > 20.25) && dist < bestDist) {
               bestDist = dist;
               best = new BlockHitResult(hitVec, side, against, true);
            }
         }
      }

      if (best != null) {
         return best;
      }

      BlockHitResult grim = this.getGrimStructureHit(pos);
      if (grim != null) {
         return grim;
      }

      return PlacementUtils.getAirPlaceHit(pos, PlacementUtils.DEFAULT_REACH);
   }

   private boolean placeStructureBlock(BlockPos pos) {
      if (!this.canPlaceStructure(pos)) {
         return false;
      }

      if (!this.ensureBuildBlocksInHotbar()) {
         return false;
      }

      int hotbarSlot = (Integer)this.obsidianHotbarSlot.get();
      ItemStack stack = this.mc.player.getInventory().getStack(hotbarSlot);
      if (stack.isEmpty()) {
         return false;
      }

      if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != hotbarSlot) {
         ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(hotbarSlot);
      }

      FindItemResult block = InvUtils.findInHotbar(itemStack -> itemStack.getItem() == stack.getItem());
      if (!block.found()) {
         return false;
      }

      BlockHitResult hit = this.resolveStructurePlaceHit(pos);
      if (hit == null) {
         return false;
      }

      if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) {
         return false;
      }

      float[] rotations = PlacementUtils.rotationsForHit(hit);
      float pitch = MathHelper.clamp(rotations[1], -80.0F, 80.0F);
      RotationUtils.getInstance().setRotationFullInstant(rotations[0], pitch);
      PlacementUtils.grimPlace(hit);
      this.mc.player.swingHand(Hand.MAIN_HAND);
      return true;
   }

   private void rotateTowards(Vec3d target) {
      float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), target);
      RotationUtils.getInstance().setRotationFull(rotations[0], rotations[1], ROTATION_TURN_SPEED);
   }

   private boolean isRotationReady() {
      return RotationUtils.getInstance().isAligned(ROTATION_ALIGN_EPS);
   }

   private boolean interactBlockWhenAligned(Vec3d lookTarget, BlockHitResult hit) {
      this.rotateTowards(lookTarget);
      if (!this.isRotationReady()) {
         return false;
      }

      this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hit);
      return true;
   }

   private boolean breakBlockWhenAligned(BlockPos pos, Direction direction, Vec3d lookTarget, boolean startBreak) {
      this.rotateTowards(lookTarget);
      if (!this.isRotationReady()) {
         return false;
      }

      if (startBreak) {
         this.mc.interactionManager.attackBlock(pos, direction);
      } else {
         this.mc.interactionManager.updateBlockBreakingProgress(pos, direction);
      }

      this.mc.player.swingHand(Hand.MAIN_HAND);
      return true;
   }

   private boolean isShulkerUpright(BlockPos pos) {
      BlockState state = this.mc.world.getBlockState(pos);
      return state.getBlock() instanceof ShulkerBoxBlock
         && state.contains(Properties.FACING)
         && state.get(Properties.FACING) == Direction.UP;
   }

   private BlockHitResult resolveShulkerPlaceHit(BlockPos pos) {
      BlockPos ground = pos.down();
      BlockState groundState = this.mc.world.getBlockState(ground);
      if (!groundState.isSolidBlock(this.mc.world, ground)) {
         return null;
      }

      Vec3d hitVec = Vec3d.ofCenter(ground).add(0.0, 0.5, 0.0);
      return new BlockHitResult(hitVec, Direction.UP, ground, false);
   }

   private boolean placeShulkerAtHotbar(BlockPos pos, int hotbarSlot) {
      BlockState posState = this.mc.world.getBlockState(pos);
      if (!posState.isAir() && !posState.isReplaceable()) {
         return false;
      }

      ItemStack stack = this.mc.player.getInventory().getStack(hotbarSlot);
      if (stack.isEmpty() || !this.isShulkerBox(stack.getItem())) {
         return false;
      }

      BlockHitResult hit = this.resolveShulkerPlaceHit(pos);
      if (hit == null) {
         return false;
      }

      if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != hotbarSlot) {
         ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(hotbarSlot);
      }

      FindItemResult block = InvUtils.findInHotbar(itemStack -> itemStack.getItem() == stack.getItem());
      if (!block.found()) {
         return false;
      }

      if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) {
         return false;
      }

      float[] rotations = PlacementUtils.rotationsForHit(hit);
      float pitch = MathHelper.clamp(rotations[1], -80.0F, 80.0F);
      RotationUtils.getInstance().setRotationFullInstant(rotations[0], pitch);
      Hand hand = block.getHand() != null ? block.getHand() : Hand.MAIN_HAND;
      this.mc.interactionManager.interactBlock(this.mc.player, hand, hit);
      this.mc.player.swingHand(hand);
      return true;
   }

   private boolean placeBlockAtHotbar(BlockPos pos, int hotbarSlot) {
      if (!PlacementUtils.canPlace(pos, false)) {
         return false;
      }

      ItemStack stack = this.mc.player.getInventory().getStack(hotbarSlot);
      if (stack.isEmpty()) {
         return false;
      }

      if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != hotbarSlot) {
         ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(hotbarSlot);
      }

      FindItemResult block = InvUtils.findInHotbar(itemStack -> itemStack.getItem() == stack.getItem());
      if (!block.found()) {
         return false;
      }

      BlockHitResult hit = this.resolvePlaceHit(pos);
      if (hit == null) {
         return false;
      }

      if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) {
         return false;
      }

      float[] rotations = PlacementUtils.rotationsForHit(hit);
      float pitch = MathHelper.clamp(rotations[1], -80.0F, 80.0F);
      RotationUtils.getInstance().setRotationFullInstant(rotations[0], pitch);
      Hand hand = block.getHand() != null ? block.getHand() : Hand.MAIN_HAND;
      PlacementUtils.placeHit(hit, hand, true);
      return true;
   }

   private boolean placeBlockGrim(BlockPos pos) {
      if (!this.ensureBuildBlocksInHotbar()) {
         return false;
      }

      return this.placeBlockAtHotbar(pos, (Integer)this.obsidianHotbarSlot.get());
   }

   private boolean isShulkerBox(Item item) {
      return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && !this.placedBlocks.isEmpty()) {
         for (BlockPos pos : this.placedBlocks) {
            event.renderer.box(pos, (Color)this.platformColor.get(), (Color)this.platformColor.get(), ShapeMode.Both, 0);
         }
      }
   }

   public String getInfoString() {
      int rockets = this.countRockets();
      int elytras = this.countValidElytras();
      return rockets + "R/" + elytras + "E - " + this.state.name();
   }

   private enum FlyingRegearState {
      IDLE,
      SWAP_TO_CHESTPLATE,
      DISABLING_MODULES,
      DROPPING,
      CENTERING_ON_PLATFORM,
      CREATING_INITIAL_PLATFORM,
      CREATING_WALLS,
      CLEARING_ECHEST_AREA,
      ROTATING_FOR_ECHEST,
      PLACING_ECHEST,
      WAIT_ECHEST_PLACE,
      OPENING_ECHEST,
      TAKING_SHULKER,
      WAIT_SHULKER_TAKEN,
      POSITIONING_FOR_SHULKER,
      ROTATING_FOR_SHULKER,
      PLACING_SHULKER,
      WAIT_SHULKER_PLACE,
      OPENING_SHULKER,
      TRANSFERRING_ITEMS,
      BREAKING_SHULKER,
      WAIT_SHULKER_BREAK,
      WAIT_SHULKER_PICKUP,
      OPENING_ECHEST_RETURN,
      RETURNING_SHULKER,
      CHECK_NEXT_SHULKER,
      BREAKING_ECHEST,
      WAIT_ECHEST_BREAK,
      RESTORING_ELYTRA,
      CLEANUP,
      TAKING_OFF,
      COMPLETE;
   }
}
