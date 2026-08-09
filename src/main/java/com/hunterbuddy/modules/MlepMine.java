package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.modules.regear.util.InventoryManager;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
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
                  .description("Instantly mines already broken blocks")
               .defaultValue(false)
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
                                 "\u00a77[\u00a7bMlepMine\u00a77] \u00a7aPersistent mode enabled! Module cannot be disabled until you turn this off or disconnect."
                              ),
                              false
                           );
                     }
                  }
               ).build()
      );
   private final Setting<MlepMine.Swap> swapConfig = this.sgGeneral
      .add(
         new Builder<MlepMine.Swap>().name("auto-swap").description("Swaps to the best tool once the mining is complete")
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
   private long lastBreak;
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
   private int swapBackTicks = 0;
   private InventoryManager inventoryManager;

   public MlepMine() {
      super(HunterBuddyAddon.UTILITY_CATEGORY, "mlep-mine", "Mines blocks faster");
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
                     "\u00a77[\u00a7bMlepMine\u00a77] \u00a7cCannot disable while Persistent mode is active! Disable Persistent first or disconnect from server."
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
      this.swapBackTicks = 0;
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
         this.fadeList.clear();
         if (this.swapConfig.get() == MlepMine.Swap.SILENT && this.inventoryManager != null) {
            this.inventoryManager.syncToClient();
         }

         this.swappedToSlot = -1;
         this.originalSlot = -1;
         this.swapBackTicks = 0;
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
      this.fadeList.clear();
      this.swappedToSlot = -1;
      this.originalSlot = -1;
      this.swapBackTicks = 0;
      this.lastAutoMineBlock = null;
      this.lastAutoMineTime = 0L;
      this.lastAntiCrawlBlock = null;
      this.lastAntiCrawlTime = 0L;
      this.currentTarget = null;
   }

   @EventHandler
   public void onPlayerTick(Pre event) {
      if (!this.mc.player.isCreative() && !this.mc.player.isSpectator()) {
         if (this.swapBackTicks > 0) {
            this.swapBackTicks--;
            if (this.swapBackTicks == 0 && this.swappedToSlot != -1 && this.originalSlot != -1) {
               this.swapBack(this.originalSlot);
               this.swappedToSlot = -1;
               this.originalSlot = -1;
            }
         }

         if (!((Keybind)this.autoMineKey.get()).isPressed() || this.mc.currentScreen != null) {
            this.autoMineTogglePressed = false;
         } else if (!this.autoMineTogglePressed) {
            this.autoMineTogglePressed = true;
            this.autoMine.set(!(Boolean)this.autoMine.get());
            if (this.mc.player != null) {
               String status = this.autoMine.get() ? "\u00a7aenabled" : "\u00a7cdisabled";
               this.mc.player.sendMessage(Text.literal("\u00a77[\u00a7bMlepMine\u00a77] \u00a7fAuto-mine " + status), false);
            }
         }

         if (!((Keybind)this.queueClearKey.get()).isPressed() || this.mc.currentScreen != null) {
            this.queueClearPressed = false;
         } else if (!this.queueClearPressed) {
            this.queueClearPressed = true;
            int dropped = this.pendingQueue.size();
            this.pendingQueue.clear();
            if (this.mc.player != null) {
               this.mc.player
                  .sendMessage(Text.literal("\u00a77[\u00a7bMlepMine\u00a77] \u00a7fQueue cleared (" + dropped + " block(s))"), false);
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
               this.mc.player.sendMessage(Text.literal("\u00a77[\u00a7bMlepMine\u00a77] \u00a7fInstant mining " + status), false);
            }
         }

         if (this.modeConfig.get() != MlepMine.SpeedmineMode.DAMAGE) {
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

               for (MlepMine.MiningData data : this.miningQueue) {
                  if (data.getState().isAir()) {
                     data.resetBreakTime();
                  }

                  if (!this.isDataPacketMine(data) || !data.getState().isAir() && (!data.hasAttemptedBreak() || !data.passedAttemptedBreakTime(500L))) {
                     float damageDelta = this.calcBlockBreakingDelta(data.getState(), this.mc.world, data.getPos());
                     data.damage(damageDelta);
                     if (this.isDataPacketMine(data) && data.getBlockDamage() >= 1.0F && data.getSlot() != -1) {
                        if (this.mc.player.isUsingItem() && !(Boolean)this.multitaskConfig.get()) {
                           return;
                        }

                        if (!data.hasAttemptedBreak()) {
                           data.setAttemptedBreak(true);
                        }
                     }
                  } else {
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
         }
      }
   }

   @EventHandler
   public void onAttackBlock(StartBreakingBlockEvent event) {
      if (!this.mc.player.isCreative() && !this.mc.player.isSpectator() && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET) {
         event.cancel();
         BlockState blockState = this.mc.world.getBlockState(event.blockPos);
         if (blockState.getHardness(this.mc.world, event.blockPos) != -1.0F && !blockState.isAir()) {
            this.startManualMine(event.blockPos, event.direction);
            this.mc.player.swingHand(Hand.MAIN_HAND);
         }
      }
   }

   @EventHandler
   public void onPacketOutbound(Send event) {
      if (event.packet instanceof PlayerActionC2SPacket packet
         && packet.getAction() == Action.STOP_DESTROY_BLOCK
         && this.modeConfig.get() == MlepMine.SpeedmineMode.DAMAGE
         && (Boolean)this.grimConfig.get()) {
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, packet.getPos(), packet.getDirection()));
      }

      if (event.packet instanceof UpdateSelectedSlotC2SPacket
         && (Boolean)this.switchResetConfig.get()
         && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET
         && this.swapBackTicks <= 0
         && this.swappedToSlot == -1) {
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

   private void handleBlockUpdatePacket(BlockUpdateS2CPacket packet) {
      if (packet.getState().isAir()) {
         for (MlepMine.MiningData data : this.miningQueue) {
            if (data.hasAttemptedBreak() && data.getPos().equals(packet.getPos())) {
               data.setAttemptedBreak(false);
            }
         }
      }
   }

   @EventHandler
   public void onRenderWorld(Render3DEvent event) {
      if (!this.mc.player.isCreative() && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET && (Boolean)this.render.get()) {
         // Waiting blocks, drawn small so they read as "later" next to the block
         // actually being mined. A box vanishing here is also the only feedback a
         // dropped block gets — nothing is printed for those.
         if ((Boolean)this.queueConfig.get() && !this.pendingQueue.isEmpty()) {
            SettingColor pending = (SettingColor)this.queueColor.get();

            for (MlepMine.MiningData waiting : this.pendingQueue) {
               BlockPos pos = waiting.getPos();
               event.renderer
                  .box(
                     pos.getX() + 0.25,
                     pos.getY() + 0.25,
                     pos.getZ() + 0.25,
                     pos.getX() + 0.75,
                     pos.getY() + 0.75,
                     pos.getZ() + 0.75,
                     pending,
                     pending,
                     (ShapeMode)this.shapeMode.get(),
                     0
                  );
            }
         }

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
            MlepMine.MiningData data = set.getKey();
            int boxAlpha = (int)(40.0F * set.getValue().getFactor());
            int lineAlpha = (int)(100.0F * set.getValue().getFactor());
            int boxColor = !(data.getBlockDamage() >= 0.95F) && !data.getState().isAir()
               ? ((SettingColor)this.colorConfig.get()).getPacked()
               : ((SettingColor)this.colorDoneConfig.get()).getPacked();
            int lineColor = !(data.getBlockDamage() >= 0.95F) && !data.getState().isAir()
               ? ((SettingColor)this.colorConfig.get()).getPacked()
               : ((SettingColor)this.colorDoneConfig.get()).getPacked();
            boxColor = boxColor & 16777215 | boxAlpha << 24;
            lineColor = lineColor & 16777215 | lineAlpha << 24;
            BlockPos mining = data.getPos();
            VoxelShape outlineShape = data.getState().getOutlineShape(this.mc.world, mining);
            outlineShape = outlineShape.isEmpty() ? VoxelShapes.fullCube() : outlineShape;
            Box render1 = outlineShape.getBoundingBox();
            Box render = new Box(
               mining.getX() + render1.minX,
               mining.getY() + render1.minY,
               mining.getZ() + render1.minZ,
               mining.getX() + render1.maxX,
               mining.getY() + render1.maxY,
               mining.getZ() + render1.maxZ
            );
            Vec3d center = render.getCenter();
            float total = this.isDataPacketMine(data) ? 1.0F : ((Double)this.speedConfig.get()).floatValue();
            float scale = data.getState().isAir()
               ? 1.0F
               : MathHelper.clamp((data.getBlockDamage() + (data.getBlockDamage() - data.getLastDamage()) * event.tickDelta) / total, 0.0F, 1.0F);
            double dx = (render1.maxX - render1.minX) / 2.0;
            double dy = (render1.maxY - render1.minY) / 2.0;
            double dz = (render1.maxZ - render1.minZ) / 2.0;
            Box scaled = new Box(center, center).expand(dx * scale, dy * scale, dz * scale);
            event.renderer
               .box(
                  scaled.minX,
                  scaled.minY,
                  scaled.minZ,
                  scaled.maxX,
                  scaled.maxY,
                  scaled.maxZ,
                  new SettingColor(boxColor),
                  new SettingColor(lineColor),
                  (ShapeMode)this.shapeMode.get(),
                  0
               );
         }

         this.fadeList.entrySet().removeIf(e -> e.getValue().getFactor() == 0.0);
      }
   }

   private void startManualMine(BlockPos pos, Direction direction) {
      this.clickMine(new MlepMine.MiningData(pos, direction));
   }

   private MlepMine.FirstOutQueue<MlepMine.MiningData> ensureMiningQueue() {
      if (this.miningQueue == null) {
         this.miningQueue = new MlepMine.FirstOutQueue<>((Boolean)this.doubleBreakConfig.get() ? 2 : 1);
      }

      return this.miningQueue;
   }

   public void clickMine(MlepMine.MiningData miningData) {
      // With the queue on, a click that arrives while something is still solid
      // goes to the backlog instead of evicting it. Clicking a block already
      // waiting takes it back out — the same gesture serves as undo.
      if ((Boolean)this.queueConfig.get() && this.modeConfig.get() == MlepMine.SpeedmineMode.PACKET) {
         if (this.pendingQueue.removeIf(d -> d.getPos().equals(miningData.getPos()))) {
            return;
         }

         if (this.hasSolidMiningEntry()) {
            if (this.isMiningBlock(miningData.getPos())) return;
            if (miningData.getState().isAir()) return;
            if (this.pendingQueue.size() >= PENDING_QUEUE_LIMIT) return;

            this.pendingQueue.add(miningData);
            return;
         }
      }

      int maxQueueSize = (Boolean) this.doubleBreakConfig.get() ? 2 : 1;
      if (this.ensureMiningQueue().size() <= maxQueueSize) {
         this.queueMiningData(miningData);
      }
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
      if (!(Boolean)this.queueConfig.get() || this.pendingQueue.isEmpty()) return;
      if (this.modeConfig.get() != MlepMine.SpeedmineMode.PACKET) return;
      if (this.mc.player == null || this.mc.world == null) return;
      if (this.mc.player.isUsingItem() && !(Boolean)this.multitaskConfig.get()) return;
      if (this.hasSolidMiningEntry() || this.isBlockDelayGrim()) return;

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
      float breakDelta = this.calcBlockBreakingDelta(data.getState(), this.mc.world, data.getPos());
      boolean isInstantBreak = breakDelta >= 1.0F;
      if ((Boolean)this.grimNewConfig.get()) {
         if (!(Boolean)this.miningFix.get()) {
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, data.getPos(), data.getDirection()));
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, data.getPos(), data.getDirection()));
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, data.getPos(), data.getDirection()));
         } else {
            this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, data.getPos(), data.getDirection()));
         }

         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, data.getPos(), data.getDirection()));
         this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
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

         int bestSlot = data.getSlot();
         int currentSlot = ((PlayerInventoryAccessor)this.mc.player.getInventory()).getSelectedSlot();
         boolean needsSwap = bestSlot != -1 && bestSlot != currentSlot;
         if (needsSwap && this.swappedToSlot == -1) {
            this.originalSlot = currentSlot;
         }

         if (needsSwap) {
            this.swapTo(bestSlot);
            this.swappedToSlot = bestSlot;
            this.swapBackTicks = 3;
         } else if (this.swappedToSlot != -1) {
            this.swapBackTicks = 3;
         }

         this.stopMiningInternal(data);
         this.lastBreak = System.currentTimeMillis();
      }
   }

   private void swapTo(int slot) {
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
   }

   private void swapBack(int originalSlot) {
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
   }

   private void stopMiningInternal(MlepMine.MiningData data) {
      this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, data.getPos(), data.getDirection()));
      this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, data.getPos(), data.getDirection()));
   }

   public boolean isBlockDelayGrim() {
      return System.currentTimeMillis() - this.lastBreak <= 280L && (Boolean)this.grimConfig.get();
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
      private final int maxSize;

      public FirstOutQueue(int maxSize) {
         this.maxSize = maxSize;
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
