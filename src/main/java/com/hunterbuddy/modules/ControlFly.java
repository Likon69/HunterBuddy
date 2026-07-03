package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.ParticleEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * GrimAC-compatible elytra flight control using WASD keys.
 *
 * <p>Ported from mlep's {@code ControlFly}. The original depends on
 * mlep's {@code RotationUtils} (sends server-side rotation packets to
 * disguise client rotations) and a {@code PlayerInventoryAccessor} mixin
 * (reads {@code selectedSlot}).
 *
 * <p>For simplicity, this port:
 * <ul>
 *   <li>Uses {@code mc.player.setYaw/setPitch} directly (client-side).
 *       This means the rotation is visible to the server, but the
 *       module never does anything unusual with rotations, so GrimAC's
 *       vanilla-physics check should still pass.</li>
 *   <li>Reads {@code selectedSlot} via the public {@code PlayerInventory.selectedSlot}
 *       field directly (no mixin needed).</li>
 * </ul>
 *
 * <p>The {@code boost-speed} setting is kept as in the original (default 0.0,
 * max 0.5). Increasing it will add velocity directly, which GrimAC's
 * vanilla-physics check will detect and reject (rubberband). User beware.
 */
public class ControlFly extends Module {
    public static ControlFly INSTANCE;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFirework = settings.createGroup("Firework");

    private final Setting<Boolean> autoFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Automatically use fireworks to maintain flight.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> inventoryFireworks = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-fireworks")
        .description("Allow using fireworks from inventory, not just hotbar.")
        .defaultValue(true)
        .visible(autoFirework::get)
        .build()
    );

    private final Setting<Double> boostSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("boost-speed")
        .description("Speed to add when flying. WARNING: any value > 0 will be rejected by GrimAC's vanilla-physics simulation check.")
        .defaultValue(0.0)
        .min(0.0)
        .max(0.5)
        .sliderRange(0.0, 0.2)
        .build()
    );

    private final Setting<Double> hoverPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("hover-pitch")
        .description("Pitch angle when idle. Negative values angle upward to maintain altitude.")
        .defaultValue(-1.25)
        .min(-10.0)
        .max(10.0)
        .sliderRange(-10.0, 10.0)
        .build()
    );

    private final Setting<Boolean> smoothCamera = sgGeneral.add(new BoolSetting.Builder()
        .name("smooth-camera")
        .description("Smooth out camera jitter when hovering idle.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideRocketParticles = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-rocket-particles")
        .description("Hide firework rocket particles including launch and trail effects.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fireworkDelay = sgFirework.add(new DoubleSetting.Builder()
        .name("firework-delay")
        .description("Base delay in seconds between firework uses.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 5.0)
        .visible(autoFirework::get)
        .build()
    );

    private final Setting<Boolean> durationScaling = sgFirework.add(new BoolSetting.Builder()
        .name("duration-scaling")
        .description("Scale firework delay based on firework flight duration.")
        .defaultValue(true)
        .visible(autoFirework::get)
        .build()
    );

    // The original mlep toggles yaw between 0° and 180° each tick when idle to
    // generate some yaw change (so the server doesn't kick for AFK). The visual
    // effect is jarring (player snaps south/north every tick). Replaced with
    // a small random jitter, similar to Meteor's YawLock "jitter-amount" —
    // generates a tiny ±0.5° yaw change each tick that the server accepts
    // as natural movement but the player barely notices.
    private double lastJitterYaw = 0.0;
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
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "control-fly",
            "GrimAC-compatible elytra flight control using WASD keys.");
        INSTANCE = this;
    }

    public boolean shouldHideRocketParticles() {
        return isActive() && hideRocketParticles.get();
    }

    public boolean isIdleHovering() {
        return isActive() && idleHovering && smoothCamera.get();
    }

    public double getSmoothCamX(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevSmoothX, smoothX);
    }

    public double getSmoothCamZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevSmoothZ, smoothZ);
    }

    @EventHandler
    private void onParticle(ParticleEvent event) {
        if (shouldHideRocketParticles() && event.particle.getType() == ParticleTypes.FIREWORK) {
            event.cancel();
        }
    }

    @Override
    public void onActivate() {
        lastJitterYaw = 0.0;
        idleHovering = false;
        nextFireworkDelay = fireworkDelay.get();
        lastFireworkTime = System.currentTimeMillis();
        previousSlot = -1;
        swapBackTicks = 0;
    }

    @Override
    public void onDeactivate() {
        idleHovering = false;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || !mc.player.isFallFlying()) return;

        if (swapBackTicks > 0) {
            swapBackTicks--;
            if (swapBackTicks == 0 && previousSlot != -1) {
                InvUtils.swap(previousSlot, false);
                previousSlot = -1;
            }
        }

        if (autoFirework.get()) handleFirework();

        if (boostSpeed.get() > 0.0 && !mc.player.isUsingItem()) {
            addSpeed(boostSpeed.get());
        }

        handleMovement();
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null || !mc.player.isFallFlying() || !idleHovering) return;
        prevSmoothX = smoothX;
        prevSmoothZ = smoothZ;
        double currentX = mc.player.getX();
        double currentZ = mc.player.getZ();
        smoothX = (currentX + prevActualX) * 0.5;
        smoothZ = (currentZ + prevActualZ) * 0.5;
        prevActualX = currentX;
        prevActualZ = currentZ;
    }

    private void addSpeed(double speed) {
        float yaw = mc.player.getYaw() * (float) (Math.PI / 180.0);
        mc.player.addVelocity(-Math.sin(yaw) * speed, 0.0, Math.cos(yaw) * speed);
    }

    private void handleFirework() {
        if (System.currentTimeMillis() - lastFireworkTime < nextFireworkDelay * 1000.0) return;
        if (mc.options.useKey.isPressed()) return;

        int fireworkSlot = findFireworkHotbar();
        if (fireworkSlot == -1 && inventoryFireworks.get()) {
            fireworkSlot = moveFireworkToHotbar();
        }
        if (fireworkSlot == -1) return;

        if (durationScaling.get()) {
            ItemStack stack = mc.player.getInventory().getStack(fireworkSlot);
            FireworksComponent component = (FireworksComponent) stack.get(DataComponentTypes.FIREWORKS);
            int flightDuration = component != null ? component.flightDuration() : 1;
            nextFireworkDelay = flightDuration * 0.5 + 0.5;
        } else {
            nextFireworkDelay = fireworkDelay.get();
        }

        // selectedSlot is a public field in PlayerInventory on Yarn 1.21.x
        int currentSlot = mc.player.getInventory().selectedSlot;
        if (currentSlot != fireworkSlot) {
            previousSlot = currentSlot;
            InvUtils.swap(fireworkSlot, false);
            swapBackTicks = 2;
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
        lastFireworkTime = System.currentTimeMillis();
    }

    private int findFireworkHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) return i;
        }
        return -1;
    }

    private int moveFireworkToHotbar() {
        int invSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                invSlot = i;
                break;
            }
        }
        if (invSlot == -1) return -1;
        int targetHotbar = findEmptyHotbarSlot();
        if (targetHotbar == -1) targetHotbar = 8;
        InvUtils.move().from(invSlot).toHotbar(targetHotbar);
        return targetHotbar;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private void handleMovement() {
        Vec3d vec = Vec3d.ZERO;
        float yaw = mc.player.getYaw();
        if (mc.options.forwardKey.isPressed()) vec = vec.add(Vec3d.fromPolar(0.0F, yaw));
        if (mc.options.backKey.isPressed()) vec = vec.add(Vec3d.fromPolar(0.0F, yaw + 180.0F));
        if (mc.options.leftKey.isPressed()) vec = vec.add(Vec3d.fromPolar(0.0F, yaw - 90.0F));
        if (mc.options.rightKey.isPressed()) vec = vec.add(Vec3d.fromPolar(0.0F, yaw + 90.0F));
        if (mc.options.jumpKey.isPressed()) vec = vec.add(0.0, 1.0, 0.0);
        if (mc.options.sneakKey.isPressed()) vec = vec.add(0.0, -1.0, 0.0);

        boolean hasFirework = findFireworkHotbar() != -1
            || (inventoryFireworks.get() && hasFireworkInInventory());

        if (vec.lengthSquared() < 1.0e-4) {
            if (hasFirework) {
                if (!idleHovering) {
                    idleHovering = true;
                    double x = mc.player.getX();
                    double z = mc.player.getZ();
                    prevActualX = x;
                    prevActualZ = z;
                    smoothX = x;
                    smoothZ = z;
                    prevSmoothX = x;
                    prevSmoothZ = z;
                }
                if (mc.options.useKey.isPressed()) {
                    mc.player.setYaw(mc.player.getYaw());
                } else {
                    float idlePitch = hoverPitch.get().floatValue();
                    // Small random jitter around the current yaw (±0.5°) so the
                    // server sees a tick of movement but the player doesn't
                    // visibly snap between south and north.
                    double jitter = (Math.random() - 0.5) * 1.0;
                    lastJitterYaw = mc.player.getYaw() + jitter;
                    mc.player.setYaw((float) lastJitterYaw);
                    mc.player.setPitch(idlePitch);
                }
            }
        } else {
            idleHovering = false;
            float[] rot = getYawPitch(vec);
            mc.player.setYaw(rot[0]);
            mc.player.setPitch(rot[1]);
        }
    }

    private boolean hasFireworkInInventory() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) return true;
        }
        return false;
    }

    private float[] getYawPitch(Vec3d vec) {
        if (vec.lengthSquared() < 1.0e-8) {
            return new float[]{mc.player.getYaw(), mc.player.getPitch()};
        }
        vec = vec.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
        double horizontalLength = Math.sqrt(vec.x * vec.x + vec.z * vec.z);
        float pitch = (float) Math.toDegrees(-Math.atan2(vec.y, horizontalLength));
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        return new float[]{yaw, pitch};
    }

    @Override
    public String getInfoString() {
        return mc.player != null && mc.player.isFallFlying() ? "Flying" : null;
    }
}