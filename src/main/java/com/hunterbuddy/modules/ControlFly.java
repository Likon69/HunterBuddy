package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.mixin.accessor.PlayerInventoryAccessor;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import meteordevelopment.meteorclient.events.world.ParticleEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class ControlFly extends Module {
   public static ControlFly INSTANCE;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgFirework = this.settings.createGroup("Firework");
   private final Setting<Boolean> autoFirework = this.sgGeneral
      .add(
         new Builder().name("auto-firework").description("Automatically use fireworks to maintain flight.").defaultValue(true)
            .build()
      );
   private final Setting<Boolean> inventoryFireworks = this.sgGeneral
      .add(
         new Builder().name("inventory-fireworks")
                     .description("Allow using fireworks from inventory, not just hotbar.")
                  .defaultValue(true)
               .visible(this.autoFirework::get)
            .build()
      );
   private final Setting<Double> boostSpeed = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("boost-speed")
               .description("Speed to add when flying.")
            .defaultValue(0.0)
            .min(0.0)
            .max(0.5)
            .sliderRange(0.0, 0.2)
            .build()
      );
   private final Setting<Double> hoverPitch = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("hover-pitch")
               .description("Pitch angle when idle. Negative values angle upward to maintain altitude.")
            .defaultValue(-1.25)
            .min(-10.0)
            .max(10.0)
            .sliderRange(-10.0, 10.0)
            .build()
      );
   private final Setting<Boolean> smoothCamera = this.sgGeneral
      .add(
         new Builder().name("smooth-camera").description("Smooth out camera jitter when hovering idle.").defaultValue(true)
            .build()
      );
   private final Setting<Boolean> hideRocketParticles = this.sgGeneral
      .add(
         new Builder().name("hide-rocket-particles")
                  .description("Hide firework rocket particles including launch and trail effects.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Double> fireworkDelay = this.sgFirework
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("firework-delay")
                  .description("Base delay in seconds between firework uses.")
               .defaultValue(1.5)
               .min(0.5)
               .sliderRange(0.5, 5.0)
               .visible(this.autoFirework::get)
            .build()
      );
   private final Setting<Boolean> durationScaling = this.sgFirework
      .add(
         new Builder().name("duration-scaling").description("Scale firework delay based on firework flight duration.")
                  .defaultValue(true)
               .visible(this.autoFirework::get)
            .build()
      );
   private boolean flipFlop = false;
   private double nextFireworkDelay = 1.5;
   private long lastFireworkTime = 0L;
   private int previousSlot = -1;
   private int swapBackTicks = 0;
   private boolean idleHovering = false;
   private double prevActualX;
   private double prevActualZ;
   private double smoothX;
   private double smoothZ;
   private double prevSmoothX;
   private double prevSmoothZ;

   public ControlFly() {
      super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "control-fly", "GrimAC-compatible elytra flight control using WASD keys.");
      INSTANCE = this;
   }

   public boolean shouldHideRocketParticles() {
      return this.isActive() && (Boolean)this.hideRocketParticles.get();
   }

   public boolean isIdleHovering() {
      return this.isActive() && this.idleHovering && (Boolean)this.smoothCamera.get();
   }

   public double getSmoothCamX(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.prevSmoothX, this.smoothX);
   }

   public double getSmoothCamZ(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.prevSmoothZ, this.smoothZ);
   }

   @EventHandler
   private void onParticle(ParticleEvent event) {
      if (this.shouldHideRocketParticles() && event.particle.getType() == ParticleTypes.FIREWORK) {
         event.cancel();
      }
   }

   public void onActivate() {
      this.flipFlop = false;
      this.idleHovering = false;
      this.nextFireworkDelay = (Double)this.fireworkDelay.get();
      this.lastFireworkTime = System.currentTimeMillis();
      this.previousSlot = -1;
      this.swapBackTicks = 0;
   }

   public void onDeactivate() {
      this.idleHovering = false;
      RotationUtils.getInstance().clearRotations();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.mc.player.isGliding()) {
            if (this.swapBackTicks > 0) {
               this.swapBackTicks--;
               if (this.swapBackTicks == 0 && this.previousSlot != -1) {
                  InvUtils.swap(this.previousSlot, false);
                  this.previousSlot = -1;
               }
            }

            if ((Boolean)this.autoFirework.get()) {
               this.handleFirework();
            }

            if ((Double)this.boostSpeed.get() > 0.0 && !this.mc.player.isUsingItem()) {
               this.addSpeed((Double)this.boostSpeed.get());
            }

            this.handleMovement();
         }
      }
   }

   @EventHandler
   private void onTickPost(Post event) {
      if (this.mc.player != null && this.mc.player.isGliding() && this.idleHovering) {
         this.prevSmoothX = this.smoothX;
         this.prevSmoothZ = this.smoothZ;
         double currentX = this.mc.player.getX();
         double currentZ = this.mc.player.getZ();
         this.smoothX = (currentX + this.prevActualX) * 0.5;
         this.smoothZ = (currentZ + this.prevActualZ) * 0.5;
         this.prevActualX = currentX;
         this.prevActualZ = currentZ;
      }
   }

   private void addSpeed(double speed) {
      float yaw = this.mc.player.getYaw() * (float) (Math.PI / 180.0);
      this.mc.player.addVelocity(-Math.sin(yaw) * speed, 0.0, Math.cos(yaw) * speed);
   }

   private void handleFirework() {
      double delayMs = this.nextFireworkDelay * 1000.0;
      if (!(System.currentTimeMillis() - this.lastFireworkTime < delayMs)) {
         if (!this.mc.options.useKey.isPressed()) {
            int fireworkSlot = this.findFireworkHotbar();
            if (fireworkSlot == -1 && (Boolean)this.inventoryFireworks.get()) {
               fireworkSlot = this.moveFireworkToHotbar();
            }

            if (fireworkSlot != -1) {
               if ((Boolean)this.durationScaling.get()) {
                  ItemStack stack = this.mc.player.getInventory().getStack(fireworkSlot);
                  FireworksComponent component = (FireworksComponent)stack.get(DataComponentTypes.FIREWORKS);
                  int flightDuration = component != null ? component.flightDuration() : 1;
                  this.nextFireworkDelay = flightDuration * 0.5 + 0.5;
               } else {
                  this.nextFireworkDelay = (Double)this.fireworkDelay.get();
               }

               PlayerInventoryAccessor invAccessor = (PlayerInventoryAccessor)this.mc.player.getInventory();
               int currentSlot = invAccessor.getSelectedSlot();
               if (currentSlot != fireworkSlot) {
                  this.previousSlot = currentSlot;
                  InvUtils.swap(fireworkSlot, false);
                  this.swapBackTicks = 2;
               }

               this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
               this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
               this.lastFireworkTime = System.currentTimeMillis();
            }
         }
      }
   }

   private int findFireworkHotbar() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.isOf(Items.FIREWORK_ROCKET)) {
            return i;
         }
      }

      return -1;
   }

   private int moveFireworkToHotbar() {
      int invSlot = -1;

      for (int i = 9; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.isOf(Items.FIREWORK_ROCKET)) {
            invSlot = i;
            break;
         }
      }

      if (invSlot == -1) {
         return -1;
      }

      int targetHotbar = this.findEmptyHotbarSlot();
      if (targetHotbar == -1) {
         targetHotbar = 8;
      }

      InvUtils.move().from(invSlot).toHotbar(targetHotbar);
      return targetHotbar;
   }

   private int findEmptyHotbarSlot() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   private void handleMovement() {
      Vec3d vec = Vec3d.ZERO;
      float yaw = this.mc.player.getYaw();
      if (this.mc.options.forwardKey.isPressed()) {
         vec = vec.add(Vec3d.fromPolar(0.0F, yaw));
      }

      if (this.mc.options.backKey.isPressed()) {
         vec = vec.add(Vec3d.fromPolar(0.0F, yaw + 180.0F));
      }

      if (this.mc.options.leftKey.isPressed()) {
         vec = vec.add(Vec3d.fromPolar(0.0F, yaw - 90.0F));
      }

      if (this.mc.options.rightKey.isPressed()) {
         vec = vec.add(Vec3d.fromPolar(0.0F, yaw + 90.0F));
      }

      if (this.mc.options.jumpKey.isPressed()) {
         vec = vec.add(0.0, 1.0, 0.0);
      }

      if (this.mc.options.sneakKey.isPressed()) {
         vec = vec.add(0.0, -1.0, 0.0);
      }

      boolean hasFirework = this.findFireworkHotbar() != -1 || (Boolean)this.inventoryFireworks.get() && this.hasFireworkInInventory();
      if (vec.lengthSquared() < 1.0E-4) {
         if (hasFirework) {
            if (!this.idleHovering) {
               this.idleHovering = true;
               double x = this.mc.player.getX();
               double z = this.mc.player.getZ();
               this.prevActualX = x;
               this.prevActualZ = z;
               this.smoothX = x;
               this.smoothZ = z;
               this.prevSmoothX = x;
               this.prevSmoothZ = z;
            }

            if (this.mc.options.useKey.isPressed()) {
               RotationUtils.getInstance().setRotationFullInstant(this.mc.player.getYaw(), this.mc.player.getPitch());
            } else {
               float idlePitch = ((Double)this.hoverPitch.get()).floatValue();
               if (this.flipFlop) {
                  this.flipFlop = false;
                  RotationUtils.getInstance().setRotationFullInstant(0.0F, idlePitch);
               } else {
                  this.flipFlop = true;
                  RotationUtils.getInstance().setRotationFullInstant(180.0F, idlePitch);
               }
            }
         }
      } else {
         this.idleHovering = false;
         float[] rot = this.getYawPitch(vec);
         RotationUtils.getInstance().setRotationFullInstant(rot[0], rot[1]);
      }
   }

   private boolean hasFireworkInInventory() {
      for (int i = 9; i < 36; i++) {
         if (this.mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
            return true;
         }
      }

      return false;
   }

   private float[] getYawPitch(Vec3d vec) {
      if (vec.lengthSquared() < 1.0E-8) {
         return new float[]{this.mc.player.getYaw(), this.mc.player.getPitch()};
      }

      vec = vec.normalize();
      float yaw = (float)Math.toDegrees(Math.atan2(-vec.x, vec.z));
      double horizontalLength = Math.sqrt(vec.x * vec.x + vec.z * vec.z);
      float pitch = (float)Math.toDegrees(-Math.atan2(vec.y, horizontalLength));
      pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }

   public String getInfoString() {
      return this.mc.player != null && this.mc.player.isGliding() ? "Flying" : null;
   }
}
