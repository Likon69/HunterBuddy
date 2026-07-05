package com.hunterbuddy.modules.elytraboost;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * State machine for elytra take-off (jump -> fall -> deploy -> boost with firework).
 *
 * <p>Ported from mlep's {@code ElytraTakeoff}. The mlep version is wired
 * to its {@code Utils} (for Baritone jump coordination) and its
 * {@code RotationUtils} (none of which we need). This version:
 * <ul>
 *   <li>Uses {@code mc.options.jumpKey.setPressed} directly (no Baritone
 *       coordination - Baritone can read the key state, so this is
 *       transparent to it).</li>
 *   <li>Uses {@code InvUtils.swap} for hotbar switching (no mixin needed).</li>
 * </ul>
 */
public class ElytraTakeoff {
    public enum Phase {
        IDLE, COOLDOWN, PREPARE, JUMPING, DEPLOYING, BOOSTING
    }

    public interface Listener {
        void onTakeoffSuccess(double speedBps);
        void onTakeoffFailed(String reason);
        default void onTakeoffDebug(String message) {}
    }

    private static final int PREPARE_TICKS = 4;
    private static final int JUMP_HOLD_TICKS = 6;
    private static final int JUMP_TIMEOUT_TICKS = 40;
    private static final int MIN_AIRBORNE_TICKS = 3;
    private static final int AIR_WAIT_TICKS = 2;
    private static final int DEPLOY_TIMEOUT_TICKS = 40;
    private static final int DEPLOY_GROUND_GRACE_TICKS = 4;
    private static final int DEPLOY_RETRY_INTERVAL = 4;
    private static final int MAX_DEPLOY_ATTEMPTS = 4;
    private static final int BOOST_CONFIRM_TICKS = 8;
    private static final int RETRY_COOLDOWN_TICKS = 25;
    private static final int MAX_ATTEMPTS = 5;
    private static final double MIN_SPEED_BPS = 5.0;

    private Phase phase = Phase.IDLE;
    private int phaseTicks = 0;
    private int attempts = 0;
    private int cooldownTicks = 0;
    private int airborneTicks = 0;
    private int deployAttempts = 0;
    private int jumpHoldTicks = 0;
    private int groundTicks = 0;
    private boolean jumpedThisAttempt = false;
    private boolean boosted = false;
    private int rocketSlot = -1;
    private Listener listener;

    public boolean isActive() { return phase != Phase.IDLE; }
    public Phase getPhase() { return phase; }

    public void start(int rocketHotbarSlot, Listener listener) {
        this.listener = listener;
        this.rocketSlot = rocketHotbarSlot;
        this.attempts = 0;
        this.cooldownTicks = 0;
        this.phase = Phase.PREPARE;
        this.phaseTicks = 0;
        this.airborneTicks = 0;
        this.deployAttempts = 0;
        this.jumpHoldTicks = 0;
        this.groundTicks = 0;
        this.jumpedThisAttempt = false;
        this.boosted = false;
        this.releaseJump();
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().options.sneakKey.setPressed(false);
        }
        if (listener != null) listener.onTakeoffDebug("Takeoff started");
    }

    public void cancel() {
        this.releaseJump();
        this.phase = Phase.IDLE;
        this.listener = null;
    }

    public void tickPre() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || phase == Phase.IDLE || phase == Phase.COOLDOWN) return;

        if (phase == Phase.JUMPING) {
            if (mc.player.isOnGround()) {
                if (!jumpedThisAttempt) {
                    holdJump(mc);
                    jumpedThisAttempt = true;
                    jumpHoldTicks = JUMP_HOLD_TICKS;
                    if (listener != null) listener.onTakeoffDebug("Takeoff jump (key press)");
                } else if (jumpHoldTicks > 0) {
                    holdJump(mc);
                }
            } else {
                holdJump(mc);
            }
        } else if (phase == Phase.DEPLOYING) {
            holdJump(mc);
        }
    }

    public void tickPost() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || phase == Phase.IDLE) return;

        if (phase == Phase.COOLDOWN) {
            if (cooldownTicks-- <= 0) beginAttempt();
            return;
        }

        phaseTicks++;

        if (phase == Phase.PREPARE) {
            if (phaseTicks >= PREPARE_TICKS) beginAttempt();
        } else if (phase == Phase.JUMPING) {
            tickJumping(mc);
        } else if (phase == Phase.DEPLOYING) {
            tickDeploying(mc);
        } else if (phase == Phase.BOOSTING) {
            tickBoosting(mc);
        }

        if (phase == Phase.JUMPING && jumpHoldTicks > 0) jumpHoldTicks--;
    }

    private void beginAttempt() {
        phase = Phase.JUMPING;
        phaseTicks = 0;
        airborneTicks = 0;
        deployAttempts = 0;
        jumpHoldTicks = 0;
        groundTicks = 0;
        jumpedThisAttempt = false;
        boosted = false;
        attempts++;
        releaseJump();
        if (listener != null) listener.onTakeoffDebug("Takeoff attempt " + attempts + "/" + MAX_ATTEMPTS);
    }

    private void tickJumping(MinecraftClient mc) {
        if (mc.player.isFallFlying()) {
            enterBoosting();
            return;
        }
        if (!mc.player.isOnGround()) {
            airborneTicks++;
            if (airborneTicks >= MIN_AIRBORNE_TICKS) {
                phase = Phase.DEPLOYING;
                phaseTicks = 0;
                airborneTicks = 0;
                deployAttempts = 0;
                groundTicks = 0;
                if (listener != null) listener.onTakeoffDebug("Airborne - deploying elytra");
            }
            return;
        }
        airborneTicks = 0;
        if (jumpedThisAttempt && phaseTicks > JUMP_TIMEOUT_TICKS) {
            scheduleRetry("jump did not leave ground");
        }
    }

    private void tickDeploying(MinecraftClient mc) {
        if (mc.player.isFallFlying()) {
            enterBoosting();
            return;
        }
        if (mc.player.isOnGround()) {
            groundTicks++;
            if (phaseTicks >= DEPLOY_GROUND_GRACE_TICKS && groundTicks >= 2) {
                scheduleRetry("landed before elytra deployed");
            }
        } else {
            groundTicks = 0;
            airborneTicks++;
            if (airborneTicks >= AIR_WAIT_TICKS
                && (deployAttempts == 0 || airborneTicks % DEPLOY_RETRY_INTERVAL == 0)
                && deployAttempts < MAX_DEPLOY_ATTEMPTS) {
                deployElytra(mc);
                deployAttempts++;
                if (listener != null) listener.onTakeoffDebug("Deploy attempt " + deployAttempts);
            }
        }
        if (phaseTicks > DEPLOY_TIMEOUT_TICKS) {
            scheduleRetry("elytra deploy timed out");
        }
    }

    private void tickBoosting(MinecraftClient mc) {
        if (!mc.player.isFallFlying()) {
            if (phaseTicks > 6) scheduleRetry("lost glide before boost completed");
            return;
        }
        if (!boosted && phaseTicks >= 2) {
            if (useFirework(mc)) {
                boosted = true;
                phaseTicks = 0;
                if (listener != null) listener.onTakeoffDebug("Firework boost while gliding");
            } else if (phaseTicks > 20) {
                scheduleRetry("no firework for boost");
            }
            return;
        }
        if (boosted) {
            double speed = getSpeedBps(mc);
            if (phaseTicks >= BOOST_CONFIRM_TICKS && (speed >= MIN_SPEED_BPS || mc.player.getVelocity().y > 0.08)) {
                finishSuccess(speed);
            } else if (phaseTicks == 14 && speed < MIN_SPEED_BPS) {
                if (useFirework(mc)) {
                    phaseTicks = 0;
                    if (listener != null) listener.onTakeoffDebug("Second firework boost");
                }
            } else if (phaseTicks > 40) {
                scheduleRetry("boost did not reach flight speed");
            }
        }
    }

    private void enterBoosting() {
        phase = Phase.BOOSTING;
        phaseTicks = 0;
        boosted = false;
        releaseJump();
        if (listener != null) listener.onTakeoffDebug("Elytra gliding");
    }

    private void scheduleRetry(String reason) {
        releaseJump();
        if (attempts >= MAX_ATTEMPTS) {
            finishFailed(reason + " (max attempts)");
            return;
        }
        phase = Phase.COOLDOWN;
        cooldownTicks = RETRY_COOLDOWN_TICKS;
        phaseTicks = 0;
        if (listener != null) listener.onTakeoffDebug("Retry in " + RETRY_COOLDOWN_TICKS + " ticks: " + reason);
    }

    private void finishSuccess(double speedBps) {
        releaseJump();
        phase = Phase.IDLE;
        Listener current = listener;
        listener = null;
        if (current != null) current.onTakeoffSuccess(speedBps);
    }

    private void finishFailed(String reason) {
        releaseJump();
        phase = Phase.IDLE;
        Listener current = listener;
        listener = null;
        if (current != null) current.onTakeoffFailed(reason);
    }

    private void releaseJump() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.options.jumpKey.setPressed(false);
    }

    private void holdJump(MinecraftClient mc) {
        if (mc.player == null) return;
        mc.options.jumpKey.setPressed(true);
    }

    private void deployElytra(MinecraftClient mc) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING));
        }
        // In Yarn 1.21+build.9 the client method to begin gliding is named
        // startFallFlying; in newer Yarn versions it's startGliding.
        try {
            mc.player.getClass().getMethod("startFallFlying").invoke(mc.player);
        } catch (Throwable ignored) {
            try {
                mc.player.getClass().getMethod("startGliding").invoke(mc.player);
            } catch (Throwable ignored2) {
                // Method not present on this Minecraft version; the server
                // will activate the elytra via the START_FALL_FLYING packet above.
            }
        }
    }

    private boolean useFirework(MinecraftClient mc) {
        if (mc.interactionManager == null || !mc.player.isFallFlying()) return false;

        if (rocketSlot >= 0 && rocketSlot < 9) {
            InvUtils.swap(rocketSlot, false);
        }

        FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
        if (!hotbar.found()) return false;

        if (hotbar.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
            return true;
        }
        if (!hotbar.isMainHand()) {
            InvUtils.swap(hotbar.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.swapBack();
            return true;
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private double getSpeedBps(MinecraftClient mc) {
        Vec3d velocity = mc.player.getVelocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z) * 20.0;
    }

    public static boolean isOverSolidGround(MinecraftClient mc) {
        BlockPos below = mc.player.getBlockPos().down();
        return !mc.world.getBlockState(below).isReplaceable();
    }
}
