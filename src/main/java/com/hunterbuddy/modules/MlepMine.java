package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.modules.regear.util.InventoryManager;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.util.GrimBreakBalance;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class MlepMine extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAutoMine = this.settings.createGroup("Auto Mine");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<MlepMine.SpeedmineMode> modeConfig = this.sgGeneral
      .add(
         new Builder<MlepMine.SpeedmineMode>().name("mode").description("The mining mode for speedmine").defaultValue(MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<Boolean> multitaskConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("multitask")
                     .description("Allows mining while using items")
                  .defaultValue(false)
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   public final Setting<Boolean> doubleBreakConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("double-break")
                     .description("Allows you to mine two blocks at once")
                  .defaultValue(true)
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<Double> rangeConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("range")
                  .description("The range to mine blocks")
               .defaultValue(6.0)
               .min(0.1)
               .sliderRange(0.1, 6.0)
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<Double> speedConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("speed")
               .description("The speed to mine blocks")
            .defaultValue(1.05)
            .min(0.1)
            .sliderRange(0.1, 1.1)
            .build()
      );
   private final Setting<Boolean> instantConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("instant")
                  .description("Keeps the spot you just cleared hot: a block placed back there breaks again on its own, with no click. Turn it off and a cleared spot is forgotten the moment it breaks.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Integer> instantWindow = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("instant-window")
                     .description("How long a cleared spot stays hot on the client, in milliseconds, so a block placed there long after is left alone instead of breaking on sight. 0 never forgets, which is what the server does anyway. Note that the click needed after a spot goes cold restarts the server timer, so that one block breaks at normal speed.")
                  .defaultValue(0)
                  .min(0)
                  .sliderRange(0, 30000)
               .visible(this.instantConfig::get)
            .build()
      );
   private final Setting<Keybind> instantToggleKey = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                     .name("instant-toggle-key")
                  .description("Key to toggle the instant mining option")
               .defaultValue(Keybind.none()).build()
      );
   public final Setting<Boolean> queueConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("queue")
                     .description("Click several blocks and break them one after another. Nothing is sent for a waiting block, so the server only ever sees one break at a time, exactly as if you clicked them yourself.")
                  .defaultValue(false)
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
               .onChanged(v -> {
                  if (!Boolean.TRUE.equals(v)) this.pendingQueue.clear();
               })
            .build()
      );
   private final Setting<Keybind> queueClearKey = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                     .name("queue-clear-key")
                  .description("Key to drop every block still waiting in the queue.")
               .defaultValue(Keybind.none())
               .visible(() -> this.queueConfig.get() && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<SettingColor> queueColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("queue-color")
                  .description("Color of the blocks waiting in the queue.")
               .defaultValue(new SettingColor(120, 180, 255, 60))
               .visible(this.queueConfig::get)
            .build()
      );
   private final Setting<Boolean> vanillaOneShot = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("vanilla-one-shot")
                     .description("Hand any block your held item takes down in a single hit back to the normal client break. It is faster there, since the client predicts the break instead of waiting for the round trip, and it sends none of the packets an anticheat counts against a break rate. Two side effects: a vanilla break restarts the server clock on a spot instant was keeping hot, and vanilla pauses 250 ms after a one-hit break.")
                  .defaultValue(true)
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<java.util.List<Block>> vanillaBlocks = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BlockListSetting.Builder()
                     .name("leave-to-vanilla")
                  .description("Blocks this module keeps its hands off entirely, whatever they are worth a hit. The normal client break takes them.")
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<Boolean> persistentConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                           .name("persistent")
                        .description("Keeps packet mine exploit active even when module is disabled (prevents exploit from breaking)")
                     .defaultValue(true)
                  .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
               .onChanged(
                  enabled -> {
                     if (enabled && this.mc.player != null) {
                        this.mc
                           .player
                           .sendMessage(
                              Text.literal(
                                 "\u00a77[\u00a7bh-mine\u00a77] \u00a7aPersistent mode enabled! Module cannot be disabled until you turn this off or disconnect."
                              ),
                              false
                           );
                     }
                  }
               ).build()
      );
   private final Setting<MlepMine.Swap> swapConfig = this.sgGeneral
      .add(
         new Builder<MlepMine.Swap>().name("auto-swap").description("Holds the best hotbar tool from the first packet until the block is gone. The tool must be in the hotbar: only a hotbar slot can be selected, so a pickaxe left in the backpack is invisible here.")
                  .defaultValue(MlepMine.Swap.SILENT)
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<Boolean> rotateConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("rotate")
                     .description("Rotates when mining the block")
                  .defaultValue(false)
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<Boolean> switchResetConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("switch-reset")
                     .description("Resets mining after switching items")
                  .defaultValue(false)
               .visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<Boolean> grimConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("grim")
                  .description("Uses grim block breaking speeds")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> grimNewConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("grim-v3")
                     .description("Uses new grim block breaking speeds")
                  .defaultValue(true)
               .visible(this.grimConfig::get)
            .build()
      );
   private final Setting<Boolean> miningFix = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("mining-fix")
                     .description("Mining fix for grim v3")
                  .defaultValue(false)
               .visible(() -> (Boolean)this.grimConfig.get() && (Boolean)this.grimNewConfig.get())
               .build()
      );
   private final Setting<Boolean> grimBalanceConfig = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("grim-balance")
                     .description("Times every start against a copy of the anticheat's own break balances instead of holding all of them for a fixed 280 ms. After a pause the balance is empty and two or three blocks go straight through; over a long run the two come out the same, because the buffer only repays a tenth at a time.")
                  .defaultValue(true)
               .visible(this.grimConfig::get)
               .build()
      );
   private final Setting<Integer> grimBudget = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("grim-budget")
                     .description("How much of the anticheat's thousand-millisecond buffer to spend. What is left is the margin jitter eats: it measures the delays as the packets land, this measures them as they leave, and a lag spike bunches a whole run of them together.")
                  .defaultValue(700)
                  .min(0)
                  .sliderRange(0, 1000)
               .visible(() -> (Boolean)this.grimConfig.get() && (Boolean)this.grimBalanceConfig.get())
               .build()
      );
   private final Setting<Boolean> grimBalanceLog = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("grim-balance-log")
                     .description("Writes a line per block to the log: what the block was predicted to cost, where the two balances stand, and how long the server took to confirm the break after the closing stop went out.")
                  .defaultValue(false)
               .visible(() -> (Boolean)this.grimConfig.get() && (Boolean)this.grimBalanceConfig.get())
               .build()
      );
   private final Setting<Keybind> autoMineKey = this.sgAutoMine
      .add(
         new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
                     .name("auto-mine-key")
                  .description("Key to toggle auto-mining enemies")
               .defaultValue(Keybind.none()).build()
      );
   private final Setting<Boolean> autoMine = this.sgAutoMine
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-mine")
                  .description("Automatically mines blocks around nearby enemies")
               .defaultValue(false)
            .build()
      );
   private final Setting<Double> enemyRange = this.sgAutoMine
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("enemy-range")
                  .description("Range to search for enemy players")
               .defaultValue(5.0)
               .min(1.0)
               .sliderRange(1.0, 10.0)
               .visible(this.autoMine::get)
            .build()
      );
   private final Setting<MlepMine.Swing> swing = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<MlepMine.Swing>()
                     .name("swing")
                  .description("Which arm animation to play while mining. NONE is what the module has always done: the bursts carry their own bare swing packets and the end-of-bar break carries none at all, which leaves a tick that breaks a block without an animation on it -- a shape an anticheat looks for, and one that reads from outside as blocks popping while you stand still. MINER swings every tick a block is being worked, which is exactly vanilla's own rate. HAMMER swings once on each tick that sends a dig: one clean blow per block.")
               .defaultValue(MlepMine.Swing.NONE)
            .build()
      );

   private final Setting<Boolean> strictDirection = this.sgAutoMine
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("strict-direction")
                     .description("Only mines blocks on visible faces")
                  .defaultValue(false)
               .visible(this.autoMine::get)
            .build()
      );
   private final Setting<Boolean> targetHead = this.sgAutoMine
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("target-head")
                     .description("Also targets blocks at head level (Y+1)")
                  .defaultValue(true)
               .visible(this.autoMine::get)
            .build()
      );
   private final Setting<Boolean> autoRotate = this.sgAutoMine
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("auto-rotate")
                     .description("Rotates to enemy blocks (uses silent rotations)")
                  .defaultValue(false)
               .visible(this.autoMine::get)
            .build()
      );
   private final Setting<Boolean> antiCrawl = this.sgAutoMine
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("anti-crawl")
                     .description("Automatically mines block above your head when crawling to stand up")
                  .defaultValue(false)
               .visible(this.autoMine::get)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render")
                  .description("Whether or not to render the block being mined")
               .defaultValue(true)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(new Builder<ShapeMode>().name("shape-mode").description("How the shapes are rendered").defaultValue(ShapeMode.Both).build());
   private final Setting<SettingColor> colorConfig = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("mine-color")
                  .description("The mine render color")
               .defaultValue(new SettingColor(Color.BLUE)).visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<SettingColor> colorDoneConfig = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("done-color")
                  .description("The done render color")
               .defaultValue(new SettingColor(Color.CYAN)).visible(() -> this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET)
            .build()
      );
   private final Setting<Integer> fadeTimeConfig = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("fade-time")
                     .description("Time to fade")
                  .defaultValue(250)
               .min(0)
               .sliderRange(0, 1000)
               .visible(() -> false)
            .build()
      );
   private final Setting<MlepMine.RenderStyle> styleConfig = this.sgRender
      .add(
         new Builder<MlepMine.RenderStyle>().name("style")
                  .description("How the block being mined is drawn. Classic is the old box growing out of the middle. Fill raises a level inside the block instead, the way a bar fills, which is readable from any angle and at any distance. Pulse is Fill with a breath on it.")
               .defaultValue(MlepMine.RenderStyle.FILL)
               .visible(this.render::get)
            .build()
      );
   private final Setting<Double> pulseRate = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("pulse-rate")
                     .description("Breaths per second.")
                  .defaultValue(1.2)
                  .min(0.1)
                  .sliderRange(0.1, 3.0)
               .visible(() -> (Boolean)this.render.get() && this.styleConfig.get() == MlepMine.RenderStyle.PULSE)
            .build()
      );
   private final Setting<Boolean> outlineConfig = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("block-outline")
                     .description("Draws a faint outline on the whole block behind the progress box, so you can still tell which block is being mined while the box is small.")
                  .defaultValue(true)
               .visible(this.render::get)
            .build()
      );
   private final Setting<Boolean> queueChain = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("queue-chain")
                     .description("Links the waiting blocks with a line, in the order they will be mined.")
                  .defaultValue(true)
               .visible(() -> (Boolean)this.render.get() && (Boolean)this.queueConfig.get())
            .build()
      );
   private final Setting<Boolean> queueNumbers = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("queue-numbers")
                     .description("Prints the rank of each waiting block above it. 1 is the next one to go.")
                  .defaultValue(true)
               .visible(() -> (Boolean)this.render.get() && (Boolean)this.queueConfig.get())
            .build()
      );
   private final Map<MlepMine.MiningData, MlepMine.Animation> fadeList = new HashMap<>();
   private MlepMine.FirstOutQueue<MlepMine.MiningData> miningQueue;

   /**
    * Blocks clicked while something was still breaking. Purely client side —
    * nothing is sent for an entry until it is promoted into {@link #miningQueue},
    * so the server never sees more than the one break it already saw before.
    */
   private final List<MlepMine.MiningData> pendingQueue = new ArrayList<>();
   private static final int PENDING_QUEUE_LIMIT = 16;
   private boolean queueClearPressed;

   /**
    * Tick of the last attack event, whatever block it was on.
    *
    * <p>Meteor cancels {@code attackBlock} at its head, so vanilla never records
    * the block as "currently breaking" and calls it again on every tick for as
    * long as the button is held — twice on the tick of the press itself. One
    * press therefore arrives here twenty times a second, and Baritone, which
    * never releases, arrives every tick forever.
    *
    * <p>A press is real when nothing at all came in on the previous tick. Which
    * block it was on is irrelevant: sweeping the crosshair across blocks with the
    * button down is still one press. Only a real press is allowed to take a block
    * back out of the queue — see {@link #clickMine(MiningData, boolean)}.
    */
   private int lastEventTick = -100;

   /** Block the press currently under way took out of the queue, if it took one. */
   private BlockPos droppedByPress;
   private long lastBreak;

   /**
    * The anticheat's own arithmetic, run on the packets as they leave.
    *
    * <p>It is fed from {@link #onPacketOutbound}, not from the places that build the
    * bursts, so it counts every digging packet this client sends while the module is
    * on -- the ones vanilla sends for a block broken by hand included, which the
    * server counts too.
    */
   private final GrimBreakBalance grimBalance = new GrimBreakBalance(this::breakDamageAt);

   /** Whether the break balance was over budget at the last finish, so it is said once. */
   private boolean balanceWasOverBudget;

   /**
    * When the closing stop went out for a position, for the log line at its break.
    *
    * <p>The delay between that stop and the server's block update is the one number
    * that says whether a break sent short of a full bar is being granted or waited
    * out, and it can only be measured from here.
    */
   private final LinkedHashMap<BlockPos, Long> stopSentAt = new LinkedHashMap<>() {
      protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
         return this.size() > 32;
      }
   };
   private boolean instantTogglePressed = false;
   private boolean autoMineTogglePressed = false;
   private PlayerEntity currentTarget = null;
   private BlockPos lastAutoMineBlock = null;
   private long lastAutoMineTime = 0L;
   private BlockPos lastAntiCrawlBlock = null;
   private long lastAntiCrawlTime = 0L;
   private static final long AUTO_MINE_DELAY_MS = 250L;
   private static final long ANTI_CRAWL_DELAY_MS = 100L;
   private int swappedToSlot = -1;
   private int originalSlot = -1;

   /**
    * True while this module is the one sending an {@code UpdateSelectedSlotC2SPacket}.
    *
    * <p>{@code switch-reset} exists to zero the progress bar when <em>the player</em>
    * changes slot. Now that the tool swap happens before the first mining packet
    * rather than after the last, that reset would fire on the module's own packet
    * and wipe the bar it just started.
    */
   private boolean ownSlotChange = false;

   /** Block the tool is currently being held for, and when the hold started. */
   private BlockPos toolHeldFor = null;
   private long toolHeldSince = 0L;

   /**
    * Ceiling on a tool hold, in milliseconds.
    *
    * <p>Long enough to cover a real break the server is still working through —
    * obsidian with a netherite pickaxe is a few seconds — and short enough that a
    * break the server never grants gives the hand back rather than wedging it.
    */
   private static final long TOOL_HOLD_TIMEOUT_MS = 5000L;
   private InventoryManager inventoryManager;

   public MlepMine() {
      super(HunterBuddyAddon.UTILITY_CATEGORY, "h-mine", "Mines blocks faster");
   }

   public Setting<Double> getSpeedConfig() {
      return this.speedConfig;
   }

   public Setting<MlepMine.SpeedmineMode> getModeConfig() {
      return this.modeConfig;
   }

   public void toggle() {
      if (this.isActive() && (Boolean)this.persistentConfig.get() && this.mc.getNetworkHandler() != null) {
         if (this.mc.player != null) {
            this.mc
               .player
               .sendMessage(
                  Text.literal(
                     "\u00a77[\u00a7bh-mine\u00a77] \u00a7cCannot disable while Persistent mode is active! Disable Persistent first or disconnect from server."
                  ),
                  false
               );
         }
      } else {
         super.toggle();
      }
   }

   public void onActivate() {
      if ((Boolean)this.doubleBreakConfig.get()) {
         this.miningQueue = new MlepMine.FirstOutQueue<>(2);
      } else {
         this.miningQueue = new MlepMine.FirstOutQueue<>(1);
      }

      if (this.swapConfig.get() == MlepMine.Swap.SILENT) {
         this.inventoryManager = InventoryManager.getInstance();
      }

      this.swappedToSlot = -1;
      this.originalSlot = -1;
      this.ownSlotChange = false;
      this.toolHeldFor = null;
      this.toolHeldSince = 0L;
      this.lastAutoMineBlock = null;
      this.lastAutoMineTime = 0L;
      this.lastAntiCrawlBlock = null;
      this.lastAntiCrawlTime = 0L;
   }

   public void onDeactivate() {
      if (!(Boolean)this.persistentConfig.get() || this.mc.getNetworkHandler() == null) {
         if (this.miningQueue != null) {
            this.miningQueue.clear();
         }

         this.pendingQueue.clear();
         this.droppedByPress = null;
         this.fadeList.clear();

         // The slot is held for as long as a block is being mined, so switching
         // the module off mid-block is one of the ways that hold has to end —
         // otherwise the hand keeps the pickaxe until the next break, which for a
         // module that was just turned off never comes.
         if (this.swappedToSlot != -1 && this.originalSlot != -1) {
            this.swapBack(this.originalSlot);
         } else if (this.swapConfig.get() == MlepMine.Swap.SILENT && this.inventoryManager != null) {
            this.inventoryManager.syncToClient();
         }

         this.swappedToSlot = -1;
         this.originalSlot = -1;
         this.ownSlotChange = false;
         this.toolHeldFor = null;
         this.toolHeldSince = 0L;
         this.lastAutoMineBlock = null;
         this.lastAutoMineTime = 0L;
         this.lastAntiCrawlBlock = null;
         this.lastAntiCrawlTime = 0L;
         this.currentTarget = null;
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      if (this.miningQueue != null) {
         this.miningQueue.clear();
      }

      // Positions are meaningless once the world is gone: kept across a
      // reconnect, the backlog would happily promote old coordinates and mine
      // whatever solid block now sits there.
      this.pendingQueue.clear();
      this.droppedByPress = null;
      this.fadeList.clear();
      this.swappedToSlot = -1;
      this.originalSlot = -1;
      this.ownSlotChange = false;
      this.toolHeldFor = null;
      this.toolHeldSince = 0L;
      this.lastAutoMineBlock = null;
      this.lastAutoMineTime = 0L;
      this.lastAntiCrawlBlock = null;
      this.lastAntiCrawlTime = 0L;

      // The anticheat starts a fresh player object on the next join, so carrying
      // the old balances over would price the first blocks there against a buffer
      // nobody is holding any more.
      this.grimBalance.reset();
      this.stopSentAt.clear();
      this.balanceWasOverBudget = false;
      this.currentTarget = null;
   }

   @EventHandler
   public void onPlayerTick(Pre event) {
      // Cleared here and nowhere else. Read at the end of the tick, they would
      // survive every early return -- and a swing left over from the tick you
      // started eating on is the one shape worth avoiding.
      this.workedThisTick = false;
      this.swungThisTick = false;

      if (!this.mc.player.isCreative() && !this.mc.player.isSpectator()) {
         if (!((Keybind)this.autoMineKey.get()).isPressed() || this.mc.currentScreen != null) {
            this.autoMineTogglePressed = false;
         } else if (!this.autoMineTogglePressed) {
            this.autoMineTogglePressed = true;
            this.autoMine.set(!(Boolean)this.autoMine.get());
            if (this.mc.player != null) {
               String status = this.autoMine.get() ? "\u00a7aenabled" : "\u00a7cdisabled";
               this.mc.player.sendMessage(Text.literal("\u00a77[\u00a7bh-mine\u00a77] \u00a7fAuto-mine " + status), false);
            }
         }

         if (!((Keybind)this.queueClearKey.get()).isPressed() || this.mc.currentScreen != null) {
            this.queueClearPressed = false;
         } else if (!this.queueClearPressed) {
            this.queueClearPressed = true;
            int dropped = this.pendingQueue.size();
            this.pendingQueue.clear();
            this.droppedByPress = null;
            if (this.mc.player != null) {
               this.mc.player
                  .sendMessage(Text.literal("\u00a77[\u00a7bh-mine\u00a77] \u00a7fQueue cleared (" + dropped + " block(s))"), false);
            }
         }

         if (!((Keybind)this.instantToggleKey.get()).isPressed() || this.mc.currentScreen != null) {
            this.instantTogglePressed = false;
         } else if (!this.instantTogglePressed) {
            this.instantTogglePressed = true;
            this.instantConfig.set(!(Boolean)this.instantConfig.get());
            if (!(Boolean)this.instantConfig.get()) {
               this.miningQueue.clear();
            }

            if (this.mc.player != null) {
               String status = this.instantConfig.get() ? "\u00a7aenabled" : "\u00a7cdisabled";
               this.mc.player.sendMessage(Text.literal("\u00a77[\u00a7bh-mine\u00a77] \u00a7fInstant mining " + status), false);
            }
         }

         if (this.modeConfig.get() != MlepMine.SpeedmineMode.DAMAGE) {
            // Picks up a double-break the player ticked or unticked since the
            // module was switched on.
            this.ensureMiningQueue();

            if ((Boolean)this.autoMine.get() && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET) {
               int maxQueueSize = this.doubleBreakConfig.get() ? 2 : 1;
               long currentTime = System.currentTimeMillis();
               if ((Boolean)this.antiCrawl.get()
                  && this.mc.player.getPose() == EntityPose.SWIMMING
                  && this.miningQueue.size() < maxQueueSize
                  && currentTime - this.lastAntiCrawlTime >= 100L) {
                  BlockPos crawlBlock = this.getAntiCrawlBlock();
                  if (crawlBlock != null && !this.isMiningBlock(crawlBlock)) {
                     Direction direction = Direction.DOWN;
                     if ((Boolean)this.autoRotate.get() && (Boolean)this.rotateConfig.get()) {
                        float[] rotations = getRotationsTo(this.mc.player.getEyePos(), crawlBlock.toCenterPos());
                        this.applyMineRotation(rotations);
                     }

                     MlepMine.MiningData data = new MlepMine.MiningData(crawlBlock, direction);
                     this.queueMiningData(data);
                     this.lastAntiCrawlBlock = crawlBlock;
                     this.lastAntiCrawlTime = currentTime;
                     if (this.miningQueue.isEmpty()) {
                        return;
                     }
                  }
               }

               this.currentTarget = this.getClosestEnemy();
               if (this.currentTarget != null) {
                  if (this.miningQueue.size() < maxQueueSize && currentTime - this.lastAutoMineTime >= 250L) {
                     BlockPos targetBlock = this.findBestEnemyBlock(this.currentTarget);
                     if (targetBlock != null && !this.isMiningBlock(targetBlock)) {
                        Direction direction = this.getInteractDirection(targetBlock);
                        if (direction != null || !(Boolean)this.strictDirection.get()) {
                           if (direction == null) {
                              direction = Direction.UP;
                           }

                           if ((Boolean)this.autoRotate.get() && (Boolean)this.rotateConfig.get()) {
                              float[] rotations = getRotationsTo(this.mc.player.getEyePos(), targetBlock.toCenterPos());
                              this.applyMineRotation(rotations);
                           }

                           MlepMine.MiningData data = new MlepMine.MiningData(targetBlock, direction);
                           this.queueMiningData(data);
                           this.lastAutoMineBlock = targetBlock;
                           this.lastAutoMineTime = currentTime;
                        }
                     }
                  }
               } else {
                  this.lastAutoMineBlock = null;
               }
            }

            if (!this.miningQueue.isEmpty()) {
               List<MlepMine.MiningData> toRemove = new ArrayList<>();
               long now = System.currentTimeMillis();
               long hotWindow = ((Integer)this.instantWindow.get()).longValue();

               for (MlepMine.MiningData data : this.miningQueue) {
                  if (data.getState().isAir()) {
                     data.resetBreakTime();
                     data.markAir(now);

                     // With instant off, a cleared spot is forgotten, full stop.
                     // The removal that runs at the break itself only fires while
                     // the block is still solid on the client, so a break the
                     // server granted first — the ordinary case at grim speeds —
                     // used to leave the entry sitting there, and the next block
                     // placed on that spot was broken once with nobody having
                     // clicked it.
                     //
                     // With instant on, instant-window is the optional ceiling.
                     // Off by default: the server holds its open break position
                     // until a START goes elsewhere, so forgetting early only
                     // costs a click, and that click restarts the server timer.
                     boolean stayHot = (Boolean)this.instantConfig.get()
                        && (hotWindow <= 0L || !data.airedOut(now, hotWindow));

                     if (!stayHot) {
                        toRemove.add(data);
                        continue;
                     }
                  } else {
                     data.markSolid();
                  }

                  if (!this.isDataPacketMine(data) || !data.getState().isAir() && (!data.hasAttemptedBreak() || !data.passedAttemptedBreakTime(500L))) {
                     float damageDelta = this.calcBlockBreakingDelta(data.getState(), this.mc.world, data.getPos());
                     data.damage(damageDelta);
                     this.workedThisTick = true;
                     if (this.isDataPacketMine(data) && data.getBlockDamage() >= 1.0F && data.getSlot() != -1) {
                        if (this.mc.player.isUsingItem() && !(Boolean)this.multitaskConfig.get()) {
                           return;
                        }

                        if (!data.hasAttemptedBreak()) {
                           data.setAttemptedBreak(true);
                        }
                     }
                  } else {
                     // Deliberately dropped, including with instant on. The server
                     // keeps exactly one open break position, so a second cleared
                     // spot cannot be re-broken by a bare STOP: it is ignored, and
                     // the ABORT behind it logs a position mismatch on every tick.
                     toRemove.add(data);
                  }
               }

               this.miningQueue.removeAll(toRemove);
               MlepMine.MiningData miningData2 = this.miningQueue.getFirst();
               if (miningData2 != null) {
                  double distance = this.mc.player.getEyePos().squaredDistanceTo(miningData2.getPos().toCenterPos());
                  if (distance > (Double)this.rangeConfig.get() * (Double)this.rangeConfig.get()) {
                     this.miningQueue.remove(miningData2);
                  } else if (!miningData2.getState().isAir()) {
                     if (miningData2.getBlockDamage() >= (Double)this.speedConfig.get()
                        && miningData2.hasAttemptedBreak()
                        && miningData2.passedAttemptedBreakTime(500L)) {
                        this.abortMining(miningData2);
                        this.miningQueue.remove(miningData2);
                     }

                     if (miningData2.getBlockDamage() >= (Double)this.speedConfig.get()) {
                        if (this.mc.player.isUsingItem() && !(Boolean)this.multitaskConfig.get()) {
                           return;
                        }

                        // A block put back on a spot that had been cleared. This
                        // break never goes through startMining, so the tool taken
                        // there was already handed back when the spot went empty
                        // and the server would measure this one with whatever is
                        // in the hand — while farming, the stack being placed.
                        // That is the break that silently fails and has you click
                        // again once the 500 ms timeout has let go of the entry.
                        if (miningData2.consumeRehold()) {
                           this.holdToolFor(miningData2);
                        }

                        this.stopMining(miningData2);
                        if (!miningData2.hasAttemptedBreak()) {
                           miningData2.setAttemptedBreak(true);
                        }

                        if (!(Boolean)this.instantConfig.get()) {
                           this.miningQueue.remove(miningData2);
                        }
                     }
                  }
               }
            }

            // Outside the "queue not empty" branch on purpose: with instant off the
            // broken entry is removed, so the queue empties between blocks and a
            // promotion nested in there would never fire again after the first one.
            this.promoteFromQueue();

            // After the promotion, never before: a backlog run would otherwise give
            // the tool back between every pair of blocks and take it again a
            // millisecond later, two slot packets per block for nothing.
            this.releaseTool();

            // Last, so it sees the whole tick: one swing whatever the number of
            // blocks that were worked in it.
            this.swingWhileWorking();
         }
      }
   }

   @EventHandler
   public void onAttackBlock(StartBreakingBlockEvent event) {
      if (!this.mc.player.isCreative() && !this.mc.player.isSpectator() && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET) {
         // Recorded before anything can return, so that a block handed to vanilla
         // or a swing at bedrock still counts as "the button was down last tick".
         int tick = this.mc.player.age;
         // The backwards test is not paranoia: player age restarts at zero on
         // death and on a dimension change, and without it every event after one
         // of those reads as a repeat until the counter climbs back — the gesture
         // that drops a block from the queue would be dead for the best part of
         // an hour.
         boolean fresh = tick < this.lastEventTick || tick - this.lastEventTick > 1;
         this.lastEventTick = tick;

         BlockState blockState = this.mc.world.getBlockState(event.blockPos);

         // Left to the normal client break, and the event is not cancelled at
         // all. Vanilla predicts the break locally rather than waiting for the
         // round trip, so it is the faster of the two on anything that goes down
         // in one hit, and it sends none of the packets a break-rate check counts.
         if (this.leaveToVanilla(blockState, event.blockPos)) return;

         event.cancel();

         if (blockState.getHardness(this.mc.world, event.blockPos) != -1.0F && !blockState.isAir()) {
            this.clickMine(new MlepMine.MiningData(event.blockPos, event.direction), fresh);

            // Through the latch rather than around it. This handler runs after the
            // tick event, so it is the last of the three to have a chance to swing,
            // and swinging unconditionally here is what doubled the animation on
            // every tick of a held button. With no animation mode chosen nothing
            // else ever takes the latch, so this still swings exactly as it did.
            this.swingOnce();
         }
      }
   }

   @EventHandler
   public void onPacketOutbound(Send event) {
      this.feedGrimBalance(event);

      if (event.packet instanceof PlayerActionC2SPacket packet
         && packet.getAction() == Action.STOP_DESTROY_BLOCK
         && this.modeConfig.get() == MlepMine.SpeedmineMode.DAMAGE
         && (Boolean)this.grimConfig.get()) {
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, packet.getPos(), packet.getDirection()));
      }

      // Only a slot change the player made counts. The module's own swap and its
      // restore are both wrapped in ownSlotChange, which is the whole point: the
      // swap now lands before the first mining packet, so without this guard it
      // would reset the progress of the block it is about to start.
      if (event.packet instanceof UpdateSelectedSlotC2SPacket
         && (Boolean)this.switchResetConfig.get()
         && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET
         && !this.ownSlotChange) {
         for (MlepMine.MiningData data : this.ensureMiningQueue()) {
            data.resetDamage();
         }
      }
   }

   @EventHandler
   public void onPacketInbound(Receive event) {
      if (this.mc.player != null && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET) {
         if (event.packet instanceof BlockUpdateS2CPacket packet) {
            this.handleBlockUpdatePacket(packet);
         } else if (event.packet instanceof BundleS2CPacket packet) {
            for (Packet<?> packet1 : packet.getPackets()) {
               if (packet1 instanceof BlockUpdateS2CPacket packet2) {
                  this.handleBlockUpdatePacket(packet2);
               }
            }
         }
      }
   }

   /**
    * Hands the anticheat mirror every digging packet on its way out.
    *
    * <p>Reading the wire rather than the call sites is deliberate. The bursts are
    * built in three places and the swing that follows them in a fourth, and the
    * check counts what arrives, not what any of those meant to send.
    */
   private void feedGrimBalance(Send event) {
      if (event.isCancelled()) return;
      if (!(Boolean)this.grimConfig.get() || !(Boolean)this.grimBalanceConfig.get()) return;

      long now = System.currentTimeMillis();

      if (event.packet instanceof PlayerActionC2SPacket packet) {
         if (packet.getAction() == Action.START_DESTROY_BLOCK) {
            this.grimBalance.onStart(packet.getPos(), now);
         } else if (packet.getAction() == Action.STOP_DESTROY_BLOCK) {
            this.grimBalance.onFinish(packet.getPos(), now);
            this.reportBalance(packet.getPos());
         }
      } else if (event.packet instanceof PlayerMoveC2SPacket || event.packet instanceof HandSwingC2SPacket) {
         // The check re-reads the block being broken on every one of these and keeps
         // the best speed it ever saw, which is how a tool picked up mid-block is
         // credited. A mirror that ignored them would price every block at whatever
         // the hand held when the start went out.
         this.grimBalance.onFlying();
      }
   }

   /**
    * Says where the two balances stand, once a finish has been priced.
    *
    * <p>The break balance is the one worth a warning: no wait brings it down, so a
    * run of hard blocks walks it up to nine times what one of them is predicted to
    * cost and leaves it there. Under the flag that is free; over it, every further
    * break is measured from a buffer that is already full.
    */
   private void reportBalance(BlockPos pos) {
      if ((Boolean)this.grimBalanceLog.get()) {
         HunterBuddyAddon.LOG
            .info(
               String.format(
                  "h-mine: finish at %d %d %d, predicted %.0f ms, measured %.0f ms, diff %.0f -- delay balance %.0f, break balance %.0f, flag at %.0f",
                  pos.getX(),
                  pos.getY(),
                  pos.getZ(),
                  this.grimBalance.lastPredicted(),
                  this.grimBalance.lastReal(),
                  this.grimBalance.lastDiff(),
                  this.grimBalance.delayBalance(),
                  this.grimBalance.breakBalance(),
                  GrimBreakBalance.FLAG
               )
            );
      }

      // Said once per crossing, and only while the log is on. It used to be said
      // every ten seconds regardless, which on obsidian is every ten seconds
      // forever: the balance sits pinned at the ceiling there, so a warning on a
      // timer is a warning that never stops.
      boolean over = this.grimBalance.breakBalance() > (Integer)this.grimBudget.get();

      if (over && !this.balanceWasOverBudget && (Boolean)this.grimBalanceLog.get()) {
         this.warning(
            "Break balance at %.0f of the anticheat's %.0f. Waiting will not bring it down -- only easier blocks will.",
            this.grimBalance.breakBalance(),
            GrimBreakBalance.FLAG
         );
      }

      this.balanceWasOverBudget = over;
   }

   private void handleBlockUpdatePacket(BlockUpdateS2CPacket packet) {
      if (packet.getState().isAir()) {
         Long sentAt = this.stopSentAt.remove(packet.getPos());
         if (sentAt != null && (Boolean)this.grimBalanceLog.get()) {
            HunterBuddyAddon.LOG
               .info(
                  String.format(
                     "h-mine: server confirmed %d %d %d gone %d ms after the closing stop",
                     packet.getPos().getX(),
                     packet.getPos().getY(),
                     packet.getPos().getZ(),
                     System.currentTimeMillis() - sentAt
                  )
               );
         }

         for (MlepMine.MiningData data : this.miningQueue) {
            if (data.hasAttemptedBreak() && data.getPos().equals(packet.getPos())) {
               data.setAttemptedBreak(false);

               // The server confirming the block is gone, which is the only moment a break is
               // certain: the client-side bar filling proves nothing.
               com.hunterbuddy.util.SessionStats.get().onBlockMined();
            }
         }
      }
   }

   @EventHandler
   public void onRenderWorld(Render3DEvent event) {
      if (!this.mc.player.isCreative() && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET && (Boolean)this.render.get()) {
         this.renderPendingQueue(event);

         for (MlepMine.MiningData data : this.miningQueue) {
            if (!data.getState().isAir() && !this.fadeList.containsKey(data)) {
               this.fadeList.put(data, new MlepMine.Animation(true, ((Integer)this.fadeTimeConfig.get()).intValue()));
            }
         }

         for (Entry<MlepMine.MiningData, MlepMine.Animation> entry : this.fadeList.entrySet()) {
            MlepMine.MiningData data = entry.getKey();
            boolean isActive = this.miningQueue.contains(data) && !data.getState().isAir();
            entry.getValue().setState(isActive);
         }

         for (Entry<MlepMine.MiningData, MlepMine.Animation> set : this.fadeList.entrySet()) {
            this.renderMiningEntry(event, set.getKey(), set.getValue().getFactor(), set.getValue().since());
         }

         this.fadeList.entrySet().removeIf(e -> e.getValue().getFactor() == 0.0);
      }
   }

   /** Blocks waiting their turn, drawn in the order they will actually be mined. */
   private void renderPendingQueue(Render3DEvent event) {
      if (!(Boolean)this.queueConfig.get() || this.pendingQueue.isEmpty()) return;

      SettingColor base = (SettingColor)this.queueColor.get();
      Vec3d previous = this.chainAnchor();

      for (int i = 0; i < this.pendingQueue.size(); i++) {
         Vec3d center = this.pendingQueue.get(i).getPos().toCenterPos();

         // Rank you can see: the next one in line is the biggest and brightest,
         // each one behind it a little smaller and dimmer. Four steps is plenty,
         // past that they all just read as "later".
         int rank = Math.min(i, 4);
         double half = (0.62 - 0.06 * rank) / 2.0;
         float dim = 1.0F - 0.13F * rank;

         if ((Boolean)this.queueChain.get() && previous != null) {
            event.renderer
               .line(
                  previous.x,
                  previous.y,
                  previous.z,
                  center.x,
                  center.y,
                  center.z,
                  this.shade(base, Math.round(90.0F * dim)),
                  this.shade(base, Math.round(220.0F * dim))
               );
         }

         this.drawBox(
            event,
            new Box(center, center).expand(half, half, half),
            base,
            Math.round(90.0F * dim),
            Math.round(255.0F * dim),
            (ShapeMode)this.shapeMode.get()
         );

         previous = center;
      }
   }

   /** Where the chain starts: whatever is being broken right now, if anything is. */
   private Vec3d chainAnchor() {
      if (this.miningQueue == null) return null;

      for (MlepMine.MiningData data : this.miningQueue) {
         if (!data.getState().isAir()) return data.getPos().toCenterPos();
      }

      return null;
   }

   private void renderMiningEntry(Render3DEvent event, MlepMine.MiningData data, float factor, long since) {
      if (factor <= 0.0F || this.mc.world == null) return;

      BlockPos pos = data.getPos();
      VoxelShape outlineShape = data.getState().getOutlineShape(this.mc.world, pos);
      Box local = (outlineShape.isEmpty() ? VoxelShapes.fullCube() : outlineShape).getBoundingBox();
      Box full = new Box(
         pos.getX() + local.minX,
         pos.getY() + local.minY,
         pos.getZ() + local.minZ,
         pos.getX() + local.maxX,
         pos.getY() + local.maxY,
         pos.getZ() + local.maxZ
      );

      boolean done = data.getState().isAir() || data.getBlockDamage() >= 0.95F;
      SettingColor base = done ? (SettingColor)this.colorDoneConfig.get() : (SettingColor)this.colorConfig.get();

      // The whole block, faintly. The progress box starts as a dot in the middle
      // of it, so on its own it never showed which block was about to go.
      if ((Boolean)this.outlineConfig.get()) {
         this.drawBox(event, full, base, 0, Math.round(55.0F * factor), ShapeMode.Lines);
      }

      float total = this.isDataPacketMine(data) ? 1.0F : ((Double)this.speedConfig.get()).floatValue();
      float scale = data.getState().isAir()
         ? 1.0F
         : MathHelper.clamp((data.getBlockDamage() + (data.getBlockDamage() - data.getLastDamage()) * event.tickDelta) / total, 0.0F, 1.0F);
      if (this.styleConfig.get() == MlepMine.RenderStyle.CLASSIC) {
         Vec3d center = full.getCenter();
         Box grown = new Box(center, center)
            .expand(
               (full.maxX - full.minX) / 2.0 * scale,
               (full.maxY - full.minY) / 2.0 * scale,
               (full.maxZ - full.minZ) / 2.0 * scale
            );
         this.drawBox(event, grown, base, Math.round(40.0F * factor), Math.round(100.0F * factor), (ShapeMode)this.shapeMode.get());
         return;
      }

      // Progress as a level rising inside the block rather than a box swelling
      // out of its middle. Two things fall out of that: a height is readable from
      // the side, where a box growing in every direction at once is not, and the
      // bright line sitting on the level gives the eye something crisp to land on
      // instead of a soft cube.
      //
      // Pulse is the same shape drawn somewhere else entirely. Fill lays the
      // level down at a flat alpha of 60 in the colour set in the menu; Pulse
      // swings that from half to nearly twice, and washes the colour towards
      // white as it rises. Both halves matter: an alpha that breathes on its own
      // is a difference you have to hunt for, whatever its amplitude, and it was
      // reported twice as no difference at all. The line does not breathe -- it
      // is where the eye reads progress, and a marker that dims is one that has
      // to be read twice.

      boolean pulsing = this.styleConfig.get() == MlepMine.RenderStyle.PULSE;
      float beat = pulsing ? this.breath(since) : 0.0F;

      double level = full.minY + (full.maxY - full.minY) * scale;
      this.drawFill(
         event,
         new Box(full.minX, full.minY, full.minZ, full.maxX, level, full.maxZ),
         base,
         pulsing ? 30.0F + 110.0F * beat : 60.0F,
         pulsing ? 0.7F * beat : 0.0F,
         factor
      );
      this.drawBox(
         event,
         new Box(full.minX, level, full.minZ, full.maxX, level, full.maxZ),
         base,
         0,
         Math.round(230.0F * factor),
         ShapeMode.Lines
      );
   }

   /**
    * Runs 0 to 1 and back, once every pulse-rate second, counted from {@code since}.
    *
    * <p>Timed from the block rather than from the clock, which is what makes the
    * slider mean anything. On the wall clock every block picked the phase up
    * wherever it happened to be: a block that lives a fifth of a second showed one
    * arbitrary brightness and no motion, and turning the rate up or down only drew
    * a different arbitrary brightness. From zero it always starts dark and climbs,
    * so even the shortest block shows the beginning of a breath -- and how far it
    * gets through one is exactly what the rate sets.
    *
    * <p>Cosine, not sine, for that reason: sine starts halfway up.
    *
    * <p>The epoch is no longer the argument, so the old precision trap is gone
    * with it. A few seconds turned into radians is a small number, and
    * {@code MathHelper.cos} takes a double and indexes its table through a long.
    */
   private float breath(long since) {
      double phase = (System.currentTimeMillis() - since) / 1000.0 * (Double)this.pulseRate.get();
      return 0.5F - 0.5F * MathHelper.cos(phase * Math.PI * 2.0);
   }

   /**
    * The level inside the block, in a colour of its own.
    *
    * <p>Apart from {@link #drawBox} because the breath moves the colour and not
    * only its alpha, and washing a colour towards white is not something a shade
    * of it can do.
    */
   private void drawFill(Render3DEvent event, Box box, SettingColor base, float alpha, float whiten, float factor) {
      meteordevelopment.meteorclient.utils.render.color.Color side =
         new meteordevelopment.meteorclient.utils.render.color.Color(
            base.r + Math.round((255 - base.r) * whiten),
            base.g + Math.round((255 - base.g) * whiten),
            base.b + Math.round((255 - base.b) * whiten),
            MathHelper.clamp(Math.round(alpha * factor) * base.a / 255, 0, 255)
         );

      event.renderer
         .box(box, side, new meteordevelopment.meteorclient.utils.render.color.Color(0, 0, 0, 0), ShapeMode.Sides, 0);
   }

   /**
    * One box in the module's colours.
    *
    * <p>Alphas are written for a fully opaque colour and then scaled by the alpha
    * actually set in the menu, so turning a colour down there dims every shell of
    * the halo with it instead of leaving the glow at full strength.
    */
   private void drawBox(Render3DEvent event, Box box, SettingColor base, int sideAlpha, int lineAlpha, ShapeMode mode) {
      event.renderer.box(box, this.shade(base, sideAlpha), this.shade(base, lineAlpha), mode, 0);
   }

   private meteordevelopment.meteorclient.utils.render.color.Color shade(SettingColor base, int alpha) {
      return new meteordevelopment.meteorclient.utils.render.color.Color(
         base.r, base.g, base.b, MathHelper.clamp(alpha * base.a / 255, 0, 255)
      );
   }

   @EventHandler
   private void onRenderQueueNumbers(Render2DEvent event) {
      if (this.mc.player == null || this.mc.world == null) return;
      if (this.modeConfig.get() != MlepMine.SpeedmineMode.PACKET) return;
      if (!(Boolean)this.render.get() || !(Boolean)this.queueConfig.get() || !(Boolean)this.queueNumbers.get()) return;
      if (this.pendingQueue.isEmpty()) return;

      SettingColor base = (SettingColor)this.queueColor.get();

      for (int i = 0; i < this.pendingQueue.size(); i++) {
         BlockPos pos = this.pendingQueue.get(i).getPos();
         org.joml.Vector3d screen = new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5);
         if (!NametagUtils.to2D(screen, 1.0)) continue;

         String text = Integer.toString(i + 1);
         int width = this.mc.textRenderer.getWidth(text);

         NametagUtils.begin(screen, event.drawContext);
         event.drawContext
            .fill(-width / 2 - 2, -1, width / 2 + 2, 9, new meteordevelopment.meteorclient.utils.render.color.Color(0, 0, 0, 150).getPacked());
         // Full alpha on purpose: the queue colour is deliberately faint for the
         // boxes, and a rank you cannot read is worth nothing.
         event.drawContext
            .drawText(
               this.mc.textRenderer,
               text,
               -width / 2,
               0,
               new meteordevelopment.meteorclient.utils.render.color.Color(base.r, base.g, base.b, 255).getPacked(),
               true
            );
         NametagUtils.end(event.drawContext);
      }
   }

   private MlepMine.FirstOutQueue<MlepMine.MiningData> ensureMiningQueue() {
      int max = (Boolean)this.doubleBreakConfig.get() ? 2 : 1;

      // The capacity used to be frozen at activation. Unticking double-break on a
      // running module left the two slot queue in place, so two blocks kept going
      // out together until the next reload.
      if (this.miningQueue == null) {
         this.miningQueue = new MlepMine.FirstOutQueue<>(max);
      } else {
         this.miningQueue.setMaxSize(max);
      }

      return this.miningQueue;
   }

   public void clickMine(MlepMine.MiningData miningData) {
      this.clickMine(miningData, true);
   }

   /**
    * @param fresh whether this is a real press rather than one of the repeats a
    *              held button produces every tick
    */
   public void clickMine(MlepMine.MiningData miningData, boolean fresh) {
      BlockPos pos = miningData.getPos();
      boolean queued = (Boolean)this.queueConfig.get() && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET;

      // Clicking a waiting block again takes it back out, and only a real press
      // may do that. A held button repeats the event every tick and Baritone
      // never releases at all, so a repeat allowed to remove would empty the
      // queue as fast as it filled it — and in a tunnel, where Baritone
      // alternates head block and feet block, it would take out the one it
      // queued a tick earlier and then swing at nothing.
      // A press forgets whatever the previous one dropped.
      if (fresh) this.droppedByPress = null;

      if (queued && fresh && this.pendingQueue.removeIf(d -> d.getPos().equals(pos))) {
         this.droppedByPress = pos.toImmutable();
         return;
      }

      // The rest of the press that dropped it must not hand it straight back. A
      // human click lasts two or three ticks: the first removed the block, and
      // the repeats behind it found it in neither list and put it back at the end
      // of the line, so the gesture stopped removing and started reordering.
      if (pos.equals(this.droppedByPress)) return;

      // Already ours. A repeat has nothing to add.
      if (this.isMiningBlock(pos)) return;
      if (this.pendingQueue.stream().anyMatch(d -> d.getPos().equals(pos))) return;
      if (miningData.getState().isAir()) return;

      // Anything already waiting has to be served first. Testing only for a block
      // still being broken left a hole between two blocks — and the 280 ms grim
      // delay makes that hole wide — through which a fresh click went straight to
      // the front, so the last block clicked was mined second while the ones
      // queued before it kept waiting.
      if (queued && (this.hasSolidMiningEntry() || !this.pendingQueue.isEmpty())) {
         if (this.pendingQueue.size() >= PENDING_QUEUE_LIMIT) return;

         this.pendingQueue.add(miningData);
         return;
      }

      // One door for every start, the same 280 ms the backlog already waited. A
      // direct click walked straight past it, which mattered little while repeats
      // were being swallowed and matters a lot now that they retry: a held button
      // on a block that keeps failing would fire a START every tick, twenty a
      // second, which is precisely the shape a break-rate check is watching for.
      if (this.isBlockDelayGrim()) {
         this.parkNext(miningData, queued);
         return;
      }

      // Repeats reach this on purpose. A block the 500 ms attempt timeout gave up
      // on is in neither list any more, and this is what puts it back: holding the
      // button retries it, which is what holding the button has always meant.
      int maxQueueSize = (Boolean) this.doubleBreakConfig.get() ? 2 : 1;
      if (this.ensureMiningQueue().size() <= maxQueueSize) {
         this.queueMiningData(miningData);
      }
   }

   /**
    * Holds a block until the grim door opens.
    *
    * <p>With the queue off there is room for exactly one, and the newest click
    * takes it — which is what clicking a second block has always meant without
    * the queue, only now it costs a fraction of a second instead of a packet.
    */
   private void parkNext(MlepMine.MiningData data, boolean queued) {
      if (!queued) {
         this.pendingQueue.clear();
      } else if (this.pendingQueue.size() >= PENDING_QUEUE_LIMIT) {
         return;
      }

      this.pendingQueue.add(data);
   }

   /**
    * True for a block this module refuses to touch, leaving the normal client
    * break to handle it.
    *
    * <p>The held item decides, not the best tool in the hotbar: vanilla breaks
    * with what is selected, and the module's own swap has not run at this point.
    */
   private boolean leaveToVanilla(BlockState state, BlockPos pos) {
      if (state.isAir()) return false;
      if (this.vanillaBlocks.get().contains(state.getBlock())) return true;

      return (Boolean)this.vanillaOneShot.get()
         && state.calcBlockBreakingDelta(this.mc.player, this.mc.world, pos) >= 1.0F;
   }

   /** True while any queued entry still has a block left to break. */
   private boolean hasSolidMiningEntry() {
      if (this.miningQueue == null) return false;

      for (MlepMine.MiningData data : this.miningQueue) {
         if (!data.getState().isAir()) return true;
      }

      return false;
   }

   /**
    * Moves the next clicked block into the real mining queue.
    *
    * <p>The gate is "nothing solid left", not "queue empty". With {@code instant}
    * on, the broken entry is deliberately kept in the queue — that is what makes
    * a block replaced on the same spot break again at once — so waiting for an
    * empty queue would stall the backlog after the very first block. Keeping the
    * air entry around also means {@code addFirst} slides it into the second slot,
    * so the spot you just cleared stays the hot one while the next block breaks.
    *
    * <p>An entry that stays solid because the server refused the break is dropped
    * by the existing 500 ms attempt timeout, so this cannot deadlock.
    */
   private void promoteFromQueue() {
      // No test on the queue setting any more: with it off, this list still holds
      // the one block a click parked while the grim door was shut, and something
      // has to let it through.
      if (this.pendingQueue.isEmpty()) return;
      if (this.modeConfig.get() != MlepMine.SpeedmineMode.PACKET) return;
      if (this.mc.player == null || this.mc.world == null) return;
      if (this.mc.player.isUsingItem() && !(Boolean)this.multitaskConfig.get()) return;
      if (this.isBlockDelayGrim()) return;

      // One block at a time is the whole point of the queue. Without it, the list
      // holds nothing but the click the door made wait, and that click is owed
      // only the door: double-break exists to run two at once, and waiting for an
      // empty queue here would quietly cancel it for 280 ms after every break.
      if ((Boolean)this.queueConfig.get() && this.hasSolidMiningEntry()) return;

      double range = (Double)this.rangeConfig.get();
      double rangeSq = range * range;

      while (!this.pendingQueue.isEmpty()) {
         MlepMine.MiningData next = this.pendingQueue.remove(0);
         BlockPos pos = next.getPos();
         BlockState state = this.mc.world.getBlockState(pos);

         // Dropped without a word: the queue render disappearing is the feedback,
         // and a line per block would spam the moment someone else mines part of
         // your selection or you drift out of range.
         if (state.isAir()) continue;
         if (state.getHardness(this.mc.world, pos) < 0.0F) continue;
         if (this.mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > rangeSq) continue;

         this.queueMiningData(new MlepMine.MiningData(pos, next.getDirection()));
         return;
      }
   }

   public void queueMiningData(MlepMine.MiningData data) {
      if (!data.getState().isAir()) {
         MlepMine.FirstOutQueue<MlepMine.MiningData> queue = this.ensureMiningQueue();
         if (queue.stream().anyMatch(p1 -> data.getPos().equals(p1.getPos()))) {
            return;
         }

         if (this.startMining(data)) {
            queue.addFirst(data);
         }
      }
   }

   private boolean startMining(MlepMine.MiningData data) {
      if (data.isStarted()) {
         return false;
      }

      data.setStarted();

      // Before the burst below, not after it. Packet mining sends START and STOP
      // in the same breath and then lets the server accumulate the break on its
      // own clock, recomputing the speed each tick from the item it sees in the
      // hand. Swapping once the client-side bar is full — which is what this used
      // to do — showed the server the pickaxe for three ticks at the very end,
      // long after it had measured the whole window at bare-hand speed. On
      // anything harder than dirt the block simply never broke: the bar filled at
      // pickaxe speed, the 500 ms attempt timeout dropped the entry, and the
      // outline vanished with nothing to show for it.
      this.holdToolFor(data);

      float breakDelta = this.calcBlockBreakingDelta(data.getState(), this.mc.world, data.getPos());
      boolean isInstantBreak = breakDelta >= 1.0F;
      boolean afterInstant = this.lastWasInstant;
      this.lastWasInstant = isInstantBreak;

      if ((Boolean)this.grimNewConfig.get()) {
         if (!(Boolean)this.miningFix.get()) {
            // The head stop exists to let the anticheat's delay balance decay, and
            // it costs a finish on a position that was never started -- which is
            // flagged, except where the previous block could be broken in one blow.
            // Sent only inside that exemption, it keeps the decay and loses the
            // flag; outside it, the burst starts straight away.
            if (afterInstant) {
               this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, data.getPos(), data.getDirection()));
            }

            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, data.getPos(), data.getDirection()));
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, data.getPos(), data.getDirection()));
         } else {
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, data.getPos(), data.getDirection()));
         }

         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, data.getPos(), data.getDirection()));

         // The first of the three becomes a real swing when an animation is asked
         // for: a bare packet is seen by everyone except you, which is the wrong
         // way round for the block that starts under your own crosshair.
         if (this.swing.get() == MlepMine.Swing.NONE || this.swungThisTick) {
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         } else {
            this.swingOnce();
         }

         if (!isInstantBreak) {
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         }

         return true;
      } else {
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, data.getPos(), data.getDirection()));
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, data.getPos(), data.getDirection()));
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, data.getPos(), data.getDirection()));
         this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         if (!isInstantBreak) {
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, data.getPos(), data.getDirection()));
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, data.getPos(), data.getDirection()));
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, data.getPos(), data.getDirection()));
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         }

         return true;
      }
   }

   private void abortMining(MlepMine.MiningData data) {
      if (data.isStarted() && !data.getState().isAir()) {
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, data.getPos(), data.getDirection()));
      }
   }

   private void stopMining(MlepMine.MiningData data) {
      if (data.isStarted() && !data.getState().isAir()) {
         if ((Boolean)this.rotateConfig.get()) {
            float[] rotations = getRotationsTo(this.mc.player.getEyePos(), data.getPos().toCenterPos());
            this.applyMineRotation(rotations);
         }

         // No swap here any more. The tool was taken in startMining and is held
         // until the block is confirmed gone; changing slot at this point would
         // restart the server's accumulation on the very block that is one packet
         // away from breaking.
         this.stopMiningInternal(data);
         this.lastBreak = System.currentTimeMillis();

         // Only while the log is on: this map exists to time the server's answer,
         // and nothing else reads it.
         if ((Boolean)this.grimBalanceLog.get()) {
            this.stopSentAt.put(data.getPos().toImmutable(), this.lastBreak);
         }
      }
   }

   /**
    * Takes the best hotbar tool for this block and keeps it until the block is gone.
    *
    * <p>Called once, from {@link #startMining}, before any mining packet leaves.
    * It deliberately does nothing on a block already under way: a slot change
    * during the server's accumulation restarts that accumulation, which is the
    * same reason {@code switch-reset} exists on the client side.
    *
    * <p>The candidate has to beat what is already in the hand, not merely exist.
    * The old selection walked the hotbar with a floor of zero, and every stack —
    * dirt, a sword, an empty slot — answers 1.0 when it has no {@code TOOL}
    * component, so slot 0 always won and the module swapped to it even when the
    * hand already held the right pickaxe.
    */
   private void holdToolFor(MlepMine.MiningData data) {
      if (this.swapConfig.get() == MlepMine.Swap.OFF) return;
      if (this.mc.player == null) return;

      int best = this.betterToolSlot(data.getState());

      // Nothing beats the hand for this block. If a slot is already held from the
      // previous block of a run, keep it and just move the watch onto the new
      // position — giving it back here would be a slot change mid-run, exactly
      // what the hold exists to avoid. Under NORMAL this is the normal path from
      // the second block onwards, since the hand is by then the pickaxe itself.
      if (best == -1) {
         if (this.swappedToSlot != -1) {
            this.toolHeldFor = data.getPos();
            this.toolHeldSince = System.currentTimeMillis();
         }

         return;
      }

      if (best != this.swappedToSlot) {
         if (this.swappedToSlot == -1) {
            this.originalSlot = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
         }

         this.swapTo(best);
         this.swappedToSlot = best;
      }

      this.toolHeldFor = data.getPos();
      this.toolHeldSince = System.currentTimeMillis();
   }

   /**
    * Gives the hand back once nothing solid is left to mine.
    *
    * <p>An empty queue is not enough on its own. With {@code instant} off the
    * entry is dropped the moment the client-side bar fills, which is precisely the
    * moment the server may still be accumulating — releasing there would hand the
    * pickaxe back a tick before the break and reproduce the bug this whole change
    * exists to fix. So the real signal is the world block turning to air, which is
    * the block update the server sends when it actually breaks it.
    *
    * <p>The timeout is the way out when that update never comes: a break the
    * server refuses, a chunk unloading, a block someone else replaced. Without it
    * the hand would stay on the pickaxe indefinitely.
    */
   private void releaseTool() {
      if (this.swappedToSlot == -1 || this.originalSlot == -1) return;
      if (this.hasSolidMiningEntry()) return;

      if (this.toolHeldFor != null
         && this.mc.world != null
         && !this.mc.world.getBlockState(this.toolHeldFor).isAir()
         && System.currentTimeMillis() - this.toolHeldSince < TOOL_HOLD_TIMEOUT_MS) {
         return;
      }

      // The player reached for another slot themselves. Their choice wins: drop
      // the hold without dragging them back to where the module found them.
      boolean playerMoved = this.swapConfig.get() == MlepMine.Swap.NORMAL
         && ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() != this.swappedToSlot;

      if (!playerMoved) {
         this.swapBack(this.originalSlot);
      }

      this.swappedToSlot = -1;
      this.originalSlot = -1;
      this.toolHeldFor = null;
   }

   /**
    * Hotbar slot that mines this block strictly faster than the held item, or -1.
    *
    * <p>Hotbar only, and that is a hard limit rather than an oversight: the slot
    * packet can only name indices 0-8. Reaching a pickaxe left in the backpack
    * would mean moving it into the hotbar first — a real inventory click, visible
    * to the server and to anyone watching. The setting description says so.
    */
   private int betterToolSlot(BlockState state) {
      var inventory = this.mc.player.getInventory();
      int held = ((PlayerInventoryAccessor)inventory).getSelectedSlot();
      float bestSpeed = inventory.getStack(held).getMiningSpeedMultiplier(state);
      int bestSlot = -1;

      for (int i = 0; i < 9; i++) {
         float speed = inventory.getStack(i).getMiningSpeedMultiplier(state);
         if (speed > bestSpeed) {
            bestSpeed = speed;
            bestSlot = i;
         }
      }

      return bestSlot;
   }

   private void swapTo(int slot) {
      this.ownSlotChange = true;

      try {
         switch ((MlepMine.Swap)this.swapConfig.get()) {
            case NORMAL:
               ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(slot);
               this.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
               break;
            case SILENT:
               if (this.inventoryManager == null) {
                  this.inventoryManager = InventoryManager.getInstance();
               }

               this.inventoryManager.setSlot(slot);
         }
      } finally {
         this.ownSlotChange = false;
      }
   }

   private void swapBack(int originalSlot) {
      this.ownSlotChange = true;

      try {
         switch ((MlepMine.Swap)this.swapConfig.get()) {
            case NORMAL:
               ((PlayerInventoryAccessor)this.mc.player.getInventory()).setSelectedSlot(originalSlot);
               this.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
               break;
            case SILENT:
               if (this.inventoryManager == null) {
                  this.inventoryManager = InventoryManager.getInstance();
               }

               this.inventoryManager.syncToClient();
         }
      } finally {
         this.ownSlotChange = false;
      }
   }

    private void stopMiningInternal(MlepMine.MiningData data) {
      Direction face = this.sendDirection(data);

      this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, data.getPos(), face));

      // No cancel behind it. On the server this one was a no-op -- the stop above
      // either destroyed the block or armed the delayed destroy, which an abort
      // does not touch -- while on the wire it was a second cancel on a position
      // already cancelled by the burst, which is the exact shape a break check
      // looks for. One packet fewer, one flag fewer, per block.
      this.swingForDig();
   }

   /**
    * The arm, on a tick that sends a dig.
    *
    * <p>Both modes go through the full swing rather than the bare packet: the bare
    * one is seen by everybody except you, which is the wrong way round for
    * something whose purpose is to look ordinary.
    */
   private void swingForDig() {
      if (this.swing.get() != MlepMine.Swing.HAMMER) return;

      this.swingOnce();
   }

   /**
    * Whether a start has to wait.
    *
    * <p>The fixed door underneath is what the anticheat's delay balance costs in the
    * worst case: hold every start for 280 ms and the balance decays on every one of
    * them, so it never climbs and never flags. It also never spends. The mirror
    * spends it: after a pause the buffer is empty and the first blocks go through
    * with no wait at all, which is what mining a stash looks like -- a few blocks,
    * then flying, then a few more.
    *
    * <p>Only the delay balance is a door. The other one is not payable by waiting --
    * nothing brings it down but another finish -- so it is watched and reported in
    * {@link #reportBalance} rather than waited on, because a door that could never
    * open would simply stop the module.
    */
   public boolean isBlockDelayGrim() {
      if (!(Boolean)this.grimConfig.get()) return false;

      if (!(Boolean)this.grimBalanceConfig.get()) {
         return System.currentTimeMillis() - this.lastBreak <= 280L;
      }

      return !this.grimBalance.canStart((Integer)this.grimBudget.get(), System.currentTimeMillis());
   }

   /**
    * Damage per tick the server will measure on a block, as this client computes it.
    *
    * <p>{@link #calcBlockBreakingDelta} is the right number rather than an
    * approximation of it: it already walks the hotbar for the tool the swap is about
    * to hold, and the anticheat reads the item in the hand at that same moment.
    */
   private double breakDamageAt(BlockPos pos) {
      if (this.mc.player == null || this.mc.world == null) return 0.0;

      BlockState state = this.mc.world.getBlockState(pos);

      // Air has no hardness, and dividing by it is the whole point: a spot the
      // server has already cleared is predicted to take no time at all.
      if (state.isAir()) return Double.POSITIVE_INFINITY;

      return this.calcBlockBreakingDelta(state, this.mc.world, pos);
   }

   private boolean isDataPacketMine(MlepMine.MiningData data) {
      return this.miningQueue.size() == 2 && data == this.miningQueue.getLast();
   }

   public float calcBlockBreakingDelta(BlockState state, BlockView world, BlockPos pos) {
      if (this.swapConfig.get() == MlepMine.Swap.OFF) {
         return state.calcBlockBreakingDelta(this.mc.player, this.mc.world, pos);
      }

      float f = state.getHardness(world, pos);
      if (f == -1.0F) {
         return 0.0F;
      }

      int i = this.canHarvest(state) ? 30 : 100;
      return this.getBlockBreakingSpeed(state) / f / i;
   }

   private float getBlockBreakingSpeed(BlockState block) {
      int tool = this.getBestTool(block);
      float f = this.mc.player.getInventory().getStack(tool).getMiningSpeedMultiplier(block);
      if (f > 1.0F) {
         ItemStack stack = this.mc.player.getInventory().getStack(tool);
         int i = 0;
         ItemEnchantmentsComponent enchantments = stack.getEnchantments();

         for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
            if (((RegistryEntry)entry.getKey()).matchesKey(Enchantments.EFFICIENCY)) {
               i = entry.getIntValue();
               break;
            }
         }

         if (i > 0 && !stack.isEmpty()) {
            f += i * i + 1;
         }
      }

      if (StatusEffectUtil.hasHaste(this.mc.player)) {
         f *= 1.0F + (StatusEffectUtil.getHasteAmplifier(this.mc.player) + 1) * 0.2F;
      }

      if (this.mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
         float g = switch (this.mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
         f *= g;
      }

      if (this.mc.player.isSubmergedIn(FluidTags.WATER)) {
         boolean hasAquaAffinity = false;
         ItemStack helmet = this.mc.player.getEquippedStack(EquipmentSlot.HEAD);
         if (!helmet.isEmpty()) {
            ItemEnchantmentsComponent enchantments = helmet.getEnchantments();

            for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
               if (((RegistryEntry)entry.getKey()).matchesKey(Enchantments.AQUA_AFFINITY)) {
                  hasAquaAffinity = true;
                  break;
               }
            }
         }

         if (!hasAquaAffinity) {
            f /= 5.0F;
         }
      }

      if (!this.mc.player.isOnGround()) {
         f /= 5.0F;
      }

      return f;
   }

   private boolean canHarvest(BlockState state) {
      if (state.isToolRequired()) {
         int tool = this.getBestTool(state);
         return this.mc.player.getInventory().getStack(tool).isSuitableFor(state);
      } else {
         return true;
      }
   }

   private int getBestTool(BlockState state) {
      int bestSlot = -1;
      float bestSpeed = 0.0F;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         float speed = stack.getMiningSpeedMultiplier(state);
         if (speed > bestSpeed) {
            bestSpeed = speed;
            bestSlot = i;
         }
      }

      return bestSlot == -1 ? ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot() : bestSlot;
   }

   public boolean isMining() {
      return !this.miningQueue.isEmpty();
   }

   private void applyMineRotation(float[] rotations) {
      if ((Boolean)this.grimConfig.get()) {
         RotationUtils.getInstance().setRotationSilent(rotations[0], rotations[1]);
      } else {
         RotationUtils.getInstance().setRotationFull(rotations[0], rotations[1]);
      }
   }

   private static float[] getRotationsTo(Vec3d src, Vec3d dest) {
      float yaw = (float)(Math.toDegrees(Math.atan2(dest.subtract(src).z, dest.subtract(src).x)) - 90.0);
      float pitch = (float)Math.toDegrees(
         -Math.atan2(dest.subtract(src).y, Math.hypot(dest.subtract(src).x, dest.subtract(src).z))
      );
      return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch)};
   }

   private PlayerEntity getClosestEnemy() {
      if (this.mc.world != null && this.mc.player != null) {
         PlayerEntity closest = null;
         double closestDist = (Double)this.enemyRange.get() * (Double)this.enemyRange.get();

         for (PlayerEntity player : this.mc.world.getPlayers()) {
            if (player != this.mc.player && !player.isSpectator() && !player.isDead() && !Friends.get().isFriend(player)) {
               double dist = this.mc.player.squaredDistanceTo(player);
               if (dist < closestDist) {
                  closestDist = dist;
                  closest = player;
               }
            }
         }

         return closest;
      } else {
         return null;
      }
   }

   private BlockPos findBestEnemyBlock(PlayerEntity enemy) {
      if (enemy == null) {
         return null;
      }

      BlockPos enemyPos = enemy.getBlockPos();
      BlockState feetState = this.mc.world.getBlockState(enemyPos);
      if (!feetState.isAir() && feetState.getHardness(this.mc.world, enemyPos) != -1.0F) {
         double feetDist = this.mc.player.getEyePos().squaredDistanceTo(enemyPos.toCenterPos());
         if (feetDist <= (Double)this.rangeConfig.get() * (Double)this.rangeConfig.get()
            && !this.isMiningBlock(enemyPos)
            && this.isResistantBlock(feetState)
            && !this.isOwnSurroundBlock(enemyPos)) {
            return enemyPos;
         }
      }

      List<BlockPos> surroundBlocks = new ArrayList<>();
      surroundBlocks.add(enemyPos.north());
      surroundBlocks.add(enemyPos.south());
      surroundBlocks.add(enemyPos.east());
      surroundBlocks.add(enemyPos.west());
      BlockPos bestSurround = this.findBestBlock(surroundBlocks);
      if (bestSurround != null) {
         return bestSurround;
      }

      if ((Boolean)this.targetHead.get()) {
         BlockPos aboveHead = enemyPos.up(2);
         BlockState aboveState = this.mc.world.getBlockState(aboveHead);
         if (!aboveState.isAir() && aboveState.getHardness(this.mc.world, aboveHead) != -1.0F) {
            double dist = this.mc.player.getEyePos().squaredDistanceTo(aboveHead.toCenterPos());
            if (dist <= (Double)this.rangeConfig.get() * (Double)this.rangeConfig.get()
               && !this.isMiningBlock(aboveHead)
               && this.isResistantBlock(aboveState)
               && !this.isOwnSurroundBlock(aboveHead)) {
               return aboveHead;
            }
         }
      }

      return null;
   }

   private BlockPos findBestBlock(List<BlockPos> positions) {
      BlockPos bestBlock = null;
      double bestDist = (Double)this.rangeConfig.get() * (Double)this.rangeConfig.get();

      for (BlockPos pos : positions) {
         double dist = this.mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos());
         if (!(dist > (Double)this.rangeConfig.get() * (Double)this.rangeConfig.get())) {
            BlockState state = this.mc.world.getBlockState(pos);
            if (!state.isAir()
               && state.getHardness(this.mc.world, pos) != -1.0F
               && !this.isMiningBlock(pos)
               && !this.isOwnSurroundBlock(pos)
               && this.isResistantBlock(state)
               && dist < bestDist) {
               bestDist = dist;
               bestBlock = pos;
            }
         }
      }

      return bestBlock;
   }

   private BlockPos getAntiCrawlBlock() {
      if (this.mc.player != null && this.mc.world != null) {
         BlockPos playerPos = this.mc.player.getBlockPos();
         BlockPos blockAbove = playerPos.up();
         BlockState state = this.mc.world.getBlockState(blockAbove);
         if (!state.isAir()
            && state.getHardness(this.mc.world, blockAbove) != -1.0F
            && !state.isOf(Blocks.BEDROCK)
            && !state.isOf(Blocks.REINFORCED_DEEPSLATE)
            && !state.isOf(Blocks.BARRIER)) {
            double dist = this.mc.player.getEyePos().squaredDistanceTo(blockAbove.toCenterPos());
            if (dist <= (Double)this.rangeConfig.get() * (Double)this.rangeConfig.get()) {
               return blockAbove;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private boolean isMiningBlock(BlockPos pos) {
      if (this.miningQueue == null) {
         return false;
      }

      for (MlepMine.MiningData data : this.miningQueue) {
         if (data.getPos().equals(pos)) {
            return true;
         }
      }

      return false;
   }

   private boolean isOwnSurroundBlock(BlockPos pos) {
      BlockPos playerPos = this.mc.player.getBlockPos();
      return pos.equals(playerPos.north())
            || pos.equals(playerPos.south())
            || pos.equals(playerPos.east())
            || pos.equals(playerPos.west())
         ? true
         : pos.equals(playerPos.up()) || pos.equals(playerPos.up(2));
   }

   private boolean isResistantBlock(BlockState state) {
      return !state.isOf(Blocks.BEDROCK)
            && !state.isOf(Blocks.REINFORCED_DEEPSLATE)
            && !state.isOf(Blocks.BARRIER)
            && !state.isOf(Blocks.COMMAND_BLOCK)
            && !state.isOf(Blocks.STRUCTURE_BLOCK)
         ? state.isOf(Blocks.OBSIDIAN)
            || state.isOf(Blocks.CRYING_OBSIDIAN)
            || state.isOf(Blocks.ENDER_CHEST)
            || state.isOf(Blocks.ANCIENT_DEBRIS)
            || state.isOf(Blocks.RESPAWN_ANCHOR)
         : false;
   }

   /** Whether the block before this one could be taken in a single blow. */
   private boolean lastWasInstant;

   /** Set while a block is under the tool this tick, read once at the end of it. */
   private boolean workedThisTick;

   /**
    * Whether an arm animation has already gone out this tick.
    *
    * <p>Three places can send one -- the held button, a burst, the end of a bar --
    * and vanilla sends exactly one per tick while mining. Two is not twice as
    * ordinary, it is a signature: this is what keeps them to one.
    */
   private boolean swungThisTick;

   /** Swings unless something already did this tick. */
   private void swingOnce() {
      if (this.swungThisTick || this.mc.player == null) return;

      this.swungThisTick = true;
      this.mc.player.swingHand(Hand.MAIN_HAND);
   }

   /**
    * The steady beat of someone mining, for MINER.
    *
    * <p>Runs at the end of the tick event so it sees the whole of it, and takes
    * the latch as it goes. The click handler fires later in the same tick -- the
    * tick event is posted at the head of the client tick, input is read after it
    * -- so it is that one which has to stand down, not this one.
    */
   private void swingWhileWorking() {
      if (!this.workedThisTick) return;
      if (this.swing.get() != MlepMine.Swing.MINER) return;

      this.swingOnce();
   }

   public enum Swing {
      NONE,
      MINER,
      HAMMER
   }

   /**
    * The face to send with a dig, worked out now rather than remembered.
    *
    * <p>The face that came with the click is the face you were on when you clicked.
    * Move around the block while the bar fills -- which is what mining a wall does
    * -- and it stops matching where your eye is, and the server's own check that
    * the eye is on the right side of that face plane no longer passes. Recomputed
    * at the moment the packet leaves, it always does.
    */
   private Direction sendDirection(MlepMine.MiningData data) {
      if (this.mc.player == null) return data.getDirection();

      Direction now = this.getInteractDirection(data.getPos());

      return now == null ? data.getDirection() : now;
   }

   private Direction getInteractDirection(BlockPos pos) {
      Vec3d eyePos = this.mc.player.getEyePos();
      Vec3d posVec = Vec3d.ofCenter(pos);
      Direction bestDir = null;
      double bestDot = -1.0;

      for (Direction dir : Direction.values()) {
         Vec3d dirVec = Vec3d.of(dir.getVector());
         double dot = eyePos.subtract(posVec).normalize().dotProduct(dirVec);
         if (dot > bestDot) {
            bestDot = dot;
            bestDir = dir;
         }
      }

      return bestDir;
   }

   public BlockPos getLastAutoMineBlock() {
      return this.lastAutoMineBlock;
   }

   public BlockPos getLastAntiCrawlBlock() {
      return this.lastAntiCrawlBlock;
   }

   private static class Animation {
      private boolean state;
      private long time;
      private final long duration;

      public Animation(boolean state, long duration) {
         this.state = state;
         this.duration = duration;
         this.time = System.currentTimeMillis();
      }

      public void setState(boolean state) {
         if (this.state != state) {
            this.state = state;
            this.time = System.currentTimeMillis();
         }
      }

      /** When this entry appeared, or last changed state: the breath is timed from here. */
      public long since() {
         return this.time;
      }

      public float getFactor() {
         if (this.state) {
            return 1.0F;
         }

         long elapsed = System.currentTimeMillis() - this.time;
         float progress = Math.min(1.0F, (float)elapsed / (float)this.duration);
         return 1.0F - progress;
      }
   }

   private class FirstOutQueue<T> extends ArrayList<T> {
      private int maxSize;

      public FirstOutQueue(int maxSize) {
         this.maxSize = maxSize;
      }

      public void setMaxSize(int maxSize) {
         this.maxSize = maxSize;

         // Shrinking on the fly: send the abort and stop following the extra
         // blocks. The server keeps its own open break position either way, so
         // this tidies the client side, nothing more.
         while (this.size() > maxSize) {
            T evicted = this.remove(this.size() - 1);
            if (evicted instanceof MlepMine.MiningData data) {
               MlepMine.this.abortMining(data);
            }
         }
      }

      @Override
      public void addFirst(T element) {
         while (this.size() >= this.maxSize) {
            T evicted = this.remove(this.size() - 1);
            if (evicted instanceof MlepMine.MiningData data) {
               MlepMine.this.abortMining(data);
            }
         }

         this.add(0, element);
      }

      @Override
      public T getFirst() {
         return this.isEmpty() ? null : this.get(0);
      }

      @Override
      public T getLast() {
         return this.isEmpty() ? null : this.get(this.size() - 1);
      }
   }

   public class MiningData {
      private boolean attemptedBreak;
      private long breakTime;
      private long airSince;
      private boolean rehold;
      private final BlockPos pos;
      private final Direction direction;
      private float lastDamage;
      private float blockDamage;
      private boolean started;

      public MiningData(BlockPos pos, Direction direction) {
         this.pos = pos;
         this.direction = direction;
      }

      public void setAttemptedBreak(boolean attemptedBreak) {
         this.attemptedBreak = attemptedBreak;
         if (attemptedBreak) {
            this.resetBreakTime();
         }
      }

      public void resetBreakTime() {
         this.breakTime = System.currentTimeMillis();
      }

      /** First moment this spot was seen empty. Zero while it still holds a block. */
      public void markAir(long now) {
         if (this.airSince == 0L) {
            this.airSince = now;
            this.rehold = true;
         }
      }

      public void markSolid() {
         this.airSince = 0L;
      }

      /** True once per clear-and-refill, for the caller that has to take the tool back. */
      public boolean consumeRehold() {
         boolean pending = this.rehold;
         this.rehold = false;
         return pending;
      }

      public boolean airedOut(long now, long window) {
         return this.airSince != 0L && now - this.airSince >= window;
      }

      public boolean hasAttemptedBreak() {
         return this.attemptedBreak;
      }

      public boolean passedAttemptedBreakTime(long time) {
         return System.currentTimeMillis() - this.breakTime >= time;
      }

      public float damage(float dmg) {
         this.lastDamage = this.blockDamage;
         this.blockDamage += dmg;
         return this.blockDamage;
      }

      public void setDamage(float blockDamage) {
         this.blockDamage = blockDamage;
      }

      public void resetDamage() {
         this.started = false;
         this.blockDamage = 0.0F;
      }

      public BlockPos getPos() {
         return this.pos;
      }

      public Direction getDirection() {
         return this.direction;
      }

      public int getSlot() {
         return this.getBestToolNoFallback(this.getState());
      }

      public BlockState getState() {
         return MlepMine.this.mc.world.getBlockState(this.pos);
      }

      public float getBlockDamage() {
         return this.blockDamage;
      }

      public float getLastDamage() {
         return this.lastDamage;
      }

      public boolean isStarted() {
         return this.started;
      }

      public void setStarted() {
         this.started = true;
      }

      private int getBestToolNoFallback(BlockState state) {
         int bestSlot = -1;
         float bestSpeed = 0.0F;

         for (int i = 0; i < 9; i++) {
            ItemStack stack = MlepMine.this.mc.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
               bestSpeed = speed;
               bestSlot = i;
            }
         }

         return bestSlot;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            MlepMine.MiningData that = (MlepMine.MiningData)o;
            return this.pos.equals(that.pos);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return this.pos.hashCode();
      }
   }

   public enum RenderStyle {
      CLASSIC,
      FILL,
      PULSE;
   }

   public enum SpeedmineMode {
      PACKET,
      DAMAGE;
   }

   public enum Swap {
      NORMAL,
      SILENT,
      OFF;
   }
}
