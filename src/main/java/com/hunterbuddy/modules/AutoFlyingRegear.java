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
import com.hunterbuddy.util.LifetimeStats;
import com.hunterbuddy.util.SessionStats;
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
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
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
   private final Setting<Integer> minGaps = this.sgTriggers
      .add(
         new Builder().name("min-gaps").description("Regear when fewer enchanted golden apples than this are carried. 0 = never for that reason.").defaultValue(0)
            .min(0)
            .sliderMax(64)
            .build()
      );
   private final Setting<Integer> minTotems = this.sgTriggers
      .add(
         new Builder().name("min-totems").description("Regear when fewer totems of undying than this are carried (offhand included). 0 = never for that reason.").defaultValue(0)
            .min(0)
            .sliderMax(16)
            .build()
      );
   private final Setting<Integer> minEnderChests = this.sgTriggers
      .add(
         new Builder().name("min-ender-chests").description("Regear when fewer ender chests than this are carried, like the gaps and totems; the regear takes more from a shulker in the ender chest (goal-ender-chests). 0 = never for that reason.").defaultValue(0)
            .min(0)
            .sliderMax(16)
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
   private final Setting<AutoFlyingRegear.ElytraMode> elytraMode = this.sgTriggers
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<AutoFlyingRegear.ElytraMode>()
                     .name("elytra-mode")
                  .description("REPLACE swaps worn elytras for fresh ones out of the shulker. REPAIR keeps the ones you fly and mends them with experience bottles, which only works while they carry Mending.")
               .defaultValue(AutoFlyingRegear.ElytraMode.REPAIR)
            .build()
      );
   private final Setting<AutoFlyingRegear.OutOfSuppliesAction> outOfSupplies = this.sgTriggers
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<AutoFlyingRegear.OutOfSuppliesAction>()
                     .name("out-of-supplies")
                  .description("What to do when a regear ends with something still missing, which means the ender chest cannot supply it any more. The bot stays in the box it just built and never takes off again: OFF_MODULES leaves the flight modules off and switches this one off too, DISCONNECT does the same and then leaves the server.")
               .defaultValue(AutoFlyingRegear.OutOfSuppliesAction.OFF_MODULES)
            .build()
      );
   private final Setting<Integer> xpBottlesToTake = this.sgTriggers
      .add(
         new Builder().name("xp-bottles-to-take").description("How many experience bottles to pull from the shulker before mending.")
                  .defaultValue(128)
               .min(16)
            .max(1024)
            .visible(() -> this.elytraMode.get() == AutoFlyingRegear.ElytraMode.REPAIR)
            .build()
      );
   private final Setting<Integer> repairStallSeconds = this.sgTriggers
      .add(
         new Builder().name("repair-stall-seconds")
                  .description("Give up on an elytra after this many seconds without its durability rising and without a single bottle being spent. Both have to be still: bottles going down means AutoEXPPlus is working on something else first, usually armour, and that is worth waiting through.")
                  .defaultValue(6)
               .min(2)
            .max(60)
            .visible(() -> this.elytraMode.get() == AutoFlyingRegear.ElytraMode.REPAIR)
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
   private final Setting<Integer> goalGaps = this.sgRestock
      .add(
         new Builder().name("goal-gaps").description("Enchanted golden apples to carry after a regear, pulled from any shulker in the ender chest that holds some, before the rockets fill what is left. 0 = leave them alone.").defaultValue(32)
            .min(0)
            .sliderMax(128)
            .build()
      );
   private final Setting<Integer> goalTotems = this.sgRestock
      .add(
         new Builder().name("goal-totems").description("Totems of undying to carry after a regear, same way; one goes to the offhand if it is empty. 0 = leave them alone.").defaultValue(4)
            .min(0)
            .sliderMax(27)
            .build()
      );
   private final Setting<Integer> goalEnderChests = this.sgRestock
      .add(
         new Builder().name("goal-ender-chests").description("Ender chests to carry after a regear when fewer are left, pulled from any shulker in the ender chest that holds some, the same way as the gaps and totems. 0 = leave them alone.").defaultValue(16)
            .min(0)
            .sliderMax(64)
            .build()
      );
   private final Setting<Integer> keepEnderChests = this.sgRestock
      .add(
         new Builder().name("keep-ender-chests").description("Ender chests the obsidian refill never breaks: the next regear needs one to reach the supplies at all.").defaultValue(2)
            .min(1)
            .sliderMax(16)
            .build()
      );
   private final Setting<Integer> obsidianRefillAt = this.sgRestock
      .add(
         new Builder().name("obsidian-refill-at").description("Looked at once per regear, last, when everything else is restocked and before the box comes down: with this much obsidian or less, ender chests are broken inside the box, 8 obsidian each, up to obsidian-refill-to. With more, nothing happens. Obsidian never starts a regear by itself.").defaultValue(26)
            .min(0)
            .sliderMax(64)
            .build()
      );
   private final Setting<Integer> obsidianRefillTo = this.sgRestock
      .add(
         new Builder().name("obsidian-refill-to").description("How much obsidian the refill stops at. 0 = no refill.").defaultValue(64)
            .min(0)
            .sliderMax(128)
            .build()
      );
   private final Setting<Integer> maxEnderChestsBroken = this.sgRestock
      .add(
         new Builder().name("max-ender-chests-broken").description("Most ender chests the obsidian refill places and breaks in one regear, besides the one the regear placed anyway.").defaultValue(5)
            .min(0)
            .sliderMax(16)
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
   private final Setting<AutoFlyingRegear.NetherTakeoff> netherTakeoff = this.sgRestock
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<AutoFlyingRegear.NetherTakeoff>()
                     .name("nether-takeoff")
                  .description("BARITONE: in the Nether, when the flight module about to fly uses Baritone, the regear ends on the platform and Baritone takes off itself along its own path (needs elytraAutoJump on). SELF: the regear jumps and boosts on the old heading before handing over, as everywhere else.")
               .defaultValue(AutoFlyingRegear.NetherTakeoff.BARITONE)
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
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
                  .name("shape-mode")
               .description("How the regear's blocks are drawn.")
            .defaultValue(ShapeMode.Both)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("side-color")
               .description("Fill colour of the regear's blocks. Faint by default, like AutoPortal.")
            .defaultValue(new SettingColor(100, 100, 255, 25))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("line-color")
               .description("Outline colour of the regear's blocks.")
            .defaultValue(new SettingColor(100, 100, 255, 200))
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
   /** Which extra kind is being restocked between the elytras and the rockets, {@link Extra#NONE} outside that. */
   private Extra extra = Extra.NONE;
   /** Whether this regear has already decided on its obsidian refill; see handleRestoringElytra. */
   private boolean obsidianTopUpDecided;
   /** Where the obsidian refill is: 0 deciding, 1 placing a chest, 2 breaking it, 3 waiting for its obsidian. */
   private int topUpStep;
   private int topUpStepTicks;
   /** Ender chests the refill has placed and broken in this regear, besides the regear's own. */
   private int topUpChestsBroken;
   /** Obsidian and ender chests held when the current break began, to tell what it gave. */
   private int topUpObsidianBefore;
   private int topUpChestsBefore;
   private boolean topUpBreakStarted;

   /** Who takes off after a Nether regear. */
   public enum NetherTakeoff {
      BARITONE,
      SELF
   }

   /** The restocks that come between the elytras and the rockets: the rockets fill whatever room is left, so these go first. */
   private enum Extra {
      NONE,
      GAP,
      TOTEM,
      ECHEST
   }

   /** One regear, from the decision to land to the takeoff, as the HUD and the counters see it. */
   public static final class Run {
      public final long startedMs = System.currentTimeMillis();
      /** Zero while the run is still going. */
      public long endedMs;
      public AutoFlyingRegear.ElytraMode mode;
      /** "complete", "takeoff failed: …" or "aborted"; null while running. */
      public String outcome;
      /** How long the flight went on over lava before this run could start. */
      public int lavaWaitSeconds;
      public int rocketsBefore;
      public int elytrasBefore;
      public int gapsBefore;
      public int totemsBefore;
      public int bottlesBefore;
      public int rocketsAfter;
      public int elytrasAfter;
      public int gapsAfter;
      public int totemsAfter;
      public int bottlesAfter;
      public int enderChestsBefore;
      public int enderChestsAfter;
      public int obsidianBefore;
      public int obsidianAfter;
      public int shulkersTaken;

      public long durationMs() {
         return (this.endedMs == 0L ? System.currentTimeMillis() : this.endedMs) - this.startedMs;
      }
   }

   /** The regear as a line of stations, for the HUD; which ones exist depends on the settings. */
   public enum Step {
      LAND,
      BOX,
      CHEST,
      ELYTRA,
      GAPS,
      TOTEMS,
      ROCKETS,
      MEND,
      CLEAR,
      FLY
   }

   private AutoFlyingRegear.Run currentRun;
   private AutoFlyingRegear.Run lastRun;
   private int sessionRegears;
   private long sessionRegearMs;
   private String lastEvent = "";
   private long lastEventMs;
   /** Set by the takeoff, read when the machine reaches COMPLETE, so one place closes the run. */
   private String pendingOutcome;
   /** SessionStats' spent-rocket count and the time it was read, re-based at activation and after every regear: the HUD's consumption rate. */
   private int rocketRateBaseUsed;
   private long rocketRateBaseMs;

   /**
    * Whether AutoEXPPlus was already running before the repair phase turned it on.
    *
    * <p>Null while nothing has been touched. Some people leave that module on permanently;
    * switching it off afterwards because the regear happened to use it would be taking away
    * something the player never handed over. It is only put back if this module moved it.
    */
   private Boolean autoExpWasActive = null;
   private int repairHotbarSlot = -1;
   private int repairLastDurability = -1;
   private int repairLastBottles = -1;
   private int repairStallTicks = 0;
   private int transferSlotIndex = 0;
   private int transferStep = 0;
   private ItemStack savedChestplate = ItemStack.EMPTY;
   private final Set<String> processedShulkers = new HashSet<>();
   private int rocketShulkersTaken = 0;
   private int placementAttempts = 0;
   private final Map<BlockPos, Integer> platformPlacementAttempts = new HashMap<>();
   private static final int MAX_PLATFORM_PLACEMENT_ATTEMPTS = 12;
   private int shulkerEnderSlot = -1;
   private final int maxPlacementAttempts = 15;
   private int stateTickCounter = 0;
   private AutoFlyingRegear.FlyingRegearState lastState = AutoFlyingRegear.FlyingRegearState.IDLE;
   /** Guards the once-only COMPLETE work; reset per regear, set the first time handleComplete runs. */
   private boolean completeHandled;
   private boolean platformBuildAborted;
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
   private int lavaPostponeTicks = 0;
   private String savedBaritoneCommand = null;
   private int startupDelayTicks = 0;
   private boolean startupComplete = false;
   private int lowSupplyTicks = 0;
   private String lowSupplyFirstReason = "none";
   private AutoEat autoEatModule = null;
   private AutoEatSync autoEatSyncModule = null;
   private int wallLayer = 0;
   private int wallBuildPhase = 0;
   private boolean wallsStarted = false;
   private boolean placementHeld = false;
   private boolean savedAllowPlace = true;
   private boolean runAborted = false;
   private int wallMissingLast = -1;
   private int wallNoProgressRounds = 0;
   private final Set<BlockPos> wallMineQueued = new HashSet<>();
   private int centerGoalTick = -1;
   private int verifyRounds = 0;
   private BlockPos currentClearingPos = null;
   private int clearingProgress = 0;
   private final Set<BlockPos> clearingGivenUp = new HashSet<>();
   private int clearingAttempts;
   private int stepAsideTicks;
   private int shulkerPickupAttempts = 0;
   private boolean shulkerPlacementRetry = false;
   private int cleanupBlockIndex = 0;
   private int cleanupBlockAttempts = 0;
   private List<BlockPos> cleanupBlocks = new ArrayList<>();
   private boolean cleanupInitialized = false;
   /** Ticks in a row the player has spent off the platform during the cleanup; see {@link #offPlatform}. */
   private int offPlatformTicks = 0;
   /** How long off the platform before the cleanup gives up on the box: half a second, so a knock that lands back on it does not. */
   private static final int OFF_PLATFORM_TICKS = 10;
   private int reEnableStage = 0;
   private final ElytraTakeoff takeoff = new ElytraTakeoff();
   private boolean takeoffInitialized = false;
   private static final double ROTATION_ALIGN_EPS = 3.0;
   private static final double ROTATION_TURN_SPEED = 120.0;
   private static final int TAKEOFF_TIMEOUT_TICKS = 280;
   // Sideways blocks travelled per block of fall above which the first scaffold
   // block would land behind the player instead of under him.
   private static final double DROP_MAX_DRIFT_PER_BLOCK = 0.45;

   public AutoFlyingRegear() {
      super(HunterBuddyAddon.LOGISTICS_CATEGORY, "AutoFlyingRegear", "Automatically creates a platform and restocks rockets/elytras from ender chest");
   }

   public void onActivate() {
      this.state = AutoFlyingRegear.FlyingRegearState.IDLE;
      this.currentRun = null;
      this.pendingOutcome = null;
      this.rocketRateBaseUsed = SessionStats.get().rocketsUsed();
      this.rocketRateBaseMs = System.currentTimeMillis();
      this.stateBeforeEating = null;
      this.timer = 0;
      this.platformCenter = null;
      this.placedBlocks.clear();
      this.clearingGivenUp.clear();
      this.clearingAttempts = 0;
      this.pendingBlocks.clear();
      this.currentBlockIndex = 0;
      this.disabledModules.clear();
      this.shulkerPlacePos = null;
      this.echestPos = null;
      this.processingElytras = true;
      this.extra = AutoFlyingRegear.Extra.NONE;
      this.transferSlotIndex = 0;
      this.transferStep = 0;
      this.savedChestplate = ItemStack.EMPTY;
      this.placementAttempts = 0;
      this.stepAsideTicks = 0;
      this.platformPlacementAttempts.clear();
      this.shulkerEnderSlot = -1;
      this.processedShulkers.clear();
      this.rocketShulkersTaken = 0;
      this.stateTickCounter = 0;
      this.lastState = AutoFlyingRegear.FlyingRegearState.IDLE;
      this.scaffoldWaitTicks = 0;
      this.wallBuildPhase = 0;
      this.wallsStarted = false;
      this.releaseBaritonePlacement();
      this.runAborted = false;
      this.wallMissingLast = -1;
      this.wallNoProgressRounds = 0;
      this.wallMineQueued.clear();
      this.lowSupplyTicks = 0;
      this.savedYaw = 0.0F;
      this.currentClearingPos = null;
      this.clearingProgress = 0;
      this.savedPitch = 0.0F;
      this.hadBaritoneGoal = false;
      this.lavaPostponeTicks = 0;
      this.savedBaritoneCommand = null;
      this.reEnableStage = 0;
      this.takeoff.cancel();
      this.takeoffInitialized = false;
      this.cleanupBlockIndex = 0;
      this.cleanupBlockAttempts = 0;
      this.cleanupBlocks.clear();
      this.cleanupInitialized = false;
      this.offPlatformTicks = 0;
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
      this.autoEatSyncModule = Modules.get().get(AutoEatSync.class);
      if (this.mlepMine != null) {
         this.mlepMineWasActive = this.mlepMine.isActive();
      }

      this.flightModules.put("Replenish", Modules.get().get(Replenish.class));
      this.flightModules.put("AreaLoader", Modules.get().get(AreaLoader.class));
      this.flightModules.put("RocketFly", Modules.get().get(RocketFly.class));
      this.flightModules.put("WaypointFollower", Modules.get().get(WaypointFollower.class));
      this.flightModules.put("ElytraBounce", Modules.get().get(ElytraBounce.class));
      this.flightModules.put("Pitch40", Modules.get().get(Pitch40.class));
      this.flightModules.put("Pitch40Classic", Modules.get().get(Pitch40Classic.class));
      this.flightModules.put("TrailFollower", Modules.get().get(TrailFollower.class));
      this.flightModules.put("ElytraRecast", Modules.get().get(ElytraRecast.class));
      this.flightModules.put("ElytraFly", Modules.get().get(ElytraFly.class));
      if ((Boolean)this.debugMessages.get()) {
         this.info("AutoFlyingRegear activated - monitoring inventory", new Object[0]);
      }
   }

   public void onDeactivate() {
      this.releaseBaritonePlacement();
      this.endRun("aborted");
      this.reEnableFlightModules();
      if (!this.savedChestplate.isEmpty() && (Boolean)this.swapToChestplate.get()) {
        this.restoreChestplate();
      }

      if (this.mlepScaffold != null && this.mlepScaffold.isActive()) {
         this.mlepScaffold.setRegearMode(false);
         this.mlepScaffold.toggle();
      }

      // Switched off mid-mending, AutoEXPPlus would otherwise stay on for good — this module
      // borrowed it and has to give it back however the regear ends, not only when it ends
      // well.
      this.restoreAutoExp();

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
      this.clearingGivenUp.clear();
      this.clearingAttempts = 0;
      this.pendingBlocks.clear();
      this.wallLayer = 0;
      this.wallBuildPhase = 0;
      this.wallsStarted = false;
      this.releaseBaritonePlacement();
      this.runAborted = false;
      this.wallMissingLast = -1;
      this.wallNoProgressRounds = 0;
      this.wallMineQueued.clear();
      this.lowSupplyTicks = 0;
      this.cleanupBlocks.clear();
      this.cleanupBlockIndex = 0;
      this.cleanupInitialized = false;
      this.lavaPostponeTicks = 0;
   }

   private boolean isAutoEating() {
      if (this.autoEatModule != null && this.autoEatModule.isActive() && this.autoEatModule.eating) {
         return true;
      }

      return this.autoEatSyncModule != null && this.autoEatSyncModule.isActive() && this.autoEatSyncModule.isEating();
   }

   public boolean isRunning() {
      return this.isActive() && this.state != AutoFlyingRegear.FlyingRegearState.IDLE;
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
                  // The obsidian refill breaks up to six chests one after another, which can run past thirty
                  // seconds; each of its steps has its own timeout, so it needs no axe either.
                  case TOPPING_UP_OBSIDIAN:
                  // Mending an elytra from empty runs through dozens of bottles, one throw
                  // at a time. It belongs with the other states that are slow by nature, not
                  // under a thirty-second axe; its own stall detector ends it.
                  case REPAIRING_ELYTRA:
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
                     this.forceCompleteStuckState();
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
                        this.stepAsideTicks = 0;
                        if (this.boxIsWhole()) {
                           this.state = AutoFlyingRegear.FlyingRegearState.CLEARING_ECHEST_AREA;
                        } else {
                           this.wallsStarted = false;
                           if ((Boolean)this.debugMessages.get()) {
                              this.warning("Box still open after the timeout - restarting wall construction", new Object[0]);
                           }
                        }
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

            if (this.state != AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM) {
               this.releaseBaritonePlacement();
               this.centerGoalTick = -1;
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
                  case REPAIRING_ELYTRA:
                     this.handleRepairingElytra();
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
                  case TOPPING_UP_OBSIDIAN:
                     this.handleToppingUpObsidian();
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

   private void forceCompleteStuckState() {
      if ((Boolean)this.debugMessages.get()) {
         this.event("timeout in " + this.getPhaseLabel().toLowerCase(java.util.Locale.ROOT) + ", forcing on");
         this.error("Unexpected state timeout in " + this.state + " after " + String.format("%.1f", this.stateTickCounter / 20.0) + " seconds. Force completing...", new Object[0]);
      }

      this.abortRun();
   }

   private void abortRunBecause(String reason) {
      this.event("regear abandoned: " + reason);
      if ((Boolean)this.debugMessages.get()) {
         this.warning("Regear abandoned in " + this.state + " - " + reason, new Object[0]);
      }

      this.abortRun();
   }

   private void abortRun() {
      if (this.state == AutoFlyingRegear.FlyingRegearState.CREATING_INITIAL_PLATFORM) {
         this.platformBuildAborted = true;
      }

      this.platformPlacementAttempts.clear();
      this.runAborted = true;
      this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
      this.stateTickCounter = 0;
      this.timer = 0;
   }

   private void stopOutOfSupplies() {
      String reason = this.lowSupplyReason();
      this.event("nothing left to regear: " + reason);
      if ((Boolean)this.debugMessages.get()) {
         this.warning("Nothing left to regear (" + reason + ") - staying in the box", new Object[0]);
      }

      this.disabledModules.clear();
      if (this.outOfSupplies.get() == AutoFlyingRegear.OutOfSuppliesAction.DISCONNECT) {
         this.mc.player.networkHandler.onDisconnect(
            new net.minecraft.network.packet.s2c.common.DisconnectS2CPacket(
               net.minecraft.text.Text.literal("[AutoFlyingRegear] nothing left to regear (" + reason + ")")
            )
         );
         return;
      }

      this.toggle();
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
         boolean lowSupplyNow = this.shouldTriggerRegear();
         if (lowSupplyNow) {
            if (this.lowSupplyTicks == 0) {
               this.lowSupplyFirstReason = this.lowSupplyReason();
            }

            this.lowSupplyTicks++;
         } else if (this.lowSupplyTicks > 0) {
            if ((Boolean)this.debugMessages.get()) {
               this.info(
                  "Supplies looked low for " + this.lowSupplyTicks + " ticks (" + this.lowSupplyFirstReason + ") and came back - no regear",
                  new Object[0]
               );
            }

            this.lowSupplyTicks = 0;
         }

         if (this.lowSupplyTicks >= 20) {
            // Never regear over lava, ever, and no time cap: the bot flies the Nether fine, so
            // when there is lava (or unreadable ground) below we simply do not start the regear —
            // Baritone keeps flying until real, solid (or water) ground is under us, and only then
            // do we drop and build. Building a box on a lava lake was the wrong idea; it is gone.
            AutoFlyingRegear.GroundScan ground = this.scanGroundBelow();
            if (ground.kind() == AutoFlyingRegear.GroundKind.LAVA || ground.kind() == AutoFlyingRegear.GroundKind.UNKNOWN) {
               if (this.lavaPostponeTicks == 0) {
                  this.event("lava below, letting Baritone fly on to solid ground");
               }

               if (this.lavaPostponeTicks % 400 == 0 && (Boolean)this.debugMessages.get()) {
                  this.info(
                     "Low on supplies but lava/unknown ground below (" + this.lowSupplyFirstReason + ") - letting the flight continue until solid ground is under us",
                     new Object[0]
                  );
               }

               this.lavaPostponeTicks++;
               return;
            }

            BlockPos groundHere = this.mc.player.getBlockPos().down();
            BlockState groundHereState = this.mc.world.getBlockState(groundHere);
            if (!groundHereState.isReplaceable()
               && groundHereState.isSolidBlock(this.mc.world, groundHere)
               && this.hasLavaNearPlatform(groundHere)) {
               if (this.lavaPostponeTicks == 0) {
                  this.event("lava next to the ground here, letting Baritone fly on");
               }

               if (this.lavaPostponeTicks % 400 == 0 && (Boolean)this.debugMessages.get()) {
                  this.info(
                     "Low on supplies but the ground here has lava next to it (" + this.lowSupplyFirstReason + ") - letting the flight continue",
                     new Object[0]
                  );
               }

               this.lavaPostponeTicks++;
               return;
            }

            this.beginRun();
            this.lavaPostponeTicks = 0;
            if ((Boolean)this.debugMessages.get()) {
               this.info("Low on supplies (" + this.lowSupplyFirstReason + ") - initiating AutoFlyingRegear sequence", new Object[0]);
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
            if ((Boolean)this.debugMessages.get()) {
               this.info("Chestplate from slot " + chestplate.slot() + " to the chest, the elytra goes to slot " + chestplate.slot(), new Object[0]);
            }

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
      if (this.mc.player.isInLava()) {
         this.abortRunBecause("in lava while dropping, giving the flight back to Baritone");
         return;
      }

      if (!blockState.isReplaceable() && blockState.isSolidBlock(this.mc.world, beneath)) {
         if (this.hasLavaNearPlatform(beneath)) {
            this.abortRunBecause("lava next to the ground at " + beneath.toShortString());
            return;
         }

         this.finishScaffoldLanding(beneath, "Detected solid ground at " + beneath.toShortString() + " - centering");
         return;
      }

      if (this.stateTickCounter > 400) {
         // Never force a platform onto lava, even as a timeout fallback. We only commit the regear
         // over solid ground, so if there is lava directly below here the fall has drifted over a
         // lake; keep falling toward the solid ground rather than laying a box on the lava.
         if (this.mc.world.getBlockState(beneath).getFluidState().isIn(FluidTags.LAVA)) {
            this.stateTickCounter = 0;
            return;
         }

         if ((Boolean)this.debugMessages.get()) {
            this.warning("Falling timeout - forcing platform creation", new Object[0]);
         }

         // The platform is the floor block, feet-1, like every other finishScaffoldLanding call
         // (Detected solid ground, the scaffold hits). Passing the feet block here built the box one
         // block too high on this fallback, which stacked a second roof over a box left from before.
         this.finishScaffoldLanding(this.mc.player.getBlockPos().down(), "Falling timeout - forcing platform");
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
            // The idle phase has already announced "will scaffold at current
            // position" for exactly this case; waiting here for a height a fall
            // can only move away from was a two-hundred-block free fall onto
            // the ground. Below the band the drop places where it is, the way
            // the Nether branch always has.
            canStartScaffold = this.hasSpaceBelow() && !this.mc.player.isOnGround() && yVelocity < 0.02;
         } else {
            canStartScaffold = playerY <= activationHeight
               && this.hasSpaceBelow()
               && !this.mc.player.isOnGround()
               && yVelocity < 0.02;
         }
      }

      // The drop starts with the glide's forward speed still on the player,
      // and a block placed straight below is behind him by the time he falls
      // to its level -- the 22:26 log showed three obsidian hung in the air
      // along the curve before one finally caught him. Waiting for the speed
      // itself to die (the old 0.08 b/t gate) took some seven seconds, longer
      // than the free fall to the ground, so the block was never placed in the
      // air at all. What decides whether a block catches him is the sideways
      // distance covered per block of fall: once that is under about half a
      // block, a column aimed one block ahead (driftCompensatedLandingPos) is
      // under his feet when he gets there. At free-fall speed that takes ticks,
      // not seconds.
      Vec3d dropVelocity = this.mc.player.getVelocity();
      double dropHSpeed = Math.hypot(dropVelocity.x, dropVelocity.z);
      double driftPerBlock = dropHSpeed * this.ticksPerBlockOfFall();
      boolean driftTooHigh = driftPerBlock > DROP_MAX_DRIFT_PER_BLOCK;
      AutoFlyingRegear.GroundScan dropGround = this.scanGroundBelow();
      if (dropGround.kind() == AutoFlyingRegear.GroundKind.LAVA) {
         this.forceCompleteStuckState();
         return;
      }

      if (canStartScaffold && driftTooHigh) {
         if (this.stateTickCounter % 20 == 0 && (Boolean)this.debugMessages.get()) {
            this.info("Waiting out forward momentum (drift " + String.format("%.2f", driftPerBlock)
               + " blocks per block of fall)", new Object[0]);
         }

         return;
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
      // Aim the fallback block, and the "did it land" check, at the column the
      // player will be over when he has fallen one more block, so the platform
      // centre is the block that actually caught him.
      BlockPos checkPos = this.driftCompensatedLandingPos();
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

   /**
    * How many ticks the player needs to fall one block at his current speed.
    * The descent is taken as at least 0.1 b/t so a hover at the top of the
    * drop never divides by zero or predicts an endless glide.
    */
   private double ticksPerBlockOfFall() {
      double vy = Math.min(-0.1, this.mc.player.getVelocity().y);
      return 1.0 / -vy;
   }

   /**
    * The block under the column the player will occupy after falling one more
    * block: straight below his feet, shifted by the sideways distance covered
    * in that time, at most one block in x and z. A block placed here is under
    * him when he reaches its level; one placed straight below is behind him.
    */
   private BlockPos driftCompensatedLandingPos() {
      Vec3d velocity = this.mc.player.getVelocity();
      double ticks = this.ticksPerBlockOfFall();
      int dx = MathHelper.clamp((int)Math.round(velocity.x * ticks), -1, 1);
      int dz = MathHelper.clamp((int)Math.round(velocity.z * ticks), -1, 1);
      return this.mc.player.getBlockPos().add(dx, -1, dz);
   }

   /**
    * Looks straight down from the player's feet for the first block that is not
    * open air. Fluids are checked before solids because lava and water report
    * isReplaceable() and would otherwise pass for empty space. Reaching the
    * bottom of the world without a hit means the column is unloaded or void.
    */
   private AutoFlyingRegear.GroundScan scanGroundBelow() {
      BlockPos feet = this.mc.player.getBlockPos();
      int bottomY = this.mc.world.getBottomY();

      for (int y = feet.getY() - 1; y >= bottomY; y--) {
         BlockPos probe = new BlockPos(feet.getX(), y, feet.getZ());
         BlockState state = this.mc.world.getBlockState(probe);
         int distance = feet.getY() - y;
         if (!state.getFluidState().isEmpty()) {
            AutoFlyingRegear.GroundKind kind = state.getFluidState().isIn(FluidTags.LAVA)
               ? AutoFlyingRegear.GroundKind.LAVA
               : AutoFlyingRegear.GroundKind.WATER;
            return new AutoFlyingRegear.GroundScan(kind, distance);
         }

         if (!state.isReplaceable()) {
            return new AutoFlyingRegear.GroundScan(AutoFlyingRegear.GroundKind.SOLID, distance);
         }
      }

      return new AutoFlyingRegear.GroundScan(AutoFlyingRegear.GroundKind.UNKNOWN, feet.getY() - bottomY);
   }

   private boolean hasLavaNearPlatform(BlockPos platform) {
      for (int dx = -2; dx <= 4; dx++) {
         for (int dz = -2; dz <= 4; dz++) {
            for (int dy = 0; dy <= 3; dy++) {
               BlockPos probe = platform.add(dx, dy, dz);
               if (this.mc.world.getBlockState(probe).getFluidState().isIn(FluidTags.LAVA)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private List<ItemEntity> itemEntitiesNear(BlockPos anchor, double radius) {
      BlockPos center = anchor != null ? anchor : this.mc.player.getBlockPos();
      Box box = new Box(center).expand(radius);
      return this.mc.world.getEntitiesByClass(ItemEntity.class, box, e -> true);
   }

   private boolean hasLavaBelow(BlockPos pos, int depth) {
      if (pos == null) {
         return false;
      }

      for (int dy = 0; dy <= depth; dy++) {
         if (this.mc.world.getBlockState(pos.down(dy)).getFluidState().isIn(FluidTags.LAVA)) {
            return true;
         }
      }

      return false;
   }

   private int freeInventorySlots() {
      int freeSlots = 0;

      for (int i = 0; i < 36; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            freeSlots++;
         }
      }

      return freeSlots;
   }

   private String locateShulkerState() {
      ItemStack hotbarStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
      if (this.isShulkerBox(hotbarStack.getItem())) {
         return "hand";
      }

      for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
         if (this.isShulkerBox(this.mc.player.getInventory().getStack(i).getItem())) {
            return "inv slot " + i;
         }
      }

      for (ItemEntity entity : this.itemEntitiesNear(this.shulkerPlacePos, 3.0)) {
         if (this.isShulkerBox(entity.getStack().getItem())) {
            return "ground";
         }
      }

      return "MISSING";
   }

   private boolean shulkerAccountedFor() {
      return !"MISSING".equals(this.locateShulkerState());
   }

   private void logShulkerState(String step) {
      if (!(Boolean)this.debugMessages.get()) {
         return;
      }

      int freeSlots = this.freeInventorySlots();
      int groundItems = this.itemEntitiesNear(this.shulkerPlacePos, 3.0).size();
      boolean lavaBelow = this.hasLavaBelow(this.shulkerPlacePos, 4) || this.hasLavaBelow(this.platformCenter, 4);
      this.info(
         "shulker@" + step + ": freeSlots " + freeSlots + ", groundItems " + groundItems + " within 3, lavaBelow " + lavaBelow + ", shulker " + this.locateShulkerState(),
         new Object[0]
      );
   }

   private void holdBaritonePlacement() {
      if (!this.placementHeld) {
         this.savedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
         BaritoneAPI.getSettings().allowPlace.value = false;
         this.placementHeld = true;
      }
   }

   private void releaseBaritonePlacement() {
      if (this.placementHeld) {
         BaritoneAPI.getSettings().allowPlace.value = this.savedAllowPlace;
         this.placementHeld = false;
      }
   }

   private void handleCenteringOnPlatform() {
      if (this.mc.player.isInLava()) {
         this.releaseMovementKeys();
         this.abortRunBecause("in lava while centering, giving the flight back to Baritone");
         return;
      }

      this.holdBaritonePlacement();
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
      boolean atPlatformLevel = this.mc.player.getBlockPos().getY() >= this.platformCenter.getY() + 1;
      if (distance < 0.2 && atPlatformLevel) {
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
      } else if (!inAir && (this.centerGoalTick < 0 || this.stateTickCounter - this.centerGoalTick <= 5)) {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone != null) {
            baritone.getCommandManager().execute("cancel");
            baritone.getCommandManager()
               .execute(
                  "goto " + this.platformCenter.getX() + " " + (this.platformCenter.getY() + 1) + " " + this.platformCenter.getZ()
               );
            if (this.centerGoalTick < 0) {
               this.centerGoalTick = this.stateTickCounter;
            }

            if ((Boolean)this.debugMessages.get()) {
               this.info("Baritone goto center on foot - distance: " + String.format("%.2f", distance), new Object[0]);
            }
         }
      } else {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         boolean baritoneActive = !inAir && baritone != null && baritone.getPathingBehavior().isPathing();
         if (!inAir && !atPlatformLevel && !baritoneActive && this.centerGoalTick >= 0 && this.stateTickCounter - this.centerGoalTick > 20) {
            this.releaseMovementKeys();
            this.abortRunBecause("the platform cannot be reached on foot, regearing somewhere else");
            return;
         }

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
            BlockPos beneath = this.mc.player.getBlockPos().down();
            BlockState beneathState = this.mc.world.getBlockState(beneath);
            boolean reAnchored = false;
            if (!this.wallsStarted
               && this.mc.player.isOnGround()
               && !this.mc.player.isTouchingWater()
               && !this.mc.player.isInLava()
               && beneath.getY() < this.platformCenter.getY()
               && !beneathState.isReplaceable()
               && beneathState.isSolidBlock(this.mc.world, beneath)
               && !this.hasLavaNearPlatform(beneath)) {
               this.platformCenter = beneath;
               reAnchored = true;
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Centering timeout, proceeding anyway - platform set to the block under the feet at " + beneath.toShortString(), new Object[0]);
               }
            } else if ((Boolean)this.debugMessages.get()) {
               this.warning("Centering timeout, proceeding anyway", new Object[0]);
            }

            if (baritone != null) {
               baritone.getCommandManager().execute("cancel");
            }

            this.releaseMovementKeys();
            if (!reAnchored && !atPlatformLevel) {
               this.abortRunBecause("could not get back on the platform, regearing somewhere else");
               return;
            }

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
         this.clearFireInBox();
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

            this.wallsStarted = false;
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
               this.event("block skipped at " + pos.toShortString());
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
               // Platform blocks go down fast: this is the layer that keeps the bot off the ground, so it
               // must form before the fall does, not on the slow wall cadence.
               this.timer = Math.max(2, (Integer)this.placeDelay.get() / 4);
            } else {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Failed to send placement packet, retrying...", new Object[0]);
               }

               this.placementAttempts++;
               this.timer = (Integer)this.placeDelay.get() * 2;
            }

            int totalAttempts = this.platformPlacementAttempts.merge(pos, 1, Integer::sum);
            if (totalAttempts >= MAX_PLATFORM_PLACEMENT_ATTEMPTS) {
               BlockPos above = pos.up();
               BlockState aboveState = this.mc.world.getBlockState(above);
               if (!aboveState.isReplaceable() && !aboveState.isAir() && this.mlepMine != null && this.mlepMine.isActive()) {
                  Vec3d playerPos = this.mc.player.getEntityPos();
                  Vec3d blockCenter = Vec3d.ofCenter(above);
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

                  ((MlepMine)this.mlepMine).queueMiningData(((MlepMine)this.mlepMine).new MiningData(above, breakDirection));
                  if ((Boolean)this.debugMessages.get()) {
                     this.info("Platform spot " + pos.toShortString() + " stuck after " + totalAttempts + " attempts, mining " + above.toShortString(), new Object[0]);
                  }

                  this.platformPlacementAttempts.remove(pos);
               } else {
                  if ((Boolean)this.debugMessages.get()) {
                     this.warning("Platform spot " + pos.toShortString() + " stuck after " + totalAttempts + " attempts with nothing to mine, force completing", new Object[0]);
                  }

                  this.forceCompleteStuckState();
                  return;
               }
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
               this.platformPlacementAttempts.clear();
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
                  this.wallsStarted = false;
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

   /** Puts out any fire in the box footprint. Fire burns the bot while it builds and, being non-solid,
    *  gives the air-place raytrace nothing to hit - which is how a cell ends up skipped. Breaks in one hit. */
   private void clearFireInBox() {
      if (this.platformCenter == null || this.mc.interactionManager == null || this.mc.world == null) return;
      for (int dx = -1; dx <= 2; dx++) {
         for (int dz = -1; dz <= 2; dz++) {
            for (int dy = 0; dy <= 3; dy++) {
               BlockPos p = this.platformCenter.add(dx, dy, dz);
               if (this.mc.world.getBlockState(p).getBlock() instanceof net.minecraft.block.AbstractFireBlock) {
                  this.mc.interactionManager.attackBlock(p, Direction.DOWN);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
               }
            }
         }
      }
   }

   /** Every cell the box should have, that is still air or replaceable - across the phases actually in scope. */
   private java.util.List<BlockPos> collectMissingBoxBlocks() {
      java.util.List<BlockPos> missing = new java.util.ArrayList<>();
      missing.addAll(this.collectMissingBlocks(this.getWallRingPositions(1)));
      if (this.getMaxWallBuildPhase() >= 2) {
         missing.addAll(this.collectMissingBlocks(this.getWallRingPositions(2)));
         missing.addAll(this.collectMissingBlocks(this.getRoofPositions()));
      }
      return missing;
   }

   private void handleCreatingWalls() {
      if (!this.wallsStarted) {
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
         this.verifyRounds = 0;
         this.clearFireInBox();
         this.wallsStarted = true;
      }

      if (this.pendingBlocks.isEmpty() && this.timer == 0) {
         this.pendingBlocks.addAll(this.collectMissingWallPhaseBlocks());
         if (this.pendingBlocks.isEmpty()) {
            if (this.advanceWallBuildPhase()) {
               return;
            }

            if (!this.boxIsWhole()) {
               int missing = this.collectMissingBoxBlocks().size();
               if (this.wallMissingLast < 0 || missing < this.wallMissingLast) {
                  this.wallMissingLast = missing;
                  this.wallNoProgressRounds = 0;
               } else if (++this.wallNoProgressRounds > 3) {
                  this.abortRunBecause(
                     "the box is still missing " + missing + " block(s) and four rounds filled none of them, regearing somewhere else"
                  );
                  return;
               }

               this.wallBuildPhase = 0;
               this.wallLayer = 0;
               this.currentBlockIndex = 0;
               this.timer = (Integer)this.placeDelay.get();
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
            if (this.cellFilled(pos)) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Block placed (" + this.getWallPhaseName() + ") at " + pos.toShortString(), new Object[0]);
               }

               if (!this.placedBlocks.contains(pos.toImmutable())) {
                  this.placedBlocks.add(pos.toImmutable());
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.stepAsideTicks = 0;
               this.timer = (Integer)this.placeDelay.get();
               return;
            }

            if (!this.withinPlacementReach(pos)) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Too far from " + pos.toShortString() + " to place it, going back to the platform", new Object[0]);
               }

               this.placementAttempts = 0;
               this.stepAsideTicks = 0;
               this.releaseMovementKeys();
               this.state = AutoFlyingRegear.FlyingRegearState.CENTERING_ON_PLATFORM;
               this.timer = 0;
               return;
            }

            BlockState inTheWay = this.mc.world.getBlockState(pos);
            if (!inTheWay.isReplaceable()
               && !inTheWay.isAir()
               && this.mlepMine != null
               && this.mlepMine.isActive()
               && this.wallMineQueued.add(pos.toImmutable())) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning(
                     "Wall cell " + pos.toShortString() + " is blocked by " + inTheWay.getBlock().getName().getString() + ", queueing it for mining",
                     new Object[0]
                  );
               }

               ((MlepMine)this.mlepMine).queueMiningData(((MlepMine)this.mlepMine).new MiningData(pos.toImmutable(), this.faceFromEye(pos)));
            }

            if (this.placementAttempts >= 10) {
               this.event("block skipped at " + pos.toShortString());
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Failed to place block after 10 attempts at " + pos.toShortString() + ", skipping", new Object[0]);
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.stepAsideTicks = 0;
               this.timer = (Integer)this.placeDelay.get() * 2;
               return;
            }

            if (this.playerOccupies(pos) && this.stepAsideTicks < 60) {
               this.stepAsideTicks++;
               this.stepAsideFrom(pos);
               this.timer = 1;
               return;
            }

            if (this.stepAsideTicks > 0) {
               this.releaseMovementKeys();
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

            // Verify every cell of the box holds a solid block before calling it done. A cell skipped
            // after ten failed attempts (fire in the way, no face to place against, knocked out of reach)
            // would otherwise leave a hole. Clear fire, then re-run the phases until nothing is missing:
            // the regear must never open the ender chest in a box that is still open.
            java.util.List<BlockPos> stillMissing = this.collectMissingBoxBlocks();
            if (!stillMissing.isEmpty()) {
               this.verifyRounds++;
               this.clearFireInBox();
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Box has " + stillMissing.size() + " missing block(s), verify pass " + this.verifyRounds, new Object[0]);
               }

               this.wallBuildPhase = 0;
               this.wallLayer = 0;
               this.pendingBlocks.clear();
               this.currentBlockIndex = 0;
               this.placementAttempts = 0;
               this.timer = (Integer)this.placeDelay.get();
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

   private Direction faceFromEye(BlockPos pos) {
      Vec3d playerPos = this.mc.player.getEntityPos();
      Vec3d blockCenter = Vec3d.ofCenter(pos);
      double dx = playerPos.x - blockCenter.x;
      double dy = playerPos.y + this.mc.player.getEyeHeight(this.mc.player.getPose()) - blockCenter.y;
      double dz = playerPos.z - blockCenter.z;
      if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) {
         return dx > 0.0 ? Direction.EAST : Direction.WEST;
      } else if (Math.abs(dz) > Math.abs(dy)) {
         return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
      } else {
         return dy > 0.0 ? Direction.UP : Direction.DOWN;
      }
   }

   private boolean cellFilled(BlockPos pos) {
      BlockState state = this.mc.world.getBlockState(pos);
      return !state.isReplaceable() && !state.isAir() && state.isSolidBlock(this.mc.world, pos);
   }

   private boolean withinPlacementReach(BlockPos pos) {
      return this.mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= 4.0;
   }

   private List<BlockPos> collectMissingBlocks(BlockPos[] positions) {
      List<BlockPos> missing = new ArrayList<>();

      for (BlockPos pos : positions) {
         if (!this.cellFilled(pos)) {
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
      // The encapsule setting decides how far the box goes: the phases are wall
      // layer 1, wall layer 2 and the roof (see collectMissingWallPhaseBlocks).
      return (Boolean)this.encapsule.get() ? 2 : 0;
   }

   private boolean advanceWallBuildPhase() {
      if (this.wallBuildPhase < this.getMaxWallBuildPhase()) {
         this.wallBuildPhase++;
         this.wallLayer = this.wallBuildPhase;
         this.pendingBlocks.clear();
         this.currentBlockIndex = 0;
         this.placementAttempts = 0;
         this.stepAsideTicks = 0;
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
         this.platformCenter.add(1, 1, 1),
         this.platformCenter.add(0, 2, 0),
         this.platformCenter.add(1, 2, 0),
         this.platformCenter.add(0, 2, 1),
         this.platformCenter.add(1, 2, 1)
      };
      if (this.currentClearingPos != null) {
         BlockState blockState = this.mc.world.getBlockState(this.currentClearingPos);
         if (!blockState.isAir() && !blockState.isReplaceable() && blockState.getBlock() != Blocks.ENDER_CHEST) {
            Vec3d targetVec = Vec3d.ofCenter(this.currentClearingPos);
            float[] rotations = RotationUtils.getRotationsTo(this.mc.player.getEyePos(), targetVec);
            this.mc.player.setYaw(rotations[0]);
            this.mc.player.setPitch(rotations[1]);
            if (this.mlepMine != null && this.mlepMine.isActive()) {
               if (this.clearingProgress == 0 || this.clearingProgress % 40 == 0) {
                  ((MlepMine)this.mlepMine)
                     .queueMiningData(
                        ((MlepMine)this.mlepMine).new MiningData(this.currentClearingPos.toImmutable(), this.faceFromEye(this.currentClearingPos))
                     );
               }

               this.clearingProgress++;
            } else if (this.clearingProgress == 0) {
               this.mc.interactionManager.attackBlock(this.currentClearingPos, Direction.UP);
               this.mc.player.swingHand(Hand.MAIN_HAND);
               this.clearingProgress++;
            } else {
               this.mc.interactionManager.updateBlockBreakingProgress(this.currentClearingPos, Direction.UP);
               this.mc.player.swingHand(Hand.MAIN_HAND);
               this.clearingProgress++;
            }

            if (this.clearingProgress > 100) {
               if (++this.clearingAttempts < 3) {
                  this.clearingProgress = 0;
               } else {
                  if ((Boolean)this.debugMessages.get()) {
                     this.warning("Gave up clearing the block at " + this.currentClearingPos.toShortString() + " after 3 attempts", new Object[0]);
                  }

                  this.clearingGivenUp.add(this.currentClearingPos);
                  this.currentClearingPos = null;
                  this.clearingProgress = 0;
                  this.clearingAttempts = 0;
                  this.timer = 2;
                  return;
               }
            }

            this.timer = 0;
         } else {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Block cleared at " + this.currentClearingPos.toShortString(), new Object[0]);
            }

            this.currentClearingPos = null;
            this.clearingProgress = 0;
            this.clearingAttempts = 0;
            this.timer = 2;
         }
      } else {
         for (BlockPos pos : clearPositions) {
            if (this.clearingGivenUp.contains(pos)) {
               continue;
            }

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
            this.info(
               this.clearingGivenUp.isEmpty()
                  ? "Box interior cleared for ender chest placement"
                  : "Box interior cleared except " + this.clearingGivenUp.size() + " block(s) that would not break",
               new Object[0]
            );
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
               this.moveStack(eChestSlot, targetHotbarSlot);
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Moving ender chest to hotbar slot " + targetHotbarSlot, new Object[0]);
               }

               this.timer = (Integer)this.clickDelay.get();
            }
         } else {
            if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != targetHotbarSlot) {
               ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(targetHotbarSlot);
            }

            if (!this.centerOnPlatform("ender chest")) {
               this.timer = 2;
               return;
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

   /**
    * Moves a stray stack out of the shulker hotbar slot into an empty inventory slot while the
    * ender chest is open: two pick-up clicks on the container's own slot ids (the chest's rows
    * first, then the main inventory, then the hotbar). Returns true when a move was made and the
    * caller should wait a pass. With no empty slot the swap goes ahead as before, and the stack
    * ends up in the ender chest rather than lost.
    */
   private boolean clearShulkerSlot(GenericContainerScreenHandler handler, int syncId) {
      int hotbarIndex = (Integer)this.shulkerHotbarSlot.get();
      ItemStack inTheWay = this.mc.player.getInventory().getStack(hotbarIndex);
      if (inTheWay.isEmpty() || this.isShulkerBox(inTheWay.getItem())) {
         return false;
      }

      if (!this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
         return false;
      }

      int containerSlots = handler.getRows() * 9;
      int emptyMain = -1;
      for (int i = 9; i < 36; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            emptyMain = i;
            break;
         }
      }

      if (emptyMain == -1) {
         if ((Boolean)this.debugMessages.get()) {
            this.warning("Shulker slot holds " + inTheWay.getName().getString() + " and the inventory has no free slot to park it in - it will go into the ender chest", new Object[0]);
         }

         return false;
      }

      int hotbarId = containerSlots + 27 + hotbarIndex;
      int mainId = containerSlots + (emptyMain - 9);
      if ((Boolean)this.debugMessages.get()) {
         this.info("Parking " + inTheWay.getName().getString() + " x" + inTheWay.getCount() + " from shulker slot " + hotbarIndex + " into inventory slot " + emptyMain + " before taking the shulker", new Object[0]);
      }

      this.mc.interactionManager.clickSlot(syncId, hotbarId, 0, SlotActionType.PICKUP, this.mc.player);
      this.mc.interactionManager.clickSlot(syncId, mainId, 0, SlotActionType.PICKUP, this.mc.player);
      return true;
   }

   private void handleTakingShulker() {
      if (this.mc.currentScreen instanceof GenericContainerScreen screen) {
         GenericContainerScreenHandler var16 = (GenericContainerScreenHandler)screen.getScreenHandler();
         String targetType = this.restockLabel();
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
                        } else if (!this.processingElytras && item == this.restockItem()) {
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
                  this.afterElytras();
               } else if (this.extra != AutoFlyingRegear.Extra.NONE) {
                  this.nextExtra();
               } else {
                  this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
                  this.timer = (Integer)this.containerOpenDelay.get();
               }

               return;
            }

            // The hotbar swap puts whatever the shulker slot holds into the ender chest. The
            // slot is meant to be empty, but the block and rocket moves before this shuffle
            // things around: on 2026-09-07 a stack of rockets landed there and went into the
            // chest in place of the shulker. Park it in the inventory first, and swap next pass.
            if (this.clearShulkerSlot(var16, syncId)) {
               this.processedShulkers.remove(targetType + "_slot_" + this.shulkerEnderSlot);
               this.timer = (Integer)this.clickDelay.get();
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

            if (this.currentRun != null) {
               this.currentRun.shulkersTaken++;
            }

            if ("rocket".equals(targetType)) {
               this.rocketShulkersTaken++;
            }

            this.event("took " + targetType + " shulker " + (this.currentRun == null ? "" : "#" + this.currentRun.shulkersTaken));
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
         String targetType = this.restockLabel();
         if ((Boolean)this.debugMessages.get()) {
            this.info(targetType + " shulker confirmed in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
         }

         this.state = AutoFlyingRegear.FlyingRegearState.POSITIONING_FOR_SHULKER;
         this.timer = 0;
         this.placementAttempts = 0;
      }
   }

   private void handlePositioningForShulker() {
      if (this.stateTickCounter == 1 && (Boolean)this.debugMessages.get()) {
         this.info("Positioning for shulker placement", new Object[0]);
      }

      if (this.centerOnPlatform("shulker")) {
         this.state = AutoFlyingRegear.FlyingRegearState.ROTATING_FOR_SHULKER;
         this.timer = 10;
      }
   }

   private boolean centerOnPlatform(String label) {
      Vec3d targetCenter = Vec3d.ofCenter(this.platformCenter);
      Vec3d playerPos = this.mc.player.getEntityPos();
      double deltaX = targetCenter.x - playerPos.x;
      double deltaZ = targetCenter.z - playerPos.z;
      double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      if (horizontalDistance < 0.2) {
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
            this.info("Centered for " + label + " (distance: " + String.format("%.3f", horizontalDistance) + ")", new Object[0]);
         }

         return true;
      } else if (this.stateTickCounter <= 5) {
         if (baritone != null) {
            baritone.getCommandManager().execute("cancel");
            baritone.getCommandManager()
               .execute(
                  "goto " + this.platformCenter.getX() + " " + (this.platformCenter.getY() + 1) + " " + this.platformCenter.getZ()
               );
            if ((Boolean)this.debugMessages.get()) {
               this.info("Baritone goto for " + label + " - distance: " + String.format("%.2f", horizontalDistance), new Object[0]);
            }
         }

         return false;
      } else {
         boolean baritoneActive = baritone != null && baritone.getPathingBehavior().isPathing();
         if (!baritoneActive && this.stateTickCounter > 20) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Using manual movement for " + label + " positioning", new Object[0]);
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
               this.warning(label + " positioning timeout", new Object[0]);
            }

            if (baritone != null) {
               baritone.getCommandManager().execute("cancel");
            }

            this.mc.options.forwardKey.setPressed(false);
            this.mc.options.sneakKey.setPressed(false);
            return true;
         }

         return false;
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

         this.logShulkerState("placed");
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

   /**
    * With the extra's shulker still open, moves the smallest surplus stack of the item back into it,
    * one per call, so an overshoot ("goal 32, already had 20, a full 64 came over") collapses from two
    * slots to one. Only moves a stack when what remains stays at or above the goal, and never touches
    * the last stack. Returns true when it moved one (the caller waits a pass and re-checks), false when
    * the item is already in as few slots as it can be.
    */
   private boolean consolidateExtraIntoShulker(int syncId) {
      if (this.extra == AutoFlyingRegear.Extra.NONE) return false;
      Item item = this.restockItem();

      int total = 0;
      int stacks = 0;
      int smallestCount = Integer.MAX_VALUE;
      int smallestInvSlot = -1;
      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() != item) continue;
         total += stack.getCount();
         stacks++;
         if (stack.getCount() < smallestCount) {
            smallestCount = stack.getCount();
            smallestInvSlot = i;
         }
      }

      if (stacks <= 1 || smallestInvSlot == -1) return false;
      if (total - smallestCount < this.extraGoal()) return false;

      // Player inventory slot -> this container's slot id: shulker rows first, then the 27 main
      // slots (player 9..35), then the 9 hotbar slots (player 0..8). QUICK_MOVE on a player slot
      // while a container is open sends the whole stack into the container.
      int containerSlots = this.mc.player.currentScreenHandler.slots.size() - 36;
      int containerId = smallestInvSlot >= 9 ? containerSlots + (smallestInvSlot - 9) : containerSlots + 27 + smallestInvSlot;
      if ((Boolean)this.debugMessages.get()) {
         this.info("Consolidating: smallest stack of " + smallestCount + " " + this.restockLabel() + "s back into the shulker (kept " + (total - smallestCount) + ")", new Object[0]);
      }

      this.mc.interactionManager.clickSlot(syncId, containerId, 0, SlotActionType.QUICK_MOVE, this.mc.player);
      return true;
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
      this.logShulkerState("opened");
      if ((Boolean)this.debugMessages.get()) {
         this.info("Opening shulker", new Object[0]);
      }
   }

   private void handleTransferringItems() {
      if (this.mc.currentScreen instanceof HandledScreen<?> screen) {
         int var13 = this.mc.player.currentScreenHandler.syncId;
         String itemType = this.restockLabel() + "s";

         // REPAIR takes bottles where REPLACE takes elytras. The swap-in-place machinery
         // below is left completely alone: it is still what REPLACE runs, and mending has no
         // use for it since nothing is being exchanged.
         if (this.processingElytras && this.elytraMode.get() == AutoFlyingRegear.ElytraMode.REPAIR) {
            this.transferExperienceBottles(var13);
            return;
         }

         // A swap in progress owns the cursor. Nothing else may run until it is done: not the
         // goal check, not the slot scan. Closing the screen with an elytra on the cursor makes
         // the server drop that elytra into the inventory, which is how a bag set for four came
         // to hold seven, three of them worn out.
         if (this.processingElytras && this.transferStep != 0) {
            this.continueElytraSwap(var13);
            return;
         }

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
            if (this.extra != AutoFlyingRegear.Extra.NONE && this.countItem(this.restockItem()) >= this.extraGoal()) {
               // Whole stacks come over, so reaching a goal of 32 with 20 already carried leaves
               // 64 in one slot and the old 20 in another — two slots for what fits in one. While
               // the shulker is still open, push the smallest surplus stack back into it, as long
               // as what stays is still at or above the goal, until the item sits in as few slots
               // as possible.
               if (this.consolidateExtraIntoShulker(var13)) {
                  this.timer = (Integer)this.clickDelay.get();
                  return;
               }

               if ((Boolean)this.debugMessages.get()) {
                  this.info("Reached goal of " + this.extraGoal() + " " + itemType + ", stopping transfer", new Object[0]);
               }

               this.mc.player.closeHandledScreen();
               this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
               this.timer = (Integer)this.breakDelay.get();
               this.transferStep = 0;
               return;
            }

            int emptySlots = 0;

            for (int i = 0; i < 36; i++) {
               if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                  emptySlots++;
               }
            }

            if (emptySlots <= 1) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Inventory nearly full (only " + emptySlots + " empty slots), stopping " + itemType + " transfer", new Object[0]);
               }

               this.logShulkerState("transfer-full");
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
                        int emptyForElytra = 0;
                        for (int i = 0; i < 36; i++) {
                           if (this.mc.player.getInventory().getStack(i).isEmpty()) emptyForElytra++;
                        }

                        if (emptyForElytra <= 1) {
                           if ((Boolean)this.debugMessages.get()) {
                              this.info("Inventory nearly full, stopping elytra transfer with " + currentValidElytras + "/" + this.goalElytras.get(), new Object[0]);
                           }

                           this.logShulkerState("transfer-full");
                           this.mc.player.closeHandledScreen();
                           this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
                           this.timer = (Integer)this.breakDelay.get();
                           this.transferStep = 0;
                           return;
                        }

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

                     // Pick the fresh one up; continueElytraSwap takes it from here on the next
                     // passes, off the cursor. The old code kept these steps in this loop, keyed
                     // on the shulker slot — which is empty from the moment the pick-up lands, so
                     // the loop saw an empty slot, moved on and reset the step with the elytra
                     // still on the cursor.
                     this.mc.interactionManager.clickSlot(var13, this.transferSlotIndex, 0, SlotActionType.PICKUP, this.mc.player);
                     if ((Boolean)this.debugMessages.get()) {
                        this.info("Picking up good elytra from shulker slot " + this.transferSlotIndex, new Object[0]);
                     }

                     this.timer = (Integer)this.clickDelay.get();
                     this.transferStep = 1;
                     return;
                  }
               } else if (!this.processingElytras && item == this.restockItem()) {
                  this.mc.interactionManager.clickSlot(var13, this.transferSlotIndex, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                  if ((Boolean)this.debugMessages.get()) {
                     this.info("Transferred " + itemType + " from shulker slot " + this.transferSlotIndex, new Object[0]);
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

   /**
    * Finishes the exchange the loop started with a pick-up. Step 1: the fresh elytra is on the
    * cursor, click a worn one in the inventory to swap them. Step 2: the worn one is on the
    * cursor, put it into the shulker slot the fresh one came from. Driven by the cursor and the
    * remembered slot, never by what the shulker slot holds now.
    */
   private void continueElytraSwap(int syncId) {
      ItemStack cursor = this.mc.player.currentScreenHandler.getCursorStack();
      if (cursor.isEmpty()) {
         // The pick-up never landed (server refused it) or was already put away: nothing to finish.
         this.transferStep = 0;
         return;
      }

      if (this.transferStep == 1) {
         int brokenElytraSlot = this.findBrokenElytraInInventory();
         if (brokenElytraSlot == -1) {
            this.mc.interactionManager.clickSlot(syncId, this.transferSlotIndex, 0, SlotActionType.PICKUP, this.mc.player);
            if ((Boolean)this.debugMessages.get()) {
               this.info("No worn elytra left to exchange, fresh one back into shulker slot " + this.transferSlotIndex, new Object[0]);
            }

            this.timer = (Integer)this.clickDelay.get();
            this.transferStep = 0;
            this.transferSlotIndex++;
            return;
         }

         this.mc.interactionManager.clickSlot(syncId, brokenElytraSlot, 0, SlotActionType.PICKUP, this.mc.player);
         if ((Boolean)this.debugMessages.get()) {
            this.info("Swapping with broken elytra in slot " + brokenElytraSlot, new Object[0]);
         }

         this.timer = (Integer)this.clickDelay.get();
         this.transferStep = 2;
         return;
      }

      this.mc.interactionManager.clickSlot(syncId, this.transferSlotIndex, 0, SlotActionType.PICKUP, this.mc.player);
      if ((Boolean)this.debugMessages.get()) {
         this.info("Placed broken elytra back into shulker slot " + this.transferSlotIndex + ", gained 1 valid elytra", new Object[0]);
      }

      this.timer = (Integer)this.clickDelay.get();
      this.transferStep = 0;
      this.transferSlotIndex++;
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

         this.logShulkerState("broken");
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
      if (this.stateTickCounter == 1) {
         this.logShulkerState("pickup-start");
      }

      ItemStack hotbarStack = this.mc.player.getInventory().getStack((Integer)this.shulkerHotbarSlot.get());
      if (this.isShulkerBox(hotbarStack.getItem())) {
         if ((Boolean)this.debugMessages.get()) {
            this.info("Shulker picked up in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
         }

         this.logShulkerState("picked-up");
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

               this.moveStack(i, (Integer)this.shulkerHotbarSlot.get());
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

         if (this.stateTickCounter > 200 && !this.shulkerAccountedFor()) {
            this.warning("shulker lost — no shulker in hand, inventory or on the ground; aborting shulker return", new Object[0]);
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

                     this.moveStack(i, (Integer)this.shulkerHotbarSlot.get());
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

            this.logShulkerState("returned");
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
         // In REPAIR mode the bottles have just been pulled and the shulker is back in the
         // ender chest; mending happens here, before the rocket pass reopens it. Everything
         // downstream is untouched — the repair state hands back to this same rocket branch.
         if (this.elytraMode.get() == AutoFlyingRegear.ElytraMode.REPAIR) {
            this.state = AutoFlyingRegear.FlyingRegearState.REPAIRING_ELYTRA;
            this.repairHotbarSlot = -1;
            this.repairLastDurability = -1;
            this.repairLastBottles = -1;
            this.repairStallTicks = 0;
            this.timer = 0;
            return;
         }

         if ((Boolean)this.debugMessages.get()) {
            this.info("Elytra restocking complete", new Object[0]);
         }

         this.afterElytras();
      } else {
         int emptySlots = 0;

         for (int i = 0; i < 36; i++) {
            if (this.mc.player.getInventory().getStack(i).isEmpty()) {
               emptySlots++;
            }
         }

         if (this.extra != AutoFlyingRegear.Extra.NONE) {
            // One extra kind at a time: on to the next kind, or the rockets, once this one is
            // covered or the inventory is nearly full; otherwise another shulker of the same kind.
            if (this.countItem(this.restockItem()) >= this.extraGoal() || emptySlots <= 1) {
               this.nextExtra();
            } else {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Still short of " + this.restockLabel() + "s, looking for more shulkers", new Object[0]);
               }

               this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
               this.timer = (Integer)this.containerOpenDelay.get();
            }
         } else if (emptySlots > 1 && this.rocketShulkersTaken < 3) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Still have " + emptySlots + " empty slots after " + this.rocketShulkersTaken + " rocket shulkers, looking for one more", new Object[0]);
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

   /**
    * Pulls experience bottles out of the open shulker, one stack per pass.
    *
    * <p>Stops on whichever comes first: enough bottles carried, the shulker exhausted, or
    * the inventory down to its last free slot — the same guard the rocket pass uses, so a
    * regear can never end wedged with nowhere to put the rockets.
    */
   private void transferExperienceBottles(int syncId) {
      if (this.countExperienceBottles() >= (Integer)this.xpBottlesToTake.get()) {
         this.closeShulkerAfterTransfer("have " + this.countExperienceBottles() + " experience bottles");
         return;
      }

      int emptySlots = 0;

      for (int i = 0; i < 36; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            emptySlots++;
         }
      }

      if (emptySlots <= 1) {
         this.closeShulkerAfterTransfer("inventory nearly full");
         return;
      }

      while (this.transferSlotIndex < 27) {
         ItemStack stack = this.mc.player.currentScreenHandler.getSlot(this.transferSlotIndex).getStack();
         if (!stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE) {
            this.mc.interactionManager.clickSlot(syncId, this.transferSlotIndex, 0, SlotActionType.QUICK_MOVE, this.mc.player);
            if ((Boolean)this.debugMessages.get()) {
               this.info("Took experience bottles from shulker slot " + this.transferSlotIndex, new Object[0]);
            }

            this.timer = (Integer)this.clickDelay.get();
            this.transferStep = 0;
            this.transferSlotIndex++;
            return;
         }

         this.transferSlotIndex++;
      }

      this.closeShulkerAfterTransfer("shulker holds no more experience bottles");
   }

   private void closeShulkerAfterTransfer(String reason) {
      if ((Boolean)this.debugMessages.get()) {
         this.info("Stopping bottle transfer: " + reason, new Object[0]);
      }

      this.mc.player.closeHandledScreen();
      this.state = AutoFlyingRegear.FlyingRegearState.BREAKING_SHULKER;
      this.timer = (Integer)this.breakDelay.get();
      this.transferStep = 0;
   }

   /**
    * Mends worn elytras with experience bottles instead of swapping them out.
    *
    * <p>AutoEXPPlus does the actual work — it is where the throwing, the rotation and the
    * bottle replenish already live, and duplicating that here would mean two implementations
    * of one behaviour drifting apart. This module only holds the right elytra in hand and
    * decides when to move on.
    *
    * <p>Its settings are never read. They are private, and reading them would tie this to
    * thresholds the player is free to change; instead the elytra is watched directly. When
    * its durability stops climbing the elytra is finished, whatever {@code max-threshold}
    * happens to be.
    *
    * <p>Bottles are watched alongside it. AutoEXPPlus repairs armour before hands, so a
    * still elytra while bottles are being spent means the queue is simply elsewhere — ending
    * the wait there would abandon an elytra that was about to be mended. Only both being
    * still counts as done, which is also what an empty bottle stack looks like.
    */
   private void handleRepairingElytra() {
      if (this.mc.player == null) {
         return;
      }

      if (this.countValidElytras() >= (Integer)this.goalElytras.get()) {
         this.finishRepairing("goal of " + this.goalElytras.get() + " valid elytras reached");
         return;
      }

      int target = this.findRepairableElytraSlot();
      if (target == -1) {
         this.finishRepairing("no elytra left to mend");
         return;
      }

      // Bring it to the hand AutoEXPPlus looks at. It swaps to a bottle to throw and swaps
      // straight back, so the elytra stays in this slot between throws.
      if (this.repairHotbarSlot == -1) {
         this.repairHotbarSlot = this.holdForRepair(target);
         if (this.repairHotbarSlot == -1) {
            this.finishRepairing("no free hotbar slot to hold an elytra");
            return;
         }

         this.repairLastDurability = -1;
         this.repairLastBottles = -1;
         this.repairStallTicks = 0;
      }

      if (this.autoExpWasActive == null) {
         Module autoExp = Modules.get().get(AutoEXPPlus.class);
         if (autoExp == null) {
            this.finishRepairing("AutoEXPPlus not found");
            return;
         }

         this.autoExpWasActive = autoExp.isActive();
         if (!autoExp.isActive()) {
            autoExp.toggle();
         }
      }

      ItemStack held = this.mc.player.getInventory().getStack(this.repairHotbarSlot);
      if (held.getItem() != Items.ELYTRA) {
         this.repairHotbarSlot = -1;
         return;
      }

      int durability = held.getMaxDamage() - held.getDamage();
      int bottles = this.countExperienceBottles();

      if (durability > this.repairLastDurability || bottles < this.repairLastBottles) {
         this.repairStallTicks = 0;
      } else {
         this.repairStallTicks++;
      }

      this.repairLastDurability = durability;
      this.repairLastBottles = bottles;

      if (this.repairStallTicks >= (Integer)this.repairStallSeconds.get() * 20) {
         double percent = durability * 100.0 / held.getMaxDamage();
         if ((Boolean)this.debugMessages.get()) {
            this.info("Elytra stopped mending at " + (int)percent + "%, moving on", new Object[0]);
         }

         // Nothing is moving and this elytra is still short of the valid threshold, so no
         // other elytra would fare better: out of bottles, no Mending, or AutoEXPPlus set to
         // armour only. Carrying on would stall on each one in turn for the same reason.
         if (percent < ((Integer)this.elytraDurabilityThreshold.get()).intValue()) {
            this.finishRepairing(bottles == 0 ? "out of experience bottles" : "mending made no progress");
            return;
         }

         this.repairHotbarSlot = -1;
      }
   }

   /** Ends the repair phase, puts AutoEXPPlus back as it was, and resumes the rocket pass. */
   private void finishRepairing(String reason) {
      this.restoreAutoExp();
      this.repairHotbarSlot = -1;
      this.repairLastDurability = -1;
      this.repairLastBottles = -1;
      this.repairStallTicks = 0;
      this.event("mending done: " + reason);
      if ((Boolean)this.debugMessages.get()) {
         this.info("Elytra mending done (" + reason + ")", new Object[0]);
      }

      this.afterElytras();
   }

   /** Returns AutoEXPPlus to the state it was in, and only if this module changed it. */
   private void restoreAutoExp() {
      if (this.autoExpWasActive == null) {
         return;
      }

      Module autoExp = Modules.get().get(AutoEXPPlus.class);
      if (autoExp != null && autoExp.isActive() != this.autoExpWasActive) {
         autoExp.toggle();
      }

      this.autoExpWasActive = null;
   }

   /**
    * The worn elytra worth mending first, or -1.
    *
    * <p>The fullest one below the valid threshold, because it is the one closest to counting
    * and therefore the cheapest way to move the tally up.
    */
   private int findRepairableElytraSlot() {
      int best = -1;
      int bestDurability = -1;
      int threshold = (Integer)this.elytraDurabilityThreshold.get();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() != Items.ELYTRA) {
            continue;
         }

         int durability = stack.getMaxDamage() - stack.getDamage();
         if (durability * 100.0 / stack.getMaxDamage() >= threshold) {
            continue;
         }

         if (durability > bestDurability) {
            bestDurability = durability;
            best = i;
         }
      }

      return best;
   }

   /** Puts the stack in the selected hotbar slot and returns that slot, or -1. */
   private int holdForRepair(int invSlot) {
      int selected = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
      if (invSlot == selected) {
         return selected;
      }

      if (invSlot < 9) {
         ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(invSlot);
         this.mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(invSlot));
         return invSlot;
      }

      // Not the selected slot: that is whatever was being held, the sword KillAuraPlus keeps
      // there as often as not, and it would end up buried in the inventory where the aura cannot
      // see it. The shulker slot is this module's own and free by now, every shulker being back
      // in the ender chest.
      int hold = (Integer)this.shulkerHotbarSlot.get();
      this.moveStack(invSlot, hold);
      ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(hold);
      this.mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(hold));
      return hold;
   }

   private int countExperienceBottles() {
      int count = 0;

      for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.EXPERIENCE_BOTTLE) {
            count += stack.getCount();
         }
      }

      return count;
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
      if (!this.obsidianTopUpDecided) {
         // Once per regear, last, when everything else is restocked and before the box comes down: see
         // handleToppingUpObsidian. Decided here, where every way out of the restocks arrives, and marked before it
         // starts, so that a timeout inside it lands back here and goes on with the teardown instead of starting over.
         this.obsidianTopUpDecided = true;
         if (this.obsidianTopUpWanted()) {
            this.topUpStep = 0;
            this.topUpStepTicks = 0;
            this.state = AutoFlyingRegear.FlyingRegearState.TOPPING_UP_OBSIDIAN;
            this.timer = 0;
            return;
         }
      }

      this.totemToOffhand();
      if ((Boolean)this.swapToChestplate.get() && !this.savedChestplate.isEmpty()) {
         this.restoreChestplate();
         if ((Boolean)this.debugMessages.get()) {
            this.info("Restored elytra", new Object[0]);
         }
      }

      if (!this.runAborted && this.shouldTriggerRegear()) {
         this.stopOutOfSupplies();
         return;
      }

      this.state = AutoFlyingRegear.FlyingRegearState.CLEANUP;
      this.timer = 5;
      this.cleanupBlockIndex = 0;
   }

   /**
    * Whether this regear refills the obsidian before the box comes down: only when it is down to obsidian-refill-at,
    * in a box that is whole (it is what keeps the drops in), and with something to break - the ender chest the regear
    * placed, or one to spare over keep-ender-chests.
    */
   private boolean obsidianTopUpWanted() {
      if ((Integer)this.obsidianRefillTo.get() <= 0 || this.platformCenter == null || this.echestPos == null) {
         return false;
      }

      if (this.countItem(Items.OBSIDIAN) > (Integer)this.obsidianRefillAt.get()) {
         return false;
      }

      if (!this.boxIsWhole()) {
         this.warning("Obsidian is low but the box is not whole: no refill this time, the drops would fall out", new Object[0]);
         return false;
      }

      return this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST
         || this.countItem(Items.ENDER_CHEST) > (Integer)this.keepEnderChests.get();
   }

   /** Both wall rings and the roof standing, whatever the wall settings: the box the obsidian refill breaks chests in. */
   private boolean boxIsWhole() {
      return this.collectMissingBlocks(this.getWallRingPositions(1)).isEmpty()
         && this.collectMissingBlocks(this.getWallRingPositions(2)).isEmpty()
         && this.collectMissingBlocks(this.getRoofPositions()).isEmpty();
   }

   /**
    * Refills the obsidian up to obsidian-refill-to before the box comes down, out of ender chests: broken with a
    * pickaxe, one drops 8 obsidian and not itself. The ender chest the regear placed goes first, then more are placed
    * where it stood and broken in turn, at most max-ender-chests-broken of them and never below keep-ender-chests. All
    * of it inside the closed box, which keeps the drops within reach. Steps: 0 decide, 1 place a chest, 2 break it,
    * 3 wait for its obsidian.
    */
   private void handleToppingUpObsidian() {
      this.topUpStepTicks++;
      if (!this.standingInBox()) {
         // a chest broken at echestPos only drops within reach, and inside walls, from where the regear stands
         this.finishTopUp("pushed out of the box");
         return;
      }

      switch (this.topUpStep) {
         case 0 -> {
            if (this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
               this.startTopUpBreak();
               return;
            }

            int obsidian = this.countItem(Items.OBSIDIAN);
            int chests = this.countItem(Items.ENDER_CHEST);
            if (obsidian >= (Integer)this.obsidianRefillTo.get()
               || this.topUpChestsBroken >= (Integer)this.maxEnderChestsBroken.get()
               || chests <= (Integer)this.keepEnderChests.get()) {
               this.finishTopUp(obsidian < (Integer)this.obsidianRefillTo.get() && chests <= (Integer)this.keepEnderChests.get()
                  ? "no ender chest to spare over the " + this.keepEnderChests.get() + " kept"
                  : null);
               return;
            }

            this.topUpStep = 1;
            this.topUpStepTicks = 0;
         }
         case 1 -> {
            if (this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
               this.topUpChestsBroken++;
               this.startTopUpBreak();
               return;
            }

            if (this.topUpStepTicks > 60) {
               this.finishTopUp("could not place an ender chest to break");
               return;
            }

            // the same slot and the same placement the regear's own ender chest went through
            int slot = (Integer)this.eChestHotbarSlot.get();
            if (this.mc.player.getInventory().getStack(slot).getItem() != Items.ENDER_CHEST) {
               int from = InvUtils.find(new Item[]{Items.ENDER_CHEST}).slot();
               if (from == -1) {
                  this.finishTopUp("no ender chest left to break");
                  return;
               }

               this.moveStack(from, slot);
               this.timer = (Integer)this.clickDelay.get();
               return;
            }

            if (this.placeBlockAtHotbar(this.echestPos, slot)) {
               this.timer = (Integer)this.placeDelay.get();
            }
         }
         case 2 -> {
            if (this.mc.world.getBlockState(this.echestPos).getBlock() != Blocks.ENDER_CHEST) {
               this.topUpStep = 3;
               this.topUpStepTicks = 0;
               return;
            }

            if (this.topUpStepTicks > 160) {
               this.finishTopUp("could not break the ender chest");
               return;
            }

            // broken without a pickaxe, an ender chest drops nothing at all
            FindItemResult pickaxe = InvUtils.findInHotbar(itemStack -> itemStack.isIn(ItemTags.PICKAXES));
            if (!pickaxe.found()) {
               this.finishTopUp("no pickaxe in the hotbar");
               return;
            }

            if (this.mlepMine != null && this.mlepMine.isActive()) {
               // MlepMine takes the chest the way the cleanup hands it the box: queued once, then left to it. The
               // pickaxe goes in hand first, for an auto-swap that is off and mines with whatever is held; the
               // game's own tick sends the slot on before the break can finish, so the drop is the pickaxe's.
               if (!this.topUpBreakStarted) {
                  ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(pickaxe.slot());
               }

               // the queue drops a repeat of a block it is still working on, so this nudge only matters if it let go
               if (!this.topUpBreakStarted || this.topUpStepTicks % 40 == 0) {
                  ((MlepMine)this.mlepMine).queueMiningData(((MlepMine)this.mlepMine).new MiningData(this.echestPos, Direction.UP));
                  this.topUpBreakStarted = true;
               }

               return;
            }

            // no mine module to hand it to: by hand, as before
            if (((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != pickaxe.slot()) {
               ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(pickaxe.slot());
            }

            if (this.breakBlockWhenAligned(this.echestPos, Direction.UP, Vec3d.ofCenter(this.echestPos), !this.topUpBreakStarted)) {
               this.topUpBreakStarted = true;
            }
         }
         case 3 -> {
            int obsidian = this.countItem(Items.OBSIDIAN);
            if (this.countItem(Items.ENDER_CHEST) > this.topUpChestsBefore) {
               this.finishTopUp("the ender chest came back whole, the pickaxe has Silk Touch");
               return;
            }

            if (obsidian >= this.topUpObsidianBefore + 8) {
               this.topUpStep = 0;
               this.topUpStepTicks = 0;
            } else if (this.topUpStepTicks > 40) {
               if (obsidian > this.topUpObsidianBefore) {
                  this.topUpStep = 0;
                  this.topUpStepTicks = 0;
               } else {
                  this.finishTopUp("the ender chest's obsidian never reached the inventory");
               }
            }
         }
         default -> this.finishTopUp(null);
      }
   }

   /**
    * Inside the box's two by two, anywhere from its floor to under its roof: where the refill has to happen. The
    * height is a band and not one block, for a floor that is a slab, which puts the feet half a block lower, and for
    * a bump that settles again.
    */
   private boolean standingInBox() {
      double y = this.mc.player.getY();
      int dx = MathHelper.floor(this.mc.player.getX()) - this.platformCenter.getX();
      int dz = MathHelper.floor(this.mc.player.getZ()) - this.platformCenter.getZ();
      return y > this.platformCenter.getY() && y < this.platformCenter.getY() + 2.0 && dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
   }

   private void startTopUpBreak() {
      this.topUpObsidianBefore = this.countItem(Items.OBSIDIAN);
      this.topUpChestsBefore = this.countItem(Items.ENDER_CHEST);
      this.topUpBreakStarted = false;
      this.topUpStep = 2;
      this.topUpStepTicks = 0;
   }

   /** Ends the refill and goes on with the teardown; {@code problem}, when there is one, is said in chat. */
   private void finishTopUp(String problem) {
      int obsidian = this.countItem(Items.OBSIDIAN);
      if (problem != null) {
         this.warning("Obsidian refill stopped at " + obsidian + ": " + problem, new Object[0]);
      } else if ((Boolean)this.debugMessages.get()) {
         this.info("Obsidian refilled to " + obsidian + ", " + this.topUpChestsBroken + " ender chests broken besides the regear's", new Object[0]);
      }

      this.event("obsidian " + obsidian + (this.topUpChestsBroken > 0 ? ", " + this.topUpChestsBroken + " ender chests broken" : ""));
      this.state = AutoFlyingRegear.FlyingRegearState.RESTORING_ELYTRA;
      this.timer = 0;
   }

   /** Adds a placed block to the cleanup list once, if it is really there (solid, not air/replaceable). */
   private void addCleanupBlock(BlockPos pos) {
      BlockPos immutablePos = pos.toImmutable();
      if (this.cleanupBlocks.contains(immutablePos)) return;
      BlockState blockState = this.mc.world.getBlockState(immutablePos);
      if (!blockState.isAir() && !blockState.isReplaceable()) this.cleanupBlocks.add(immutablePos);
   }

   /**
    * Not standing on the platform any more: the body's centre more than half its width beyond the edges of the 2x2,
    * so that no part of it is over the platform, or the feet under the floor block, fallen through where it was.
    * Above the platform still counts as on it: a knock upwards comes back down on it.
    */
   private boolean offPlatform() {
      double x = this.mc.player.getX() - this.platformCenter.getX();
      double z = this.mc.player.getZ() - this.platformCenter.getZ();
      double half = this.mc.player.getWidth() / 2.0;
      return x < -half || x > 2.0 + half || z < -half || z > 2.0 + half
         || this.mc.player.getY() < this.platformCenter.getY();
   }

   private void handleCleanup() {
      if (this.platformCenter != null && this.offPlatform()) {
         // Off the platform while the box comes down: a ghast's fireball knocked the bot into the lava below once,
         // and the cleanup went on waiting for the rest of the box while the bot sat at the bottom until someone
         // stepped in. The user's rule: off the platform during the box breaking, stop and hand the flight on,
         // through the same takeoff the end of a cleanup starts, which hands the Nether to Baritone when
         // netherTakeoff says so; Baritone gets out of the lava its own way. What is left of the box stays standing.
         if (++this.offPlatformTicks >= OFF_PLATFORM_TICKS) {
            this.warning("Off the platform while breaking the box, at " + this.mc.player.getBlockPos().toShortString() + " with the platform at "
               + this.platformCenter.toShortString() + ": leaving the rest of it standing and going on to the takeoff", new Object[0]);
            this.offPlatformTicks = 0;
            this.cleanupBlocks.clear();
            this.cleanupBlockAttempts = 0;
            this.cleanupBlockIndex = 0;
            this.cleanupInitialized = false;
            this.beginTakeoff();
         }

         return;
      }

      this.offPlatformTicks = 0;
      if (!this.cleanupInitialized) {
         this.cleanupInitialized = true;
         if ((Boolean)this.debugMessages.get()) {
            this.info("Starting cleanup of all blocks above floor for flight clearance", new Object[0]);
         }

         this.cleanupBlocks.clear();
         this.cleanupBlockAttempts = 0;
         if (this.platformCenter != null) {
            // The ender chest goes down first, on purpose: broken with a pickaxe it drops its 8
            // obsidian, and doing it before the walls means those 8 are picked up while there is
            // still room, before the rest of the box fills the last free slots. Left standing (the
            // old behaviour — nothing ever set BREAKING_ECHEST) both the chest and its obsidian
            // were simply abandoned in the box.
            if (this.echestPos != null && this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
               this.cleanupBlocks.add(this.echestPos.toImmutable());
            }

            Set<BlockPos> floorBlocks = new HashSet<>();
            floorBlocks.add(this.platformCenter.toImmutable());
            floorBlocks.add(this.platformCenter.add(1, 0, 0).toImmutable());
            floorBlocks.add(this.platformCenter.add(0, 0, 1).toImmutable());
            floorBlocks.add(this.platformCenter.add(1, 0, 1).toImmutable());

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

            BlockPos[] roofPositions = new BlockPos[]{
               this.platformCenter.add(0, 3, 0),
               this.platformCenter.add(1, 3, 0),
               this.platformCenter.add(0, 3, 1),
               this.platformCenter.add(1, 3, 1)
            };

            // Top down, after the ender chest: roof, then the upper ring, then the lower ring.
            // Breaking a block drops its item where the block was, and a drop from above falls
            // onto the platform we are standing on and is absorbed on the way past; break the
            // bottom ring first instead and its drops scatter off the platform edge into the void
            // or lava below before we ever reach them. The placed-block sweep runs last, only to
            // catch anything the fixed lists missed, and its contains-check keeps it from
            // reordering what the top-down lists already placed.
            for (BlockPos pos : roofPositions) addCleanupBlock(pos);
            for (BlockPos basePos : wallPositionsY1) addCleanupBlock(basePos.up());
            for (BlockPos pos : wallPositionsY1) addCleanupBlock(pos);

            for (BlockPos pos : this.placedBlocks) {
               if (!floorBlocks.contains(pos.toImmutable())) addCleanupBlock(pos);
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

         // Hand the teardown to MlepMine: it was disabled for the shulker ops, turn it back on now so
         // it packet-mines the box for us. It holds the pickaxe itself for each block, so the regear no
         // longer grabs the selected hotbar slot every tick - which is what used to keep AutoEat from
         // ever holding a gap and eating.
         if (this.mlepMineWasActive && this.mlepMine != null && !this.mlepMine.isActive()) {
            this.mlepMine.toggle();
            if ((Boolean)this.debugMessages.get()) {
               this.info("Re-enabled MlepMine for cleanup teardown", new Object[0]);
            }
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
               // The watchdog's thirty seconds count from the last block the cleanup got through, not from
               // its start, so the limit grows with the box. Twenty blocks mined by MlepMine at about 2.7 s
               // each ran 54 s and were cut at 30 in the log that changed this. A block that stalls is skipped
               // by its own 150 attempts, well inside those thirty seconds; one that never lines up for the hand
               // break never counts an attempt, and these thirty seconds, still running from the block before,
               // are what end it.
               this.stateTickCounter = 0;
               this.timer = 1;
               return;
            }

            if (this.cleanupBlockAttempts > 150) {
               if ((Boolean)this.debugMessages.get()) {
                  this.warning("Cleanup timeout on block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size() + ", skipping", new Object[0]);
               }

               this.cleanupBlockIndex++;
               this.cleanupBlockAttempts = 0;
               // skipping a block is getting through it too, for the watchdog
               this.stateTickCounter = 0;
               this.timer = 2;
               return;
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

            if (this.mlepMine != null && this.mlepMine.isActive()) {
               // Click once and let the mine module do the rest. queueMiningData holds the pickaxe
               // itself for the packet mine, so we never touch the selected hotbar slot here - that
               // is what lets AutoEat keep a gap in hand and actually eat when a ghast hits us. The
               // queue dedups by position, so re-issuing it while it is still working is a no-op.
               if (this.cleanupBlockAttempts == 0) {
                  ((MlepMine)this.mlepMine).queueMiningData(((MlepMine)this.mlepMine).new MiningData(pos, breakDirection));
                  if ((Boolean)this.debugMessages.get()) {
                     this.info("Mining block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size() + " via mine module", new Object[0]);
                  }
               }

               this.cleanupBlockAttempts++;
               // Backstop only: if the module has not taken the block down after a while, nudge it
               // back into the queue. Still no hotbar grab, still no per-tick spam.
               if (this.cleanupBlockAttempts % 40 == 0) {
                  ((MlepMine)this.mlepMine).queueMiningData(((MlepMine)this.mlepMine).new MiningData(pos, breakDirection));
               }

               this.timer = 2;
            } else {
               // No mine module available: break it ourselves, selecting a pickaxe once (not every tick).
               if (this.cleanupBlockAttempts == 0) {
                  FindItemResult pickaxe = InvUtils.find(itemStack -> itemStack.isIn(ItemTags.PICKAXES));
                  if (pickaxe.found() && pickaxe.isHotbar() && ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != pickaxe.slot()) {
                     ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(pickaxe.slot());
                  }
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
            }
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
         if (this.baritoneTakesOff()) {
            this.handTakeoffToBaritone();
            return;
         }

         this.startTakeoffSequence();
      }
   }

   /**
    * Whether the takeoff is Baritone's to do: Nether, the setting says so, the flight module
    * that was flying uses Baritone there (TrailFollower always does, WaypointFollower in its
    * Baritone mode), and Baritone is allowed to leave the ground on its own.
    */
   private boolean baritoneTakesOff() {
      if (this.netherTakeoff.get() != AutoFlyingRegear.NetherTakeoff.BARITONE) {
         return false;
      }

      if (this.mc.world == null || !this.mc.world.getRegistryKey().equals(World.NETHER)) {
         return false;
      }

      boolean baritoneFlies = this.disabledModules.contains("TrailFollower");
      if (!baritoneFlies && this.disabledModules.contains("WaypointFollower")) {
         Module follower = this.flightModules.get("WaypointFollower");
         baritoneFlies = follower instanceof WaypointFollower wf && wf.fliesWithBaritone();
      }

      if (!baritoneFlies) {
         return false;
      }

      if (!BaritoneAPI.getSettings().elytraAutoJump.value) {
         if ((Boolean)this.debugMessages.get()) {
            this.warning("Baritone's elytraAutoJump is off, so it cannot take off by itself - taking off the old way", new Object[0]);
         }

         return false;
      }

      return true;
   }

   /**
    * Ends the regear on the platform and lets Baritone take off. The old way jumped and boosted
    * on the heading from before the regear, then handed over to a follower that had nothing to
    * steer with for several seconds: from a box built wherever solid ground was, often a
    * gallery, that was a straight run into the nearest wall at thirty blocks a second, and a
    * totem. Baritone's standing takeoff jumps, opens, fires one rocket and follows a path it
    * computed from right here.
    */
   private void handTakeoffToBaritone() {
      this.releaseMovementKeys();
      this.event("takeoff left to baritone");
      if ((Boolean)this.debugMessages.get()) {
         this.info("Nether flight is Baritone's: staying on the platform and letting it take off along its own path", new Object[0]);
      }

      this.enableFlightModulesForTakeoff();
      this.pendingOutcome = "complete";
      this.takeoff.cancel();
      this.takeoffInitialized = false;
      this.state = AutoFlyingRegear.FlyingRegearState.COMPLETE;
      this.timer = 5;
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
      this.pendingOutcome = flightConfirmed ? "complete" : "takeoff failed" + (reason == null ? "" : ": " + reason);

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
      if (!this.completeHandled) {
         this.completeHandled = true;
         this.endRun(this.pendingOutcome != null ? this.pendingOutcome : "complete");
         if ((Boolean)this.debugMessages.get()) {
            this.info(
               (this.platformBuildAborted ? "AutoFlyingRegear aborted (platform could not be built) - Rockets: " : "AutoFlyingRegear complete! Rockets: ")
                  + this.countRockets() + " Elytras: " + this.countValidElytras(),
               new Object[0]
            );
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
         this.extra = AutoFlyingRegear.Extra.NONE;
         this.timer = 0;
         this.shulkerEnderSlot = -1;
         this.transferStep = 0;
         this.transferSlotIndex = 0;
         this.placementAttempts = 0;
         this.platformPlacementAttempts.clear();
         this.stateTickCounter = 0;
         this.hadBaritoneGoal = false;
         this.lavaPostponeTicks = 0;
         this.savedBaritoneCommand = null;
         this.reEnableStage = 0;
         this.processedShulkers.clear();
         this.mlepMineWasActive = false;
         this.cleanupBlockIndex = 0;
         this.cleanupBlockAttempts = 0;
      }
   }

   private String lowSupplyReason() {
      if (this.mc.player == null || this.mc.interactionManager == null) {
         return "none";
      }

      List<String> reasons = new ArrayList<>();
      int rockets = this.countRockets();
      int validElytras = this.countValidElytras();
      if (rockets < (Integer)this.minRockets.get()) {
         reasons.add("rockets " + rockets + "/" + this.minRockets.get());
      }

      if (validElytras < (Integer)this.minElytras.get()) {
         reasons.add("elytras " + validElytras + "/" + this.minElytras.get());
      }

      int gaps = this.countItem(Items.ENCHANTED_GOLDEN_APPLE);
      if ((Integer)this.minGaps.get() > 0 && gaps < (Integer)this.minGaps.get()) {
         reasons.add("gaps " + gaps + "/" + this.minGaps.get());
      }

      int totems = this.countItem(Items.TOTEM_OF_UNDYING);
      if ((Integer)this.minTotems.get() > 0 && totems < (Integer)this.minTotems.get()) {
         reasons.add("totems " + totems + "/" + this.minTotems.get());
      }

      int enderChests = this.countItem(Items.ENDER_CHEST);
      if ((Integer)this.minEnderChests.get() > 0 && enderChests < (Integer)this.minEnderChests.get() && enderChests > 0) {
         reasons.add("ender chests " + enderChests + "/" + this.minEnderChests.get());
      }

      return reasons.isEmpty() ? "none" : String.join(", ", reasons);
   }

   private boolean shouldTriggerRegear() {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         GameMode gameMode = this.mc.interactionManager.getCurrentGameMode();
         if (gameMode != GameMode.SURVIVAL) {
            return false;
         }

         int rockets = this.countRockets();
         int validElytras = this.countValidElytras();
         if (rockets < (Integer)this.minRockets.get() || validElytras < (Integer)this.minElytras.get()) {
            return true;
         }

         boolean shortOfGaps = (Integer)this.minGaps.get() > 0 && this.countItem(Items.ENCHANTED_GOLDEN_APPLE) < (Integer)this.minGaps.get();
         boolean shortOfTotems = (Integer)this.minTotems.get() > 0 && this.countItem(Items.TOTEM_OF_UNDYING) < (Integer)this.minTotems.get();
         // with none left at all, a regear could not even place the chest it restocks from, and would start over every tick
         int enderChests = this.countItem(Items.ENDER_CHEST);
         boolean shortOfEnderChests = (Integer)this.minEnderChests.get() > 0 && enderChests < (Integer)this.minEnderChests.get() && enderChests > 0;
         return shortOfGaps || shortOfTotems || shortOfEnderChests;
      } else {
         return false;
      }
   }

   /** The item the current restock pass is after. */
   private Item restockItem() {
      if (this.processingElytras) {
         return Items.ELYTRA;
      }

      return switch (this.extra) {
         case GAP -> Items.ENCHANTED_GOLDEN_APPLE;
         case TOTEM -> Items.TOTEM_OF_UNDYING;
         case ECHEST -> Items.ENDER_CHEST;
         default -> Items.FIREWORK_ROCKET;
      };
   }

   /** Its name in messages and in the processed-shulker ids. */
   private String restockLabel() {
      if (this.processingElytras) {
         return "elytra";
      }

      return switch (this.extra) {
         case GAP -> "golden apple";
         case TOTEM -> "totem";
         case ECHEST -> "ender chest";
         default -> "rocket";
      };
   }

   private int extraGoal() {
      return switch (this.extra) {
         case GAP -> (Integer)this.goalGaps.get();
         case TOTEM -> (Integer)this.goalTotems.get();
         case ECHEST -> (Integer)this.goalEnderChests.get();
         default -> 0;
      };
   }

   private boolean extraWanted(Extra kind) {
      int goal = switch (kind) {
         case GAP -> (Integer)this.goalGaps.get();
         case TOTEM -> (Integer)this.goalTotems.get();
         case ECHEST -> (Integer)this.goalEnderChests.get();
         default -> 0;
      };
      Item item = switch (kind) {
         case GAP -> Items.ENCHANTED_GOLDEN_APPLE;
         case TOTEM -> Items.TOTEM_OF_UNDYING;
         case ECHEST -> Items.ENDER_CHEST;
         default -> Items.AIR;
      };
      return goal > 0 && this.countItem(item) < goal;
   }

   /** The elytra pass is over: on to the extras that are wanted, then the rockets. */
   private void afterElytras() {
      this.processingElytras = false;
      this.extra = Extra.NONE;
      this.nextExtra();
   }

   /** Moves on to the next extra kind still short of its goal, or to the rockets, and reopens the ender chest. */
   private void nextExtra() {
      Extra next = nextExtraAfter(this.extra);
      while (next != null && !this.extraWanted(next)) {
         next = nextExtraAfter(next);
      }

      this.extra = next == null ? Extra.NONE : next;
      this.event("now getting " + this.restockLabel() + "s");
      if ((Boolean)this.debugMessages.get()) {
         this.info("Now getting " + this.restockLabel() + "s", new Object[0]);
      }

      this.state = AutoFlyingRegear.FlyingRegearState.OPENING_ECHEST;
      this.timer = (Integer)this.containerOpenDelay.get();
   }

   /** The extras in the order they are restocked, gaps, totems, ender chests; null after the last. */
   private static Extra nextExtraAfter(Extra kind) {
      return switch (kind) {
         case NONE -> Extra.GAP;
         case GAP -> Extra.TOTEM;
         case TOTEM -> Extra.ECHEST;
         case ECHEST -> null;
      };
   }

   /** How many of an item the inventory holds, offhand and armour included. */
   private int countItem(Item item) {
      if (this.mc.player == null) {
         return 0;
      }

      int count = 0;

      for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == item) {
            count += stack.getCount();
         }
      }

      return count;
   }

   /** One inventory move, with a debug line naming what goes where and what it displaces. */
   private void moveStack(int from, int to) {
      if ((Boolean)this.debugMessages.get()) {
         ItemStack moving = this.mc.player.getInventory().getStack(from);
         ItemStack displaced = this.mc.player.getInventory().getStack(to);
         this.info(
            "Moving " + moving.getName().getString() + " x" + moving.getCount() + " from slot " + from + " to slot " + to
               + (displaced.isEmpty() ? "" : " (" + displaced.getName().getString() + " goes to slot " + from + ")"),
            new Object[0]
         );
      }

      InvUtils.move().from(from).to(to);
   }

   /** Puts a totem in the offhand when totems were part of the restock and the offhand is empty. */
   private void totemToOffhand() {
      if ((Integer)this.goalTotems.get() <= 0 || !this.mc.player.getOffHandStack().isEmpty()) {
         return;
      }

      for (int i = 0; i < 36; i++) {
         if (this.mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
            if ((Boolean)this.debugMessages.get()) {
               this.info("Totem from slot " + i + " to the offhand", new Object[0]);
            }

            this.event("totem to the offhand");
            InvUtils.move().from(i).toOffhand();
            return;
         }
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
               this.moveStack(bestRocketSlot, targetSlot);
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

   /**
    * Puts an elytra back on in place of the chestplate: the one with the most durability left,
    * not the first one the inventory scan happens upon, which after a restock is as likely as
    * not one of the worn ones waiting to go back into the shulker.
    */
   private void restoreChestplate() {
      if (this.savedChestplate.isEmpty()) {
         return;
      }

      int best = -1;
      int bestDurability = -1;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() != Items.ELYTRA) {
            continue;
         }

         int durability = stack.getMaxDamage() - stack.getDamage();
         if (durability > bestDurability) {
            bestDurability = durability;
            best = i;
         }
      }

      if (best == -1) {
         return;
      }

      if ((Boolean)this.debugMessages.get()) {
         ItemStack chosen = this.mc.player.getInventory().getStack(best);
         this.info(
            "Elytra from slot " + best + " (" + bestDurability * 100 / Math.max(1, chosen.getMaxDamage()) + "%) to the chest, the chestplate goes to slot " + best,
            new Object[0]
         );
      }

      InvUtils.move().from(best).toArmor(2);
      this.savedChestplate = ItemStack.EMPTY;
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
         this.moveStack(blocks.slot(), targetHotbarSlot);
         this.timer = (Integer)this.clickDelay.get();
         return false;
      }

      return true;
   }

   private boolean playerOccupies(BlockPos pos) {
      return this.mc.player != null && this.mc.player.getBoundingBox().intersects(new Box(pos));
   }

   private void stepAsideFrom(BlockPos blocked) {
      if (this.mc.player == null || this.platformCenter == null) {
         return;
      }

      BlockPos[] floorCells = new BlockPos[]{
         this.platformCenter.add(0, 1, 0),
         this.platformCenter.add(1, 1, 0),
         this.platformCenter.add(0, 1, 1),
         this.platformCenter.add(1, 1, 1)
      };
      BlockPos target = null;
      double bestDistance = -1.0;

      for (BlockPos cell : floorCells) {
         double cellDx = cell.getX() - blocked.getX();
         double cellDz = cell.getZ() - blocked.getZ();
         double cellDistance = cellDx * cellDx + cellDz * cellDz;
         if (cellDistance > bestDistance) {
            bestDistance = cellDistance;
            target = cell;
         }
      }

      if (target == null) {
         return;
      }

      Vec3d center = Vec3d.ofCenter(target);
      double deltaX = center.x - this.mc.player.getX();
      double deltaZ = center.z - this.mc.player.getZ();
      double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
      float playerYaw = this.mc.player.getYaw();
      float yawDiff = MathHelper.wrapDegrees(targetYaw - playerYaw);
      if (Math.abs(yawDiff) > 5.0F) {
         this.mc.player.setYaw(playerYaw + Math.signum(yawDiff) * 5.0F);
      }

      if (Math.abs(yawDiff) < 30.0F) {
         if (distance > 0.5) {
            Utils.setPressed(this.mc.options.forwardKey, true);
         } else if (distance > 0.2) {
            Utils.setPressed(this.mc.options.forwardKey, this.stepAsideTicks % 3 == 0);
         } else {
            Utils.setPressed(this.mc.options.forwardKey, false);
         }

         Utils.setPressed(this.mc.options.sneakKey, distance < 1.0);
      }
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
      return PlacementUtils.getGrimDirectionalHit(pos, this.isRoofPosition(pos) ? Direction.UP : Direction.DOWN);
   }

   private BlockHitResult resolveStructurePlaceHit(BlockPos pos) {
      Vec3d eye = this.mc.player.getEyePos();
      BlockHitResult best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction d : Direction.values()) {
         BlockPos against = pos.offset(d);
         BlockState state = this.mc.world.getBlockState(against);
         if (!state.isReplaceable() && !state.getCollisionShape(this.mc.world, against).isEmpty()) {
            Direction side = d.getOpposite();
            Vec3d normal = Vec3d.of(side.getVector());
            Vec3d hitVec = Vec3d.ofCenter(against).add(normal.multiply(0.5));
            if (eye.subtract(hitVec).dotProduct(normal) <= 0.0) {
               continue;
            }

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
      return this.placeStructureBlock(pos, false);
   }

   private boolean placeStructureBlock(BlockPos pos, boolean forceAirPlace) {
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

      BlockHitResult hit = forceAirPlace ? PlacementUtils.getGrimDirectionalHit(pos, Direction.DOWN) : this.resolveStructurePlaceHit(pos);
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

   /**
    * Outlines the blocks of the box that are still standing.
    *
    * <p>{@link #placedBlocks} is the list of everything we placed, and it is only emptied when the whole
    * regear ends — nothing takes an entry out of it as the mine module tears the box back down. Drawing it
    * as-is therefore left a box hanging in the air over every block already broken, for the length of the
    * teardown. Reading the world instead of trying to keep a second list in step also covers the blocks we
    * never lost track of but never placed either: one that failed silently, one a neighbour mined, one that
    * fell in lava.
    */
   @EventHandler
   private void onRender(Render3DEvent event) {
      if (!(Boolean)this.render.get() || this.placedBlocks.isEmpty() || this.mc.world == null) {
         return;
      }

      for (BlockPos pos : this.placedBlocks) {
         if (this.mc.world.getBlockState(pos).isAir()) {
            continue;
         }

         event.renderer.box(pos, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
      }
   }

   /**
    * Meteor runs every chat message through {@code String.format}, arguments or not, so a
    * literal percent sign in a message built from an item name or a durability ("(91%)") throws
    * {@code UnknownFormatConversionException} and takes the game down — which is exactly what
    * the restore message did on 2026-09-07. Messages given without arguments are literal here.
    */
   @Override
   public void info(String message, Object... args) {
      super.info(args.length == 0 ? message.replace("%", "%%") : message, args);
   }

   @Override
   public void warning(String message, Object... args) {
      super.warning(args.length == 0 ? message.replace("%", "%%") : message, args);
   }

   @Override
   public void error(String message, Object... args) {
      super.error(args.length == 0 ? message.replace("%", "%%") : message, args);
   }

   private void beginRun() {
      this.completeHandled = false;
      this.platformBuildAborted = false;
      this.obsidianTopUpDecided = false;
      this.topUpChestsBroken = 0;
      this.wallsStarted = false;
      this.releaseBaritonePlacement();
      this.runAborted = false;
      this.wallMissingLast = -1;
      this.wallNoProgressRounds = 0;
      this.wallMineQueued.clear();
      this.lowSupplyTicks = 0;
      AutoFlyingRegear.Run run = new AutoFlyingRegear.Run();
      run.mode = this.elytraMode.get();
      run.lavaWaitSeconds = this.lavaPostponeTicks / 20;
      run.rocketsBefore = this.countRockets();
      run.elytrasBefore = this.countValidElytras();
      run.gapsBefore = this.countItem(Items.ENCHANTED_GOLDEN_APPLE);
      run.totemsBefore = this.countItem(Items.TOTEM_OF_UNDYING);
      run.bottlesBefore = this.countExperienceBottles();
      run.enderChestsBefore = this.countItem(Items.ENDER_CHEST);
      run.obsidianBefore = this.countItem(Items.OBSIDIAN);
      this.currentRun = run;
      this.pendingOutcome = null;
      this.event(run.lavaWaitSeconds > 0 ? "landing after " + run.lavaWaitSeconds + "s over lava" : "landing to regear");
   }

   /** Closes the running regear, if there is one, under the given outcome; counts it for the session and for good. */
   private void endRun(String outcome) {
      AutoFlyingRegear.Run run = this.currentRun;
      if (run == null) {
         return;
      }

      run.endedMs = System.currentTimeMillis();
      run.outcome = outcome;
      if (this.mc.player != null) {
         run.rocketsAfter = this.countRockets();
         run.elytrasAfter = this.countValidElytras();
         run.gapsAfter = this.countItem(Items.ENCHANTED_GOLDEN_APPLE);
         run.totemsAfter = this.countItem(Items.TOTEM_OF_UNDYING);
         run.bottlesAfter = this.countExperienceBottles();
         run.enderChestsAfter = this.countItem(Items.ENDER_CHEST);
         run.obsidianAfter = this.countItem(Items.OBSIDIAN);
      }

      this.currentRun = null;
      this.lastRun = run;
      this.pendingOutcome = null;
      this.sessionRegears++;
      this.sessionRegearMs += run.durationMs();
      LifetimeStats.get().addRegear(run.durationMs() / 1000L);
      this.rocketRateBaseUsed = SessionStats.get().rocketsUsed();
      this.rocketRateBaseMs = run.endedMs;
      this.event("regear " + outcome + " in " + run.durationMs() / 1000L + "s");
   }

   /** The last thing worth a glance, for the HUD's event line. */
   private void event(String text) {
      this.lastEvent = text;
      this.lastEventMs = System.currentTimeMillis();
   }

   public AutoFlyingRegear.Run hudCurrentRun() {
      return this.currentRun;
   }

   public AutoFlyingRegear.Run hudLastRun() {
      return this.lastRun;
   }

   public int hudSessionRegears() {
      return this.sessionRegears;
   }

   public long hudAverageRunMs() {
      return this.sessionRegears == 0 ? 0L : this.sessionRegearMs / this.sessionRegears;
   }

   public String hudLastEvent() {
      return this.lastEvent;
   }

   public long hudLastEventMs() {
      return this.lastEventMs;
   }

   /** True while supplies are low but the flight goes on because there is lava or nothing known below. */
   public boolean hudWaitingForGround() {
      return this.state == AutoFlyingRegear.FlyingRegearState.IDLE && this.lavaPostponeTicks > 0;
   }

   public int hudLavaWaitSeconds() {
      return this.lavaPostponeTicks / 20;
   }

   public int hudGapCount() {
      return this.mc.player == null ? 0 : this.countItem(Items.ENCHANTED_GOLDEN_APPLE);
   }

   public int hudTotemCount() {
      return this.mc.player == null ? 0 : this.countItem(Items.TOTEM_OF_UNDYING);
   }

   public int hudMinRockets() {
      return (Integer)this.minRockets.get();
   }

   public int hudGoalElytras() {
      return (Integer)this.goalElytras.get();
   }

   public int hudGoalGaps() {
      return (Integer)this.goalGaps.get();
   }

   public int hudGoalTotems() {
      return (Integer)this.goalTotems.get();
   }

   public int hudEnderChestCount() {
      return this.mc.player == null ? 0 : this.countItem(Items.ENDER_CHEST);
   }

   public int hudObsidianCount() {
      return this.mc.player == null ? 0 : this.countItem(Items.OBSIDIAN);
   }

   /** How many ender chests the obsidian refill never breaks. */
   public int hudKeepEnderChests() {
      return (Integer)this.keepEnderChests.get();
   }

   /** The obsidian count at or under which the next regear refills it; {@code -1} when the refill is off. */
   public int hudObsidianRefillAt() {
      return (Integer)this.obsidianRefillTo.get() > 0 ? (Integer)this.obsidianRefillAt.get() : -1;
   }

   /** What will force the next regear first. */
   public enum RegearBound {
      ROCKETS,
      GAPS,
      TOTEMS,
      ELYTRA,
      BOTTLES,
      UNKNOWN
   }

   /**
    * What will trigger the next regear first. A consumable already under its own trigger minimum
    * wins outright (the regear is effectively due now); otherwise it is the rockets, on the rate
    * they are being spent. There is no spend rate for gaps or totems — they deplete slowly and
    * erratically (a death, a bite), so no honest time can be put on them; they only ever read as
    * "now" once already short. {@link #hudSecondsToNextRegear} pairs with this.
    */
   public RegearBound hudNextRegearBound() {
      if (!this.isActive() || this.mc.player == null) {
         return RegearBound.UNKNOWN;
      }

      if (this.getValidElytraCount() < (Integer)this.minElytras.get()) return RegearBound.ELYTRA;
      if ((Integer)this.minGaps.get() > 0 && this.countItem(Items.ENCHANTED_GOLDEN_APPLE) < (Integer)this.minGaps.get()) return RegearBound.GAPS;
      if ((Integer)this.minTotems.get() > 0 && this.countItem(Items.TOTEM_OF_UNDYING) < (Integer)this.minTotems.get()) return RegearBound.TOTEMS;
      if (this.getRocketCount() < (Integer)this.minRockets.get()) return RegearBound.ROCKETS;

      return this.hudRocketsPerMinute() > 0.0 ? RegearBound.ROCKETS : RegearBound.UNKNOWN;
   }

   /**
    * Seconds until the next forced regear: 0 when a consumable is already under its minimum, the
    * rocket-rate estimate when rockets are the constraint, -1 when it cannot be said (no rate yet).
    */
   public int hudSecondsToNextRegear() {
      RegearBound bound = this.hudNextRegearBound();
      if (bound == RegearBound.UNKNOWN) return -1;
      if (bound != RegearBound.ROCKETS) return 0;

      int left = this.getRocketCount() - (Integer)this.minRockets.get();
      if (left <= 0) return 0;

      double perMinute = this.hudRocketsPerMinute();
      if (perMinute <= 0.0) return -1;
      return (int) Math.round(left / perMinute * 60.0);
   }

   /**
    * Rockets spent per minute since the last regear (or since activation), or -1 while there is
    * too little to go on: under two minutes, or under three rockets.
    */
   public double hudRocketsPerMinute() {
      long elapsed = System.currentTimeMillis() - this.rocketRateBaseMs;
      int used = SessionStats.get().rocketsUsed() - this.rocketRateBaseUsed;
      if (this.rocketRateBaseMs == 0L || elapsed < 120_000L || used < 3) {
         return -1.0;
      }

      return used / (elapsed / 60000.0);
   }

   /** Whether the settings give this station a place on the line at all. */
   public boolean hudStepEnabled(AutoFlyingRegear.Step step) {
      return switch (step) {
         case GAPS -> (Integer)this.goalGaps.get() > 0;
         case TOTEMS -> (Integer)this.goalTotems.get() > 0;
         case MEND -> this.elytraMode.get() == AutoFlyingRegear.ElytraMode.REPAIR;
         default -> true;
      };
   }

   /** The station the machine is at, null when idle. */
   public AutoFlyingRegear.Step hudStep() {
      if (this.state == null) {
         return null;
      }

      return switch (this.state) {
         case IDLE, COMPLETE -> null;
         case SWAP_TO_CHESTPLATE, DISABLING_MODULES, DROPPING, CENTERING_ON_PLATFORM -> AutoFlyingRegear.Step.LAND;
         case CREATING_INITIAL_PLATFORM, CREATING_WALLS, CLEARING_ECHEST_AREA -> AutoFlyingRegear.Step.BOX;
         case ROTATING_FOR_ECHEST, PLACING_ECHEST, WAIT_ECHEST_PLACE -> AutoFlyingRegear.Step.CHEST;
         case OPENING_ECHEST, OPENING_ECHEST_RETURN, TAKING_SHULKER, WAIT_SHULKER_TAKEN, POSITIONING_FOR_SHULKER,
              ROTATING_FOR_SHULKER, PLACING_SHULKER, WAIT_SHULKER_PLACE, OPENING_SHULKER, TRANSFERRING_ITEMS,
              BREAKING_SHULKER, WAIT_SHULKER_BREAK, WAIT_SHULKER_PICKUP, RETURNING_SHULKER, CHECK_NEXT_SHULKER -> this.restockStep();
         case REPAIRING_ELYTRA -> AutoFlyingRegear.Step.MEND;
         case BREAKING_ECHEST, WAIT_ECHEST_BREAK, RESTORING_ELYTRA, TOPPING_UP_OBSIDIAN, CLEANUP -> AutoFlyingRegear.Step.CLEAR;
         case TAKING_OFF -> AutoFlyingRegear.Step.FLY;
      };
   }

   private AutoFlyingRegear.Step restockStep() {
      if (this.processingElytras) {
         return AutoFlyingRegear.Step.ELYTRA;
      }

      return switch (this.extra) {
         case GAP -> AutoFlyingRegear.Step.GAPS;
         case TOTEM -> AutoFlyingRegear.Step.TOTEMS;
         case ECHEST -> AutoFlyingRegear.Step.CHEST;
         default -> AutoFlyingRegear.Step.ROCKETS;
      };
   }

   /** The station and its progress in a few capitals, for the HUD's big line. */
   public String hudPhaseDetail() {
      AutoFlyingRegear.Step step = this.hudStep();
      if (step == null) {
         return "READY";
      }

      return switch (step) {
         case LAND -> this.state == AutoFlyingRegear.FlyingRegearState.DROPPING ? "DROPPING" : "LANDING";
         case BOX -> "BUILDING BOX";
         case CHEST -> "ENDER CHEST";
         case ELYTRA -> this.elytraMode.get() == AutoFlyingRegear.ElytraMode.REPAIR
            ? "XP BOTTLES " + this.countExperienceBottles() + "/" + this.xpBottlesToTake.get()
            : "ELYTRAS " + this.countValidElytras() + "/" + this.goalElytras.get();
         case GAPS -> "GAPS " + this.countItem(Items.ENCHANTED_GOLDEN_APPLE) + "/" + this.goalGaps.get();
         case TOTEMS -> "TOTEMS " + this.countItem(Items.TOTEM_OF_UNDYING) + "/" + this.goalTotems.get();
         case ROCKETS -> "ROCKETS " + this.countRockets();
         case MEND -> {
            int pct = this.getRepairPercent();
            yield pct < 0 ? "MENDING" : "MENDING " + pct + "%";
         }
         case CLEAR -> this.state == AutoFlyingRegear.FlyingRegearState.CLEANUP && !this.cleanupBlocks.isEmpty()
            ? "CLEANUP " + Math.min(this.cleanupBlockIndex + 1, this.cleanupBlocks.size()) + "/" + this.cleanupBlocks.size()
            : "CLEARING";
         case FLY -> "TAKEOFF";
      };
   }

   public String getInfoString() {
      int rockets = this.countRockets();
      int elytras = this.countValidElytras();
      return rockets + "R/" + elytras + "E - " + this.getPhaseLabel();
   }

   /**
    * The current state, named for a human.
    *
    * <p>Thirty-three states is the right granularity for the machine and the wrong one for
    * anybody watching: {@code WAIT_SHULKER_PICKUP} says nothing that {@code Shulker} does not,
    * and the raw enum name was what the module reported in the Meteor list.
    */
   public String getPhaseLabel() {
      if (this.state == null) return "Idle";

      return switch (this.state) {
         case IDLE, COMPLETE -> "Idle";
         case TAKING_OFF -> "Takeoff";
         case SWAP_TO_CHESTPLATE, DISABLING_MODULES -> "Preparing";
         case DROPPING, CENTERING_ON_PLATFORM, CREATING_INITIAL_PLATFORM, CREATING_WALLS,
              CLEARING_ECHEST_AREA -> "Platform";
         case ROTATING_FOR_ECHEST, PLACING_ECHEST, WAIT_ECHEST_PLACE, OPENING_ECHEST,
              OPENING_ECHEST_RETURN -> "Ender chest";
         case TAKING_SHULKER, WAIT_SHULKER_TAKEN, POSITIONING_FOR_SHULKER, ROTATING_FOR_SHULKER,
              PLACING_SHULKER, WAIT_SHULKER_PLACE, OPENING_SHULKER, BREAKING_SHULKER,
              WAIT_SHULKER_BREAK, WAIT_SHULKER_PICKUP, RETURNING_SHULKER,
              CHECK_NEXT_SHULKER -> "Shulker";
         case TRANSFERRING_ITEMS -> "Transfer";
         case BREAKING_ECHEST, WAIT_ECHEST_BREAK -> "Recovering chest";
         case REPAIRING_ELYTRA -> "Mending elytra";
         case RESTORING_ELYTRA -> "Restoring elytra";
         case TOPPING_UP_OBSIDIAN -> "Obsidian";
         case CLEANUP -> "Cleanup";
      };
   }

   /** True while the machine is doing anything at all. */
   public boolean isBusy() {
      return this.state != null
         && this.state != AutoFlyingRegear.FlyingRegearState.IDLE
         && this.state != AutoFlyingRegear.FlyingRegearState.COMPLETE;
   }

   public AutoFlyingRegear.ElytraMode getElytraMode() {
      return this.elytraMode.get();
   }

   public int getBottleCount() {
      return this.mc.player == null ? 0 : this.countExperienceBottles();
   }

   public int getRocketCount() {
      return this.mc.player == null ? 0 : this.countRockets();
   }

   public int getValidElytraCount() {
      return this.mc.player == null ? 0 : this.countValidElytras();
   }

   /**
    * Durability percentage of the elytra being mended, or -1 when none is.
    *
    * <p>Deliberately the progress of the operation and not the full durability picture — that
    * belongs to the elytra status line, and repeating it here would be two places to keep in
    * agreement for no gain.
    */
   public int getRepairPercent() {
      if (this.state != AutoFlyingRegear.FlyingRegearState.REPAIRING_ELYTRA) return -1;
      if (this.mc.player == null || this.repairHotbarSlot == -1) return -1;

      ItemStack held = this.mc.player.getInventory().getStack(this.repairHotbarSlot);
      if (held.getItem() != Items.ELYTRA || held.getMaxDamage() <= 0) return -1;

      return (held.getMaxDamage() - held.getDamage()) * 100 / held.getMaxDamage();
   }

   /** What the straight-down scan found under the player's feet. */
   private enum GroundKind {
      SOLID,
      LAVA,
      WATER,
      UNKNOWN;
   }

   /** Result of scanGroundBelow(): the kind of ground and how many blocks below the feet it starts. */
   private record GroundScan(AutoFlyingRegear.GroundKind kind, int distance) {
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
      REPAIRING_ELYTRA,
      RESTORING_ELYTRA,
      TOPPING_UP_OBSIDIAN,
      CLEANUP,
      TAKING_OFF,
      COMPLETE;
   }

   public enum ElytraMode {
      REPLACE,
      REPAIR;
   }

   public enum OutOfSuppliesAction {
      OFF_MODULES,
      DISCONNECT;
   }
}
