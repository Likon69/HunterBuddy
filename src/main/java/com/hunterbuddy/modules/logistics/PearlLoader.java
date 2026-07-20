package com.hunterbuddy.modules.logistics;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.HunterBuddyAddon;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BlockPosSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class PearlLoader extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgCoordinates = this.settings.createGroup("Coordinates");
   private final SettingGroup sgWhitelist = this.settings.createGroup("Whitelist");
   private final SettingGroup sgLoadLocations = this.settings.createGroup("Load Locations");
   private final Setting<BlockPos> walkPoint1 = this.sgCoordinates
      .add(
         new Builder().name("Walk Point 1").description("First position for anti-AFK walking loop")
               .defaultValue(new BlockPos(0, 64, 0)).build()
      );
   private final Setting<BlockPos> walkPoint2 = this.sgCoordinates
      .add(
         new Builder().name("Walk Point 2").description("Second position for anti-AFK walking loop")
               .defaultValue(new BlockPos(10, 64, 0)).build()
      );
   private final Setting<Boolean> useWhitelist = this.sgWhitelist
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("Use Whitelist")
                  .description("Only accept triggers from whitelisted players")
               .defaultValue(true)
            .build()
      );
   private final Setting<List<String>> whitelistedPlayers = this.sgWhitelist
      .add(
         new meteordevelopment.meteorclient.settings.StringListSetting.Builder()
                        .name("Whitelisted Players")
                     .description("Players who can trigger pearl loading")
                  .defaultValue(new ArrayList()).visible(this.useWhitelist::get)
            .build()
      );
   private final Setting<Double> reachThreshold = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("Reach Threshold")
               .description("Fallback distance threshold if Baritone check fails")
            .defaultValue(1.0)
            .min(0.1)
            .max(5.0)
            .sliderRange(0.1, 5.0)
            .build()
      );
   private final Setting<Boolean> debugMode = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("Debug Mode")
                  .description("Show detailed debug information")
               .defaultValue(false)
            .build()
      );
   private final Setting<Integer> arrivalWaitTicks = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("Arrival Wait Ticks")
                  .description("Ticks to wait after arriving at position for lag")
               .defaultValue(19)
            .min(0)
            .max(100)
            .sliderRange(0, 100)
            .build()
      );
   private List<PearlLoader.LoadLocation> loadLocations = new ArrayList<>();
   private PearlLoader.State currentState = PearlLoader.State.WALKING_TO_POINT2;
   private boolean pearlLoadTriggered = false;
   private long stateStartTime = 0L;
   private long lastInteractionTime = 0L;
   private BlockPos currentTarget = null;
   private int rotationTicks = 0;
   private float targetYaw = 0.0F;
   private float targetPitch = 0.0F;
   private boolean trapdoorWasClosed = false;
   private PearlLoader.LoadLocation currentLoadLocation = null;

   public PearlLoader() {
      super(HunterBuddyAddon.LOGISTICS_CATEGORY, "PearlLoader", "Anti-AFK loop with pearl loading capability");
   }

   public NbtCompound toTag() {
      NbtCompound tag = super.toTag();
      NbtList locationsList = new NbtList();

      for (PearlLoader.LoadLocation location : this.loadLocations) {
         locationsList.add(location.toTag());
      }

      tag.put("loadLocations", locationsList);
      return tag;
   }

   public Module fromTag(NbtCompound tag) {
      super.fromTag(tag);
      this.loadLocations.clear();
      if (tag.contains("loadLocations")) {
         Optional<NbtList> locationsListOpt = tag.getList("loadLocations");
         if (locationsListOpt.isPresent()) {
            NbtList locationsList = locationsListOpt.get();

            for (int i = 0; i < locationsList.size(); i++) {
               Optional<NbtCompound> compoundOpt = locationsList.getCompound(i);
               if (compoundOpt.isPresent()) {
                  PearlLoader.LoadLocation location = new PearlLoader.LoadLocation();
                  location.fromTag(compoundOpt.get());
                  this.loadLocations.add(location);
               }
            }
         }
      }

      return this;
   }

   public void onActivate() {
      this.currentState = PearlLoader.State.WALKING_TO_POINT2;
      this.pearlLoadTriggered = false;
      this.stateStartTime = System.currentTimeMillis();
      this.currentTarget = (BlockPos)this.walkPoint2.get();
      this.startPathing(this.currentTarget);
      this.info("Pearl Loader activated - Starting anti-AFK loop", new Object[0]);
   }

   public void onDeactivate() {
      this.stopPathing();
      this.pearlLoadTriggered = false;
      this.currentState = PearlLoader.State.WALKING_TO_POINT2;
      this.stateStartTime = 0L;
      this.currentTarget = null;
      this.info("Pearl Loader deactivated", new Object[0]);
   }

   @EventHandler
   private void onReceiveMessage(ReceiveMessageEvent event) {
      if (this.isActive() && !this.pearlLoadTriggered) {
         String fullMessage = event.getMessage().getString();
         String messageLower = fullMessage.toLowerCase();
         Iterator var4 = this.loadLocations.iterator();

         PearlLoader.LoadLocation location;
         while (true) {
            if (!var4.hasNext()) {
               return;
            }

            location = (PearlLoader.LoadLocation)var4.next();
            String keyword = location.triggerKeyword.toLowerCase();
            if (messageLower.contains(keyword)) {
               if (!(Boolean)this.useWhitelist.get()) {
                  this.info("Pearl load triggered by keyword: " + location.triggerKeyword, new Object[0]);
                  break;
               }

               String sender = this.extractSenderName(fullMessage);
               if (sender != null && this.isPlayerWhitelisted(sender)) {
                  this.info("Pearl load triggered by " + sender + " with keyword: " + location.triggerKeyword, new Object[0]);
                  break;
               }

               if ((Boolean)this.debugMode.get()) {
                  this.info("Trigger ignored - player not whitelisted: " + sender, new Object[0]);
               }
            }
         }

         this.triggerPearlLoad(location);
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.isActive() && this.mc.player != null && this.mc.world != null) {
         if (this.pearlLoadTriggered) {
            this.handlePearlLoadingSequence();
         } else {
            this.handleWalkingLoop();
         }
      }
   }

   private void handleWalkingLoop() {
      if (this.currentTarget != null) {
         if (this.isPathingDone()) {
            if (this.currentState == PearlLoader.State.WALKING_TO_POINT1) {
               this.currentState = PearlLoader.State.WALKING_TO_POINT2;
               this.currentTarget = (BlockPos)this.walkPoint2.get();
               if ((Boolean)this.debugMode.get()) {
                  this.info("Reached Point 1, walking to Point 2", new Object[0]);
               }
            } else if (this.currentState == PearlLoader.State.WALKING_TO_POINT2) {
               this.currentState = PearlLoader.State.WALKING_TO_POINT1;
               this.currentTarget = (BlockPos)this.walkPoint1.get();
               if ((Boolean)this.debugMode.get()) {
                  this.info("Reached Point 2, walking to Point 1", new Object[0]);
               }
            }

            this.startPathing(this.currentTarget);
         }
      }
   }

   private void handlePearlLoadingSequence() {
      switch (this.currentState) {
         case WALKING_TO_TRAPDOOR:
            this.handleWalkingToTrapdoor();
            break;
         case ARRIVED_AT_TRAPDOOR:
            this.handleArrivedAtTrapdoor();
            break;
         case ROTATING_TO_TRAPDOOR:
            this.handleRotatingToTrapdoor();
            break;
         case CLOSING_TRAPDOOR:
            this.handleClosingTrapdoor();
            break;
         case WAITING_CLOSED:
            this.handleWaitingClosed();
            break;
         case OPENING_TRAPDOOR:
            this.handleOpeningTrapdoor();
            break;
         case WALKING_TO_LOAD_POSITION:
            this.handleWalkingToLoadPosition();
            break;
         case ARRIVED_AT_LOAD_POSITION:
            this.handleArrivedAtLoadPosition();
            break;
         case STANDING_AT_LOAD:
            this.handleStandingAtLoad();
            break;
         case RETURNING_FROM_LOAD:
            this.handleReturningFromLoad();
      }
   }

   private void handleWalkingToTrapdoor() {
      BlockPos targetPos = this.getTrapdoorApproachPosition();
      double distance = this.getDistanceToTarget(targetPos);
      if (distance <= (Double)this.reachThreshold.get()) {
         this.stopPathing();
         this.currentState = PearlLoader.State.ARRIVED_AT_TRAPDOOR;
         this.stateStartTime = System.currentTimeMillis();
         if ((Boolean)this.debugMode.get()) {
            this.info("Within threshold of trapdoor approach position, waiting for settle", new Object[0]);
         }
      } else if (this.isPathingDone()) {
         this.startPathing(targetPos);
         if ((Boolean)this.debugMode.get()) {
            this.info("Pathing done but not close enough, restarting pathing to approach position", new Object[0]);
         }
      }
   }

   private void handleArrivedAtTrapdoor() {
      long elapsed = System.currentTimeMillis() - this.stateStartTime;
      long waitTime = (Integer)this.arrivalWaitTicks.get() * 50;
      BlockPos targetPos = this.getTrapdoorApproachPosition();
      double distance = this.getDistanceToTarget(targetPos);
      if (distance > (Double)this.reachThreshold.get()) {
         this.currentState = PearlLoader.State.WALKING_TO_TRAPDOOR;
         this.startPathing(targetPos);
         if ((Boolean)this.debugMode.get()) {
            this.info("Drifted away during wait, returning to walking", new Object[0]);
         }
      } else {
         if (elapsed >= waitTime) {
            this.currentState = PearlLoader.State.ROTATING_TO_TRAPDOOR;
            this.stateStartTime = System.currentTimeMillis();
            this.rotationTicks = 0;
            if ((Boolean)this.debugMode.get()) {
               this.info("Wait after arrival complete, proceeding to rotate", new Object[0]);
            }
         }
      }
   }

   private void handleWalkingToLoadPosition() {
      BlockPos target = this.currentLoadLocation.position;
      double distance = this.getDistanceToTarget(target);
      if (distance <= (Double)this.reachThreshold.get()) {
         this.stopPathing();
         this.currentState = PearlLoader.State.ARRIVED_AT_LOAD_POSITION;
         this.stateStartTime = System.currentTimeMillis();
         if ((Boolean)this.debugMode.get()) {
            this.info("Within threshold of load position, waiting for settle", new Object[0]);
         }
      } else if (this.isPathingDone()) {
         this.startPathing(target);
         if ((Boolean)this.debugMode.get()) {
            this.info("Pathing done but not close enough, restarting pathing to load position", new Object[0]);
         }
      }
   }

   private void handleArrivedAtLoadPosition() {
      long elapsed = System.currentTimeMillis() - this.stateStartTime;
      long waitTime = (Integer)this.arrivalWaitTicks.get() * 50;
      BlockPos target = this.currentLoadLocation.position;
      double distance = this.getDistanceToTarget(target);
      if (distance > (Double)this.reachThreshold.get()) {
         this.currentState = PearlLoader.State.WALKING_TO_LOAD_POSITION;
         this.startPathing(target);
         if ((Boolean)this.debugMode.get()) {
            this.info("Drifted away during wait, returning to walking", new Object[0]);
         }
      } else {
         if (elapsed >= waitTime) {
            this.currentState = PearlLoader.State.STANDING_AT_LOAD;
            this.stateStartTime = System.currentTimeMillis();
            if ((Boolean)this.debugMode.get()) {
               this.info("Wait after arrival complete, starting stand time", new Object[0]);
            }
         }
      }
   }

   private void handleRotatingToTrapdoor() {
      if (this.rotateToBlock(this.currentLoadLocation.position)) {
         BlockState state = this.mc.world.getBlockState(this.currentLoadLocation.position);
         if (!(state.getBlock() instanceof TrapdoorBlock)) {
            this.error("No trapdoor found at specified position!", new Object[0]);
            this.resetToLoop();
            return;
         }

         boolean isOpen = (Boolean)state.get(TrapdoorBlock.OPEN);
         if ((Boolean)this.debugMode.get()) {
            this.info("Rotation complete, trapdoor is currently " + (isOpen ? "OPEN" : "CLOSED") + ", proceeding to interact", new Object[0]);
         }

         this.currentState = PearlLoader.State.CLOSING_TRAPDOOR;
         this.stateStartTime = System.currentTimeMillis();
      }
   }

   private void handleClosingTrapdoor() {
      double distToTrapdoor = this.getDistanceToTarget(this.currentLoadLocation.position);
      if (distToTrapdoor > 5.0) {
         this.error("Too far from trapdoor to interact safely! Restarting approach.", new Object[0]);
         this.currentState = PearlLoader.State.WALKING_TO_TRAPDOOR;
         BlockPos approachPos = this.getTrapdoorApproachPosition();
         this.startPathing(approachPos);
      } else {
         BlockState state = this.mc.world.getBlockState(this.currentLoadLocation.position);
         boolean isOpen = (Boolean)state.get(TrapdoorBlock.OPEN);
         if (isOpen) {
            this.interactWithTrapdoor();
            this.trapdoorWasClosed = true;
            if ((Boolean)this.debugMode.get()) {
               this.info("Closed trapdoor", new Object[0]);
            }
         } else {
            if ((Boolean)this.debugMode.get()) {
               this.info("Trapdoor already closed", new Object[0]);
            }

            this.trapdoorWasClosed = false;
         }

         this.currentState = PearlLoader.State.WAITING_CLOSED;
         this.stateStartTime = System.currentTimeMillis();
      }
   }

   private void handleWaitingClosed() {
      long elapsed = System.currentTimeMillis() - this.stateStartTime;
      long waitTime = (long)(this.currentLoadLocation.trapdoorCloseTime * 1000.0);
      if (elapsed > 500L) {
         BlockState state = this.mc.world.getBlockState(this.currentLoadLocation.position);
         boolean isOpen = (Boolean)state.get(TrapdoorBlock.OPEN);
         if (isOpen) {
            if ((Boolean)this.debugMode.get()) {
               this.info("Trapdoor not closed properly, retrying close", new Object[0]);
            }

            this.currentState = PearlLoader.State.CLOSING_TRAPDOOR;
            this.stateStartTime = System.currentTimeMillis();
            return;
         }
      }

      if ((Boolean)this.debugMode.get() && elapsed % 1000L < 50L) {
         this.info(
            String.format("Waiting with trapdoor closed: %.1f / %.1f seconds", elapsed / 1000.0, this.currentLoadLocation.trapdoorCloseTime), new Object[0]
         );
      }

      if (elapsed >= waitTime) {
         this.currentState = PearlLoader.State.OPENING_TRAPDOOR;
         this.stateStartTime = System.currentTimeMillis();
         if ((Boolean)this.debugMode.get()) {
            this.info("Wait complete after " + elapsed / 1000.0 + " seconds, now opening trapdoor", new Object[0]);
         }
      }
   }

   private void handleOpeningTrapdoor() {
      double distToTrapdoor = this.getDistanceToTarget(this.currentLoadLocation.position);
      if (distToTrapdoor > 5.0) {
         this.error("Too far from trapdoor to interact safely! Restarting approach.", new Object[0]);
         this.currentState = PearlLoader.State.WALKING_TO_TRAPDOOR;
         BlockPos approachPos = this.getTrapdoorApproachPosition();
         this.startPathing(approachPos);
      } else {
         BlockState state = this.mc.world.getBlockState(this.currentLoadLocation.position);
         boolean isOpen = (Boolean)state.get(TrapdoorBlock.OPEN);
         if (!isOpen) {
            this.interactWithTrapdoor();
            if ((Boolean)this.debugMode.get()) {
               this.info("Opened trapdoor", new Object[0]);
            }
         } else if ((Boolean)this.debugMode.get()) {
            this.info("Trapdoor already open", new Object[0]);
         }

         this.info("Pearl loading complete", new Object[0]);
         this.resetToLoop();
      }
   }

   private void handleStandingAtLoad() {
      long elapsed = System.currentTimeMillis() - this.stateStartTime;
      long waitTime = (long)(this.currentLoadLocation.standTime * 1000.0);
      if (elapsed >= waitTime) {
         this.currentState = PearlLoader.State.RETURNING_FROM_LOAD;
         this.currentTarget = (BlockPos)this.walkPoint1.get();
         this.startPathing(this.currentTarget);
         if ((Boolean)this.debugMode.get()) {
            this.info("Stand time complete, returning to loop", new Object[0]);
         }
      }
   }

   private void handleReturningFromLoad() {
      if (this.isPathingDone() || this.getDistanceToTarget(this.currentTarget) <= (Double)this.reachThreshold.get()) {
         this.info("Pearl loading complete", new Object[0]);
         this.resetToLoop();
      }
   }

   private void triggerPearlLoad(PearlLoader.LoadLocation location) {
      if (!this.pearlLoadTriggered) {
         this.pearlLoadTriggered = true;
         this.currentLoadLocation = location;
         this.stopPathing();
         if (location.mode == PearlLoader.LoadMode.TRAPDOOR) {
            this.currentState = PearlLoader.State.WALKING_TO_TRAPDOOR;
            BlockPos approachPos = this.getTrapdoorApproachPosition();
            this.startPathing(approachPos);
            if ((Boolean)this.debugMode.get()) {
               this.info("Starting trapdoor pearl load sequence for: " + location.triggerKeyword, new Object[0]);
            }
         } else {
            this.currentState = PearlLoader.State.WALKING_TO_LOAD_POSITION;
            this.startPathing(location.position);
            if ((Boolean)this.debugMode.get()) {
               this.info("Starting walk-to pearl load sequence for: " + location.triggerKeyword, new Object[0]);
            }
         }

         this.stateStartTime = System.currentTimeMillis();
      }
   }

   private void resetToLoop() {
      this.pearlLoadTriggered = false;
      this.currentLoadLocation = null;
      this.currentState = PearlLoader.State.WALKING_TO_POINT1;
      this.currentTarget = (BlockPos)this.walkPoint1.get();
      this.startPathing(this.currentTarget);
      this.stateStartTime = System.currentTimeMillis();
   }

   private boolean rotateToBlock(BlockPos pos) {
      Vec3d target = Vec3d.ofCenter(pos);
      Vec3d playerEyes = this.mc.player.getEyePos();
      Vec3d lookVec = target.subtract(playerEyes);
      double dx = lookVec.x;
      double dy = lookVec.y;
      double dz = lookVec.z;
      double distance = Math.sqrt(dx * dx + dz * dz);
      this.targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      this.targetPitch = (float)Math.toDegrees(Math.atan2(-dy, distance));
      this.targetPitch = MathHelper.clamp(this.targetPitch, -90.0F, 90.0F);
      this.rotationTicks++;
      float yawDiff = this.wrapDegrees(this.targetYaw - this.mc.player.getYaw());
      float pitchDiff = this.targetPitch - this.mc.player.getPitch();
      float rotSpeed = 0.1F;
      this.mc.player.setYaw(this.mc.player.getYaw() + yawDiff * rotSpeed);
      this.mc.player.setPitch(this.mc.player.getPitch() + pitchDiff * rotSpeed);
      return Math.abs(yawDiff) < 2.0F && Math.abs(pitchDiff) < 2.0F || this.rotationTicks > 50;
   }

   private void interactWithTrapdoor() {
      if (System.currentTimeMillis() - this.lastInteractionTime >= 500L) {
         Vec3d hitVec = Vec3d.ofCenter(this.currentLoadLocation.position);
         Direction hitSide = this.getClosestSide(this.currentLoadLocation.position);
         BlockHitResult hitResult = new BlockHitResult(hitVec, hitSide, this.currentLoadLocation.position, false);
         this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
         this.mc.player.swingHand(Hand.MAIN_HAND);
         this.lastInteractionTime = System.currentTimeMillis();
      }
   }

   private BlockPos getTrapdoorApproachPosition() {
      BlockPos trapPos = this.currentLoadLocation.position;
      Direction[] dirs = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
      BlockPos bestPos = null;
      double minDist = Double.MAX_VALUE;

      for (Direction dir : dirs) {
         BlockPos checkPos = trapPos.offset(dir);
         BlockState state = this.mc.world.getBlockState(checkPos);
         BlockState below = this.mc.world.getBlockState(checkPos.down());
         if (state.isAir() && below.isSolidBlock(this.mc.world, checkPos.down())) {
            double dist = this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(checkPos));
            if (dist < minDist) {
               minDist = dist;
               bestPos = checkPos;
            }
         }
      }

      return bestPos != null ? bestPos : trapPos.north();
   }

   private Direction getClosestSide(BlockPos pos) {
      Vec3d playerPos = this.mc.player.getEntityPos();
      Vec3d blockCenter = Vec3d.ofCenter(pos);
      Vec3d diff = playerPos.subtract(blockCenter);
      if (Math.abs(diff.x) > Math.abs(diff.z)) {
         return diff.x > 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         return diff.z > 0.0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   private double getDistanceToTarget(BlockPos target) {
      return this.mc.player != null && target != null ? this.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(target)) : Double.MAX_VALUE;
   }

   private void startPathing(BlockPos target) {
      if (target != null) {
         try {
            Class.forName("baritone.api.BaritoneAPI");
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
         } catch (ClassNotFoundException e) {
            this.error("Baritone not available!", new Object[0]);
         }
      }
   }

   private void stopPathing() {
      try {
         Class.forName("baritone.api.BaritoneAPI");
         BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
      } catch (ClassNotFoundException var2) {
      }
   }

   private boolean isPathingDone() {
      try {
         return !BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
      } catch (Exception e) {
         return false;
      }
   }

   private String extractSenderName(String message) {
      if (message.contains(" whispers: ")) {
         String beforeWhispers = message.substring(0, message.indexOf(" whispers: "));
         beforeWhispers = beforeWhispers.replaceAll("\u00a7[0-9a-fk-or]", "");
         String[] parts = beforeWhispers.split(" ");
         if (parts.length >= 1) {
            String name = parts[parts.length - 1].trim();
            if ((Boolean)this.debugMode.get()) {
               this.info("Extracted whisper sender: " + name, new Object[0]);
            }

            return name;
         }
      } else if (message.contains(": ")) {
         String beforeColon = message.substring(0, message.indexOf(": "));
         beforeColon = beforeColon.replaceAll("\u00a7[0-9a-fk-or]", "");
         if (beforeColon.contains("<") && beforeColon.contains(">")) {
            int start = beforeColon.lastIndexOf("<");
            int end = beforeColon.lastIndexOf(">");
            if (start < end) {
               String name = beforeColon.substring(start + 1, end).trim();
               if ((Boolean)this.debugMode.get()) {
                  this.info("Extracted public sender: " + name, new Object[0]);
               }

               return name;
            }
         }

         String[] parts = beforeColon.split(" ");
         if (parts.length > 0) {
            String name = parts[parts.length - 1].replaceAll("[<>\\[\\]]", "").trim();
            if ((Boolean)this.debugMode.get()) {
               this.info("Extracted sender: " + name, new Object[0]);
            }

            return name;
         }
      }

      if ((Boolean)this.debugMode.get()) {
         this.warning("Could not extract sender from message: " + message, new Object[0]);
      }

      return null;
   }

   private boolean isPlayerWhitelisted(String playerName) {
      if (playerName == null) {
         return false;
      }

      for (String whitelisted : this.whitelistedPlayers.get()) {
         if (whitelisted.equalsIgnoreCase(playerName)) {
            return true;
         }
      }

      return false;
   }

   private float wrapDegrees(float degrees) {
      degrees %= 360.0F;
      if (degrees >= 180.0F) {
         degrees -= 360.0F;
      }

      if (degrees < -180.0F) {
         degrees += 360.0F;
      }

      return degrees;
   }

   public WWidget getWidget(GuiTheme theme) {
      WVerticalList mainList = theme.verticalList();
      WButton addButton = (WButton)mainList.add(theme.button("Add Load Location")).widget();
      addButton.action = () -> {
         this.loadLocations
            .add(new PearlLoader.LoadLocation("!pearl" + (this.loadLocations.size() + 1), PearlLoader.LoadMode.TRAPDOOR, new BlockPos(0, 64, 0), 2.0, 1.0));
         this.info("Load location added. Close and reopen settings to see changes.", new Object[0]);
      };

      for (int i = 0; i < this.loadLocations.size(); i++) {
         PearlLoader.LoadLocation location = this.loadLocations.get(i);
         mainList.add(theme.horizontalSeparator()).expandX();
         WHorizontalList headerList = (WHorizontalList)mainList.add(theme.horizontalList()).expandX().widget();
         headerList.add(theme.label("Location " + (i + 1) + ":")).expandX();
         WButton removeButton = (WButton)headerList.add(theme.button("-")).widget();
         removeButton.action = () -> {
            if (this.loadLocations.remove(location)) {
               this.info("Load location removed. Close and reopen settings to see changes.", new Object[0]);
            } else {
               this.error("Failed to remove load location. Please close and reopen settings.", new Object[0]);
            }
         };
         WButton triggerButton = (WButton)headerList.add(theme.button("Trigger")).widget();
         triggerButton.action = () -> {
            if (!this.pearlLoadTriggered && this.isActive()) {
               this.info("Manually triggering pearl load: " + location.triggerKeyword, new Object[0]);
               this.triggerPearlLoad(location);
            }
         };
         WTextBox keywordBox = (WTextBox)mainList.add(theme.textBox(location.triggerKeyword)).expandX().widget();
         keywordBox.action = () -> location.triggerKeyword = keywordBox.get();
         mainList.add(theme.label("Position: " + location.position.toShortString()));
         WHorizontalList posButtons = (WHorizontalList)mainList.add(theme.horizontalList()).expandX().widget();
         WButton setPosButton = (WButton)posButtons.add(theme.button("Set to Player Pos")).widget();
         setPosButton.action = () -> {
            if (this.mc.player != null) {
               location.position = this.mc.player.getBlockPos();
            }
         };
         mainList.add(theme.label("Mode: " + location.mode.toString()));
         WHorizontalList modeButtons = (WHorizontalList)mainList.add(theme.horizontalList()).expandX().widget();
         WButton trapdoorButton = (WButton)modeButtons.add(theme.button("Trapdoor")).widget();
         trapdoorButton.action = () -> location.mode = PearlLoader.LoadMode.TRAPDOOR;
         WButton walkToButton = (WButton)modeButtons.add(theme.button("Walk To")).widget();
         walkToButton.action = () -> location.mode = PearlLoader.LoadMode.WALK_TO;
         if (location.mode == PearlLoader.LoadMode.TRAPDOOR) {
            mainList.add(theme.label("Close Time: " + location.trapdoorCloseTime + "s"));
         } else {
            mainList.add(theme.label("Stand Time: " + location.standTime + "s"));
         }
      }

      mainList.add(theme.horizontalSeparator()).expandX();
      WButton testLoop = (WButton)mainList.add(theme.button("Test Walking Loop")).widget();
      testLoop.action = () -> {
         if (this.isActive()) {
            this.info("Testing walking loop", new Object[0]);
            this.resetToLoop();
         }
      };
      return mainList;
   }

   public static class LoadLocation implements ISerializable<PearlLoader.LoadLocation> {
      public String triggerKeyword = "!pearl";
      public PearlLoader.LoadMode mode = PearlLoader.LoadMode.TRAPDOOR;
      public BlockPos position = new BlockPos(0, 64, 0);
      public double trapdoorCloseTime = 2.0;
      public double standTime = 1.0;

      public LoadLocation() {
      }

      public LoadLocation(String keyword, PearlLoader.LoadMode mode, BlockPos pos, double closeTime, double standTime) {
         this.triggerKeyword = keyword;
         this.mode = mode;
         this.position = pos;
         this.trapdoorCloseTime = closeTime;
         this.standTime = standTime;
      }

      public NbtCompound toTag() {
         NbtCompound tag = new NbtCompound();
         tag.putString("keyword", this.triggerKeyword);
         tag.putString("mode", this.mode.name());
         tag.putInt("x", this.position.getX());
         tag.putInt("y", this.position.getY());
         tag.putInt("z", this.position.getZ());
         tag.putDouble("closeTime", this.trapdoorCloseTime);
         tag.putDouble("standTime", this.standTime);
         return tag;
      }

      public PearlLoader.LoadLocation fromTag(NbtCompound tag) {
         this.triggerKeyword = tag.getString("keyword").orElse("");
         this.mode = PearlLoader.LoadMode.valueOf(tag.getString("mode").orElse("TRAPDOOR"));
         this.position = new BlockPos(tag.getInt("x").orElse(0), tag.getInt("y").orElse(0), tag.getInt("z").orElse(0));
         this.trapdoorCloseTime = tag.getDouble("closeTime").orElse(1.0);
         this.standTime = tag.getDouble("standTime").orElse(0.0);
         return this;
      }
   }

   public enum LoadMode {
      TRAPDOOR("Trapdoor - Interact with trapdoor to load pearl"),
      WALK_TO("Walk To - Walk to position and return");

      private final String description;

      LoadMode(String description) {
         this.description = description;
      }

      @Override
      public String toString() {
         return this.description;
      }
   }

   private enum State {
      WALKING_TO_POINT1,
      WALKING_TO_POINT2,
      WALKING_TO_TRAPDOOR,
      ARRIVED_AT_TRAPDOOR,
      ROTATING_TO_TRAPDOOR,
      CLOSING_TRAPDOOR,
      WAITING_CLOSED,
      OPENING_TRAPDOOR,
      WALKING_TO_LOAD_POSITION,
      ARRIVED_AT_LOAD_POSITION,
      STANDING_AT_LOAD,
      RETURNING_FROM_LOAD;
   }
}
