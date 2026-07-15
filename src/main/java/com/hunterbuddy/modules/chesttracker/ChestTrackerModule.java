package com.hunterbuddy.modules.chesttracker;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.KeybindSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.HopperBlock;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.block.BarrelBlock;
import net.minecraft.util.hit.BlockHitResult;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3d;

public class ChestTrackerModule extends Module {
   private static boolean blockInteractionCallbackRegistered = false;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAutoOpen = this.settings.createGroup("Auto-Open");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final SettingGroup sgLabels = this.settings.createGroup("Labels");
   private final SettingGroup sgFilter = this.settings.createGroup("Filter");
   private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced");
   private final Setting<Keybind> browserKey = this.sgGeneral
      .add(
         new Builder().name("browser-keybind").description("Open container browser GUI.").defaultValue(Keybind.fromKey(89))
            .action(() -> {
               if (this.mc.currentScreen == null) {
                  this.mc.setScreen(new ChestTrackerScreen(this));
               }
            })
            .build()
      );
   private final Setting<Boolean> autoOpenEnabled = this.sgAutoOpen
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-open")
                  .description("Automatically open nearby containers.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Double> autoOpenRange = this.sgAutoOpen
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("auto-open-range")
                  .description("Range to search for containers to auto-open.")
               .defaultValue(4.0)
               .min(1.0)
               .max(6.0)
               .sliderRange(1.0, 6.0)
               .decimalPlaces(1)
               .visible(this.autoOpenEnabled::get)
            .build()
      );
   private final Setting<Integer> autoOpenDelay = this.sgAutoOpen
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("auto-open-delay")
                     .description("Delay in ticks between opening containers.")
                  .defaultValue(5)
               .min(1)
               .max(20)
               .sliderRange(1, 20)
               .visible(this.autoOpenEnabled::get)
            .build()
      );
   private final Setting<Integer> autoOpenCloseDelay = this.sgAutoOpen
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("auto-close-delay")
                     .description("Delay in ticks before closing container after opening (allows server to send all contents).")
                  .defaultValue(5)
               .min(0)
               .max(40)
               .sliderRange(0, 40)
               .visible(this.autoOpenEnabled::get)
            .build()
      );
   private final Setting<Boolean> renderTracked = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render-tracked")
                  .description("Render all tracked containers.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> renderSearchResults = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render-search-results")
                  .description("Render search results.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Integer> renderDistance = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("render-distance")
                     .description("Maximum render distance.")
                  .defaultValue(128)
               .min(8)
               .max(2048)
               .sliderRange(8, 2048)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
                     .name("shape-mode")
                  .description("Render shape mode.")
               .defaultValue(ShapeMode.Both)
            .build()
      );
   private final Setting<SettingColor> trackedColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("tracked-color")
               .description("Color for tracked containers.")
            .defaultValue(new SettingColor(255, 255, 0, 75)).build()
      );
   private final Setting<SettingColor> searchColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("search-color")
               .description("Color for search results.")
            .defaultValue(new SettingColor(0, 255, 0, 100)).build()
      );
   private final Setting<SettingColor> searchLineColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("search-line-color")
               .description("Line color for search results.")
            .defaultValue(new SettingColor(0, 255, 0, 255)).build()
      );
   private final Setting<Boolean> renderLabels = this.sgLabels
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render-labels")
                  .description("Render item icons on containers.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Double> labelScale = this.sgLabels
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("icon-scale")
                  .description("Item icon scale.")
               .defaultValue(1.0)
               .min(0.5)
               .max(3.0)
               .sliderRange(0.5, 3.0)
            .build()
      );
   private final Setting<Integer> labelMaxDistance = this.sgLabels
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("icon-max-distance")
                     .description("Max distance for item icons.")
                  .defaultValue(64)
               .min(8)
               .max(512)
               .sliderRange(8, 512)
            .build()
      );
   private final Setting<Boolean> trackChests = this.sgFilter
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("track-chests")
                  .description("Track chests and trapped chests.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> trackBarrels = this.sgFilter
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("track-barrels")
                  .description("Track barrels.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> trackShulkers = this.sgFilter
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("track-shulkers")
                  .description("Track shulker boxes.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> trackEnderChests = this.sgFilter
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("track-ender-chests")
                  .description("Track ender chests.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> trackHoppers = this.sgFilter
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("track-hoppers")
                  .description("Track hoppers.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> trackDispensers = this.sgFilter
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("track-dispensers")
                  .description("Track dispensers and droppers.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> trackCopperChests = this.sgFilter
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("track-copper-chests")
                  .description("Track copper chests (for modded servers).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> debugMode = this.sgAdvanced
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("debug")
                  .description("Show debug messages in chat.")
               .defaultValue(false)
            .build()
      );
   private Item currentSearchItem;
   private BlockPos lastInteractedBlock = null;
   private boolean awaiting = false;
   private int awaitingTicks = 0;
   private int tickCounter = 0;
   private BlockPos[] currentOpenPositions = new BlockPos[2];
   private List<TrackedContainer> renderCache = new ArrayList<>();
   private long lastRenderCacheUpdate = 0L;
   private boolean shouldAutoClose = false;
   private int ticksUntilClose = 0;
   private static final int AWAITING_TIMEOUT = 40;
   private final Map<BlockPos, Integer> blockedContainers = new HashMap<>();
   private static final int BLOCKED_COOLDOWN_TICKS = 100;
   private static final int MAX_FAILED_ATTEMPTS = 3;
   private boolean wasInContainerScreen = false;
   private ScreenHandler lastScreenHandler = null;
   private BlockPos[] containerPositionsForClose = new BlockPos[2];

   public ChestTrackerModule() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "chest-tracker", "Track items in containers.");
   }

   private ChestTrackerDataV2 getData() {
      return ChestTrackerDataManager.getData();
   }

   public ChestTrackerDataV2 getSharedData() {
      return ChestTrackerDataManager.getData();
   }

   public void onActivate() {
      ChestTrackerDataManager.onModuleActivate();
      ChestTrackerDataV2 data = this.getData();
      if (data != null) {
         int count = data.getTotalContainerCount();
         if (count > 0 && (Boolean)this.debugMode.get()) {
            this.info("Loaded " + count + " containers", new Object[0]);
         }
      }

      this.resetState();
      if (!blockInteractionCallbackRegistered) {
         this.setupBlockInteractionTracking();
         blockInteractionCallbackRegistered = true;
      }
   }

   public void onDeactivate() {
      ChestTrackerDataManager.onModuleDeactivate();
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      ChestTrackerDataManager.onWorldJoin();
      this.renderCache.clear();
      this.lastRenderCacheUpdate = 0L;
      if ((Boolean)this.debugMode.get()) {
         ChestTrackerDataV2 data = this.getData();
         if (data != null) {
            this.info("Reloaded " + data.getTotalContainerCount() + " containers for this server", new Object[0]);
         }
      }
   }

   private void resetState() {
      this.lastInteractedBlock = null;
      this.currentSearchItem = null;
      this.awaiting = false;
      this.awaitingTicks = 0;
      this.tickCounter = 0;
      this.currentOpenPositions = new BlockPos[2];
      this.renderCache.clear();
      this.shouldAutoClose = false;
      this.ticksUntilClose = 0;
      this.blockedContainers.clear();
      this.wasInContainerScreen = false;
      this.lastScreenHandler = null;
      this.containerPositionsForClose = new BlockPos[2];
   }

   private void setupBlockInteractionTracking() {
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
         if (!this.isActive()) {
            return ActionResult.PASS;
         }

         if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
         }

         if (this.mc.player != player) {
            return ActionResult.PASS;
         }

         BlockPos pos = hitResult.getBlockPos();
         if (this.mc.world == null) {
            return ActionResult.PASS;
         }

         Block block = this.mc.world.getBlockState(pos).getBlock();
         if (this.isTrackableContainer(block)) {
            this.lastInteractedBlock = pos.toImmutable();
         }

         return ActionResult.PASS;
      });
   }

   @EventHandler
   private void onTick(Pre event) {
      if (!this.blockedContainers.isEmpty()) {
         Iterator<Entry<BlockPos, Integer>> it = this.blockedContainers.entrySet().iterator();

         while (it.hasNext()) {
            Entry<BlockPos, Integer> entry = it.next();
            int ticksRemaining = entry.getValue() - 1;
            if (ticksRemaining <= 0) {
               it.remove();
               if ((Boolean)this.debugMode.get()) {
                  this.info("Removed " + entry.getKey().toShortString() + " from blocked list (cooldown expired)", new Object[0]);
               }
            } else {
               entry.setValue(ticksRemaining);
            }
         }
      }

      boolean currentlyInContainer = this.isInContainerScreen();
      if (this.wasInContainerScreen && !currentlyInContainer) {
         this.trackContainerOnClose();
      }

      if (currentlyInContainer && this.mc.player != null) {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (handler != null && handler != this.mc.player.playerScreenHandler) {
            this.lastScreenHandler = handler;
            if (this.containerPositionsForClose[0] == null) {
               if (this.currentOpenPositions[0] != null) {
                  this.containerPositionsForClose[0] = this.currentOpenPositions[0];
                  this.containerPositionsForClose[1] = this.currentOpenPositions[1];
               } else if (this.lastInteractedBlock != null) {
                  this.containerPositionsForClose[0] = this.lastInteractedBlock;
                  this.containerPositionsForClose[1] = this.findDoubleChestOtherHalf(this.lastInteractedBlock);
               }
            }
         }
      }

      this.wasInContainerScreen = currentlyInContainer;
      if (this.shouldAutoClose) {
         if (!this.isInContainerScreen()) {
            this.shouldAutoClose = false;
            this.ticksUntilClose = 0;
            if ((Boolean)this.debugMode.get()) {
               this.info("Container closed manually, cancelling auto-close", new Object[0]);
            }
         } else if (this.ticksUntilClose > 0) {
            this.ticksUntilClose--;
            if (this.ticksUntilClose % 5 == 0 && (Boolean)this.debugMode.get()) {
               this.info("Auto-close countdown: " + this.ticksUntilClose + " ticks remaining", new Object[0]);
            }

            if (this.ticksUntilClose == 0) {
               this.shouldAutoClose = false;
               if (this.mc.player != null) {
                  this.mc.player.closeHandledScreen();
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Auto-closed container after " + this.autoOpenCloseDelay.get() + " tick delay", new Object[0]);
                  }
               }
            }
         }
      }

      if (this.awaiting) {
         if (this.isInContainerScreen()) {
            this.awaitingTicks++;
            if (this.awaitingTicks > 5) {
               if ((Boolean)this.debugMode.get()) {
                  this.info("InventoryEvent didn't fire - manually processing container", new Object[0]);
               }

               ScreenHandler handler = this.mc.player.currentScreenHandler;
               if (handler != null && this.currentOpenPositions[0] != null) {
                  BlockPos trackPos = this.currentOpenPositions[0];
                  this.awaiting = false;
                  this.awaitingTicks = 0;
                  this.blockedContainers.remove(trackPos);
                  if (this.currentOpenPositions[1] != null) {
                     this.blockedContainers.remove(this.currentOpenPositions[1]);
                  }

                  List<ItemStack> items = new ArrayList<>();
                  int containerSlots = handler.slots.size() - 36;

                  for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
                     Slot slot = (Slot)handler.slots.get(i);
                     ItemStack stack = slot.getStack();
                     if (!stack.isEmpty()) {
                        items.add(stack.copy());
                     }
                  }

                  String currentDim = this.getCurrentDimension();
                  String containerType = this.getContainerType(trackPos);
                  this.getData().trackContainer(trackPos, currentDim, containerType, items);
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Manually tracked " + containerType + " (" + items.size() + " items)", new Object[0]);
                  }

                  this.containerPositionsForClose[0] = trackPos;
                  this.containerPositionsForClose[1] = this.currentOpenPositions[1];
                  this.lastScreenHandler = handler;
                  int closeDelay = (Integer)this.autoOpenCloseDelay.get();
                  if (closeDelay == 0) {
                     this.mc.player.closeHandledScreen();
                     if ((Boolean)this.debugMode.get()) {
                        this.info("Closed immediately (0 tick delay)", new Object[0]);
                     }
                  } else {
                     this.shouldAutoClose = true;
                     this.ticksUntilClose = closeDelay;
                     if ((Boolean)this.debugMode.get()) {
                        this.info("Set shouldAutoClose=true, ticksUntilClose=" + closeDelay, new Object[0]);
                     }
                  }

                  this.currentOpenPositions = new BlockPos[2];
               } else {
                  this.awaiting = false;
                  this.awaitingTicks = 0;
                  this.currentOpenPositions = new BlockPos[2];
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Reset awaiting flag (can't process inventory)", new Object[0]);
                  }
               }
            }
         } else {
            this.awaitingTicks++;
            if (this.awaitingTicks > 40) {
               if (this.currentOpenPositions[0] != null) {
                  this.blockedContainers.put(this.currentOpenPositions[0], 100);
                  if ((Boolean)this.debugMode.get()) {
                     this.info(
                        "Container at " + this.currentOpenPositions[0].toShortString() + " failed to open (timeout), adding to blocked list", new Object[0]
                     );
                  }
               }

               this.awaiting = false;
               this.awaitingTicks = 0;
               this.currentOpenPositions = new BlockPos[2];
               if ((Boolean)this.debugMode.get()) {
                  this.info("Reset awaiting flag (timeout - container never opened or closed without inventory event)", new Object[0]);
               }
            }
         }
      }

      if (!this.isInContainerScreen()) {
         if ((Boolean)this.autoOpenEnabled.get()) {
            if (!this.awaiting) {
               if (this.tickCounter < (Integer)this.autoOpenDelay.get()) {
                  this.tickCounter++;
               } else {
                  this.tickCounter = 0;
                  int range = (int)Math.ceil((Double)this.autoOpenRange.get());
                  BlockPos playerPos = this.mc.player.getBlockPos();
                  String currentDim = this.getCurrentDimension();

                  for (int x = -range; x <= range; x++) {
                     for (int y = -range; y <= range; y++) {
                        for (int z = -range; z <= range; z++) {
                           BlockPos blockPos = playerPos.add(x, y, z);
                           double distSq = this.mc
                              .player
                              .squaredDistanceTo(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                           double maxDistSq = (Double)this.autoOpenRange.get() * (Double)this.autoOpenRange.get();
                           if (!(distSq > maxDistSq)) {
                              BlockState blockState = this.mc.world.getBlockState(blockPos);
                              Block block = blockState.getBlock();
                              if (this.isTrackableContainer(block)) {
                                 boolean isAlreadyTracked = this.getData().getContainer(blockPos, currentDim) != null;
                                 if (!isAlreadyTracked && block instanceof ChestBlock) {
                                    ChestType chestType = (ChestType)blockState.get(ChestBlock.CHEST_TYPE);
                                    if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                                       Direction facing = (Direction)blockState.get(ChestBlock.FACING);
                                       BlockPos otherHalf = blockPos.offset(
                                          chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise()
                                       );
                                       if (this.getData().getContainer(otherHalf, currentDim) != null) {
                                          isAlreadyTracked = true;
                                       }
                                    }
                                 }

                                 if (!this.blockedContainers.containsKey(blockPos) && !isAlreadyTracked) {
                                    Vec3d vec = new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                                    BlockHitResult hitResult = new BlockHitResult(vec, Direction.UP, blockPos, false);
                                    ActionResult result = this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
                                    if (result == ActionResult.SUCCESS || result == ActionResult.CONSUME) {
                                       this.blockedContainers.remove(blockPos);
                                       this.awaiting = true;
                                       this.awaitingTicks = 0;
                                       this.currentOpenPositions[0] = blockPos.toImmutable();
                                       this.currentOpenPositions[1] = null;
                                       if (block instanceof ChestBlock) {
                                          ChestType chestType = (ChestType)blockState.get(ChestBlock.CHEST_TYPE);
                                          if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                                             Direction facing = (Direction)blockState.get(ChestBlock.FACING);
                                             BlockPos otherPos = blockPos.offset(
                                                chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise()
                                             );
                                             this.currentOpenPositions[1] = otherPos;
                                          }
                                       }

                                       this.mc.player.swingHand(Hand.MAIN_HAND);
                                       if ((Boolean)this.debugMode.get()) {
                                          this.info("Auto-opening container at " + blockPos.toShortString(), new Object[0]);
                                       }

                                       return;
                                    }

                                    if (result == ActionResult.FAIL) {
                                       this.blockedContainers.put(blockPos.toImmutable(), 100);
                                       if ((Boolean)this.debugMode.get()) {
                                          this.info("Container at " + blockPos.toShortString() + " is blocked, adding to cooldown list", new Object[0]);
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
      }
   }

   @EventHandler
   private void onInventory(InventoryEvent event) {
      if (this.isActive()) {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (handler != null) {
            BlockPos trackPos = this.currentOpenPositions[0];
            if (trackPos == null) {
               trackPos = this.lastInteractedBlock;
            }

            if (trackPos == null) {
               this.awaiting = false;
               this.awaitingTicks = 0;
            } else {
               boolean wasAutoOpened = this.awaiting;
               this.awaiting = false;
               this.awaitingTicks = 0;
               this.blockedContainers.remove(trackPos);
               if (this.currentOpenPositions[1] != null) {
                  this.blockedContainers.remove(this.currentOpenPositions[1]);
               }

               if ((Boolean)this.debugMode.get()) {
                  this.info("Inventory event fired - wasAutoOpened: " + wasAutoOpened, new Object[0]);
               }

               List<ItemStack> items = new ArrayList<>();
               int containerSlots = handler.slots.size() - 36;

               for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
                  Slot slot = (Slot)handler.slots.get(i);
                  ItemStack stack = slot.getStack();
                  if (!stack.isEmpty()) {
                     items.add(stack.copy());
                  }
               }

               String currentDim = this.getCurrentDimension();
               String containerType = this.getContainerType(trackPos);
               this.getData().trackContainer(trackPos, currentDim, containerType, items);
               if ((Boolean)this.debugMode.get()) {
                  this.info("Tracked " + containerType + " (" + items.size() + " items)", new Object[0]);
               }

               if (wasAutoOpened) {
                  int closeDelay = (Integer)this.autoOpenCloseDelay.get();
                  if ((Boolean)this.debugMode.get()) {
                     this.info("Scheduling auto-close with delay: " + closeDelay + " ticks", new Object[0]);
                  }

                  if (closeDelay == 0) {
                     this.mc.player.closeHandledScreen();
                     if ((Boolean)this.debugMode.get()) {
                        this.info("Closed immediately (0 tick delay)", new Object[0]);
                     }
                  } else {
                     this.shouldAutoClose = true;
                     this.ticksUntilClose = closeDelay;
                     if ((Boolean)this.debugMode.get()) {
                        this.info("Set shouldAutoClose=true, ticksUntilClose=" + closeDelay, new Object[0]);
                     }
                  }
               }

               this.containerPositionsForClose[0] = trackPos;
               this.containerPositionsForClose[1] = this.findDoubleChestOtherHalf(trackPos);
               this.lastScreenHandler = handler;
               this.lastInteractedBlock = null;
               this.currentOpenPositions = new BlockPos[2];
            }
         }
      }
   }

   private void trackContainerOnClose() {
      if (this.lastScreenHandler == null || this.containerPositionsForClose[0] == null) {
         this.lastScreenHandler = null;
         this.containerPositionsForClose = new BlockPos[2];
      } else if (this.mc.player != null && this.mc.world != null) {
         BlockPos trackPos = this.containerPositionsForClose[0];
         Block block = this.mc.world.getBlockState(trackPos).getBlock();
         if (!this.isTrackableContainer(block)) {
            if ((Boolean)this.debugMode.get()) {
               this.info("Block at " + trackPos.toShortString() + " is no longer a trackable container", new Object[0]);
            }

            this.lastScreenHandler = null;
            this.containerPositionsForClose = new BlockPos[2];
         } else {
            List<ItemStack> items = new ArrayList<>();
            int containerSlots = this.lastScreenHandler.slots.size() - 36;

            for (int i = 0; i < containerSlots && i < this.lastScreenHandler.slots.size(); i++) {
               Slot slot = (Slot)this.lastScreenHandler.slots.get(i);
               ItemStack stack = slot.getStack();
               if (!stack.isEmpty()) {
                  items.add(stack.copy());
               }
            }

            String currentDim = this.getCurrentDimension();
            String containerType = this.getContainerType(trackPos);
            this.getData().trackContainer(trackPos, currentDim, containerType, items);
            if ((Boolean)this.debugMode.get()) {
               this.info("Tracked on close: " + containerType + " at " + trackPos.toShortString() + " (" + items.size() + " items)", new Object[0]);
            }

            this.lastScreenHandler = null;
            this.containerPositionsForClose = new BlockPos[2];
         }
      } else {
         this.lastScreenHandler = null;
         this.containerPositionsForClose = new BlockPos[2];
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         if ((Boolean)this.renderTracked.get() || (Boolean)this.renderSearchResults.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - this.lastRenderCacheUpdate > 1000L) {
               String currentDim = this.getCurrentDimension();
               this.renderCache = new ArrayList<>(this.getData().getAllContainers(currentDim));
               this.lastRenderCacheUpdate = currentTime;
            }

            double maxDist = ((Integer)this.renderDistance.get()).intValue();
            double maxDistSq = maxDist * maxDist;

            for (TrackedContainer container : this.renderCache) {
               BlockPos pos = container.getPosition();
               double distSq = this.mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
               if (!(distSq > maxDistSq)) {
                  boolean isSearchResult = this.currentSearchItem != null && container.containsItem(this.currentSearchItem);
                  boolean shouldRender = false;
                  SettingColor sideCol = null;
                  SettingColor lineCol = null;
                  if (isSearchResult && (Boolean)this.renderSearchResults.get()) {
                     shouldRender = true;
                     sideCol = (SettingColor)this.searchColor.get();
                     lineCol = (SettingColor)this.searchLineColor.get();
                  } else if ((Boolean)this.renderTracked.get()) {
                     shouldRender = true;
                     sideCol = (SettingColor)this.trackedColor.get();
                     lineCol = (SettingColor)this.trackedColor.get();
                  }

                  if (shouldRender) {
                     event.renderer.box(pos, sideCol, lineCol, (ShapeMode)this.shapeMode.get(), 0);
                     BlockPos otherHalf = this.findDoubleChestOtherHalf(pos);
                     if (otherHalf != null) {
                        event.renderer.box(otherHalf, sideCol, lineCol, (ShapeMode)this.shapeMode.get(), 0);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         if ((Boolean)this.renderLabels.get()) {
            if (this.currentSearchItem != null) {
               DrawContext context = event.drawContext;
               double maxDist = ((Integer)this.labelMaxDistance.get()).intValue();
               double maxDistSq = maxDist * maxDist;
               Vector3d tempVec = new Vector3d();

               for (TrackedContainer container : this.renderCache) {
                  if (container.containsItem(this.currentSearchItem)) {
                     BlockPos pos = container.getPosition();
                     double distSq = this.mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                     if (!(distSq > maxDistSq)) {
                        tempVec.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (NametagUtils.to2D(tempVec, 1.0)) {
                           int screenX = (int)tempVec.x;
                           int screenY = (int)tempVec.y;
                           int itemSize = (int)(16.0 * (Double)this.labelScale.get());
                           int renderX = screenX - itemSize / 2;
                           int renderY = screenY - itemSize / 2;
                           Matrix3x2fStack matrices = context.getMatrices();
                           matrices.pushMatrix();
                           if (itemSize != 16) {
                              float scale = itemSize / 16.0F;
                              matrices.translate(renderX + itemSize / 2.0F, renderY + itemSize / 2.0F);
                              matrices.scale(scale, scale);
                              matrices.translate(-8.0F, -8.0F);
                              context.drawItemWithoutEntity(new ItemStack(this.currentSearchItem), 0, 0);
                           } else {
                              context.drawItemWithoutEntity(new ItemStack(this.currentSearchItem), renderX, renderY);
                           }

                           matrices.popMatrix();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private BlockPos findDoubleChestOtherHalf(BlockPos pos) {
      if (this.mc.world == null) {
         return null;
      }

      BlockState state = this.mc.world.getBlockState(pos);
      Block block = state.getBlock();
      if (!(block instanceof ChestBlock) && !(block instanceof TrappedChestBlock)) {
         return null;
      }

      try {
         if (state.contains(ChestBlock.CHEST_TYPE)) {
            ChestType chestType = (ChestType)state.get(ChestBlock.CHEST_TYPE);
            if (chestType == ChestType.SINGLE) {
               return null;
            }

            if (state.contains(ChestBlock.FACING)) {
               Direction facing = (Direction)state.get(ChestBlock.FACING);
               BlockPos otherPos = chestType == ChestType.LEFT ? pos.offset(facing.rotateYClockwise()) : pos.offset(facing.rotateYCounterclockwise());
               BlockState otherState = this.mc.world.getBlockState(otherPos);
               if (otherState.getBlock().getClass() == block.getClass()) {
                  return otherPos;
               }
            }
         }
      } catch (Exception var8) {
      }

      return null;
   }

   public WWidget getWidget(GuiTheme theme) {
      WTable table = theme.table();
      WButton openBrowser = (WButton)table.add(theme.button("Open Browser (" + this.browserKey.get() + ")")).expandX().widget();
      openBrowser.action = () -> this.mc.setScreen(new ChestTrackerScreen(this));
      table.row();
      WButton searchHeld = (WButton)table.add(theme.button("Search Held Item")).expandX().widget();
      searchHeld.action = this::searchHeldItem;
      table.row();
      WButton clearSearch = (WButton)table.add(theme.button("Clear Search")).expandX().widget();
      clearSearch.action = () -> {
         this.currentSearchItem = null;
         if ((Boolean)this.debugMode.get()) {
            this.info("Search cleared", new Object[0]);
         }
      };
      table.row();
      WButton saveData = (WButton)table.add(theme.button("Save Data")).expandX().widget();
      saveData.action = () -> ChestTrackerDataManager.saveData();
      table.row();
      WButton clearAll = (WButton)table.add(theme.button("Clear All Data")).expandX().widget();
      clearAll.action = () -> {
         this.getData().clearAll();
         if ((Boolean)this.debugMode.get()) {
            this.info("All data cleared", new Object[0]);
         }
      };
      return table;
   }

   private void searchHeldItem() {
      if (this.mc.player != null) {
         ItemStack held = this.mc.player.getMainHandStack();
         if (held.isEmpty()) {
            if ((Boolean)this.debugMode.get()) {
               this.warning("No item in hand", new Object[0]);
            }
         } else {
            this.currentSearchItem = held.getItem();
            List<TrackedContainer> results = this.getData().searchItem(this.currentSearchItem);
            if ((Boolean)this.debugMode.get()) {
               this.info("Found " + results.size() + " containers with " + this.currentSearchItem.getName().getString(), new Object[0]);
            }
         }
      }
   }

   private boolean isTrackableContainer(Block block) {
      if (!(block instanceof ChestBlock) && !(block instanceof TrappedChestBlock)) {
         if (block instanceof BarrelBlock) {
            return (Boolean)this.trackBarrels.get();
         } else if (block instanceof ShulkerBoxBlock) {
            return (Boolean)this.trackShulkers.get();
         } else if (block instanceof EnderChestBlock) {
            return (Boolean)this.trackEnderChests.get();
         } else if (block instanceof HopperBlock) {
            return (Boolean)this.trackHoppers.get();
         } else {
            return !(block instanceof DispenserBlock) && !(block instanceof DropperBlock) ? false : (Boolean)this.trackDispensers.get();
         }
      } else {
         return block != Blocks.COPPER_CHEST
               && block != Blocks.EXPOSED_COPPER_CHEST
               && block != Blocks.WEATHERED_COPPER_CHEST
               && block != Blocks.OXIDIZED_COPPER_CHEST
               && block != Blocks.WAXED_COPPER_CHEST
               && block != Blocks.WAXED_EXPOSED_COPPER_CHEST
               && block != Blocks.WAXED_WEATHERED_COPPER_CHEST
               && block != Blocks.WAXED_OXIDIZED_COPPER_CHEST
            ? (Boolean)this.trackChests.get()
            : (Boolean)this.trackCopperChests.get();
      }
   }

   private String getContainerType(BlockPos pos) {
      if (this.mc.world == null) {
         return "container";
      } else {
         Block block = this.mc.world.getBlockState(pos).getBlock();
         if (block == Blocks.COPPER_CHEST
            || block == Blocks.EXPOSED_COPPER_CHEST
            || block == Blocks.WEATHERED_COPPER_CHEST
            || block == Blocks.OXIDIZED_COPPER_CHEST
            || block == Blocks.WAXED_COPPER_CHEST
            || block == Blocks.WAXED_EXPOSED_COPPER_CHEST
            || block == Blocks.WAXED_WEATHERED_COPPER_CHEST
            || block == Blocks.WAXED_OXIDIZED_COPPER_CHEST) {
            return "copper_chest";
         } else if (block instanceof ChestBlock || block instanceof TrappedChestBlock) {
            return "chest";
         } else if (block instanceof BarrelBlock) {
            return "barrel";
         } else if (block instanceof ShulkerBoxBlock) {
            return "shulker_box";
         } else if (block instanceof EnderChestBlock) {
            return "ender_chest";
         } else if (block instanceof HopperBlock) {
            return "hopper";
         } else if (block instanceof DispenserBlock) {
            return "dispenser";
         } else {
            return block instanceof DropperBlock ? "dropper" : "container";
         }
      }
   }

   private boolean isInContainerScreen() {
      if (this.mc.currentScreen == null) {
         return false;
      } else {
         return this.mc.player == null ? false : this.mc.player.currentScreenHandler != this.mc.player.playerScreenHandler;
      }
   }

   private String getCurrentDimension() {
      return this.mc.world == null ? "unknown" : this.mc.world.getRegistryKey().getValue().toString();
   }

   public Item getCurrentSearchItem() {
      return this.currentSearchItem;
   }

   public void setCurrentSearchItem(Item item) {
      this.currentSearchItem = item;
   }

   public void searchItem(Item item) {
      this.currentSearchItem = item;
   }

   public double getRenderDistance() {
      return ((Integer)this.renderDistance.get()).intValue();
   }
}
