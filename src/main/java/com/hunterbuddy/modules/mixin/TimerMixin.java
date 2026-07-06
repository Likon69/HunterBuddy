package com.hunterbuddy.modules.mixin;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

@Mixin(value = Timer.class, remap = false)
public abstract class TimerMixin extends Module {
   @Shadow
   @Final
   private SettingGroup sgGeneral;
   @Shadow
   @Final
   private Setting<Double> multiplier;
   @Unique
   private Setting<Boolean> autoAdjust;
   @Unique
   private Setting<Boolean> onlyWhenTraveling;
   @Unique
   private Setting<Double> travelSpeedThreshold;
   @Unique
   private Setting<Double> minSpeed;
   @Unique
   private Setting<Double> maxSpeed;
   @Unique
   private Setting<Integer> checkRadius;
   @Unique
   private Setting<Integer> unloadedThreshold;
   @Unique
   private Setting<Double> adjustSpeed;
   @Unique
   private Setting<Integer> checkInterval;
   @Unique
   private double targetSpeed = 1.0;
   @Unique
   private double currentAutoSpeed = 1.0;
   @Unique
   private int tickCounter = 0;
   @Unique
   private int lastUnloadedCount = 0;
   @Unique
   private Vec3d lastPlayerPos = null;
   @Unique
   private int speedCheckTicks = 0;
   @Unique
   private double currentSpeed = 0.0;

   public TimerMixin(Category category, String name, String description) {
      super(category, name, description);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void onInit(CallbackInfo ci) {
      this.autoAdjust = this.sgGeneral
         .add(
            new Builder().name("auto-adjust").description("Automatically adjust timer speed based on chunk loading (for 2b2t).")
                  .defaultValue(true)
               .build()
         );
      this.onlyWhenTraveling = this.sgGeneral
         .add(
            new Builder().name("only-when-traveling")
                        .description("Only use auto-adjust when moving faster than threshold speed. Sets timer to 1.0 when slower.")
                     .defaultValue(true)
                  .visible(this.autoAdjust::get)
               .build()
         );
      this.travelSpeedThreshold = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("travel-speed-threshold")
                     .description("Minimum speed (km/h) required for auto-adjust to activate.")
                  .defaultValue(30.0)
                  .min(1.0)
                  .sliderRange(1.0, 100.0)
                  .visible(() -> (Boolean)this.autoAdjust.get() && (Boolean)this.onlyWhenTraveling.get())
               .build()
         );
      this.minSpeed = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("min-speed")
                     .description("Minimum timer speed when chunks aren't loading (slows down time).")
                  .defaultValue(0.2)
                  .min(0.1)
                  .sliderRange(0.1, 1.0)
                  .visible(this.autoAdjust::get)
               .build()
         );
      this.maxSpeed = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("max-speed")
                     .description("Maximum timer speed when chunks load fine (1.0 = vanilla).")
                  .defaultValue(1.0)
                  .min(0.1)
                  .sliderRange(0.1, 2.0)
                  .visible(this.autoAdjust::get)
               .build()
         );
      this.checkRadius = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                           .name("check-radius")
                        .description("Radius in chunks to check for loading (higher = more conservative).")
                     .defaultValue(4)
                  .min(1)
                  .sliderRange(1, 8)
                  .visible(this.autoAdjust::get)
               .build()
         );
      this.unloadedThreshold = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                           .name("unloaded-threshold")
                        .description("Number of unloaded chunks before slowing down.")
                     .defaultValue(6)
                  .min(1)
                  .sliderRange(1, 20)
                  .visible(this.autoAdjust::get)
               .build()
         );
      this.adjustSpeed = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("adjust-speed")
                     .description("How quickly timer adjusts to chunk loading (higher = faster).")
                  .defaultValue(0.15)
                  .min(0.01)
                  .sliderRange(0.01, 1.0)
                  .visible(this.autoAdjust::get)
               .build()
         );
      this.checkInterval = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                           .name("check-interval")
                        .description("Ticks between chunk load checks (lower = more responsive).")
                     .defaultValue(5)
                  .min(1)
                  .sliderRange(1, 40)
                  .visible(this.autoAdjust::get)
               .build()
         );
   }

   public void onActivate() {
      this.currentAutoSpeed = (Double)this.multiplier.get();
      this.targetSpeed = (Double)this.multiplier.get();
      this.tickCounter = 0;
      this.lastUnloadedCount = 0;
      this.lastPlayerPos = null;
      this.speedCheckTicks = 0;
      this.currentSpeed = 0.0;
   }

   @Unique
   @EventHandler
   private void onTick(Pre event) {
      if (Utils.canUpdate() && (Boolean)this.autoAdjust.get() && this.mc.player != null) {
         if (this.lastPlayerPos != null) {
            Vec3d currentPos = this.mc.player.getEntityPos();
            double distanceTraveled = currentPos.subtract(this.lastPlayerPos).multiply(1.0, 0.0, 1.0).length();
            double speedBPS = distanceTraveled * 20.0;
            this.currentSpeed = speedBPS * 3.6;
         }

         this.lastPlayerPos = this.mc.player.getEntityPos();
         this.tickCounter++;
         if (this.tickCounter >= (Integer)this.checkInterval.get()) {
            this.tickCounter = 0;
            if ((Boolean)this.onlyWhenTraveling.get() && this.currentSpeed < (Double)this.travelSpeedThreshold.get()) {
               this.targetSpeed = 1.0;
               this.currentAutoSpeed = 1.0;
               this.multiplier.set(1.0);
               this.lastUnloadedCount = 0;
            } else {
               int unloadedChunks = this.countUnloadedChunks();
               if (unloadedChunks > (Integer)this.unloadedThreshold.get()) {
                  double severity = Math.min(1.0, unloadedChunks / (((Integer)this.unloadedThreshold.get()).intValue() * 2.0));
                  this.targetSpeed = (Double)this.minSpeed.get() + ((Double)this.maxSpeed.get() - (Double)this.minSpeed.get()) * (1.0 - severity);
               } else {
                  this.targetSpeed = (Double)this.maxSpeed.get();
               }

               double diff = this.targetSpeed - this.currentAutoSpeed;
               if (Math.abs(diff) > 0.01) {
                  this.currentAutoSpeed = this.currentAutoSpeed + diff * (Double)this.adjustSpeed.get();
                  this.multiplier.set(Math.max((Double)this.minSpeed.get(), Math.min((Double)this.maxSpeed.get(), this.currentAutoSpeed)));
               }

               this.lastUnloadedCount = unloadedChunks;
            }
         }
      }
   }

   @Unique
   private int countUnloadedChunks() {
      if (this.mc.player != null && this.mc.world != null) {
         ClientChunkManager chunkManager = this.mc.world.getChunkManager();
         ChunkPos playerChunkPos = this.mc.player.getChunkPos();
         int radius = (Integer)this.checkRadius.get();
         int unloadedCount = 0;

         for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
               int chunkX = playerChunkPos.x + x;
               int chunkZ = playerChunkPos.z + z;
               Chunk chunk = chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
               if (chunk == null) {
                  unloadedCount++;
               }
            }
         }

         return unloadedCount;
      } else {
         return 0;
      }
   }

   @Unique
   public boolean isAutoAdjustEnabled() {
      return this.autoAdjust != null && (Boolean)this.autoAdjust.get();
   }

   @Unique
   public double getCurrentAutoSpeed() {
      return this.currentAutoSpeed;
   }

   @Unique
   public int getLastUnloadedCount() {
      return this.lastUnloadedCount;
   }
}