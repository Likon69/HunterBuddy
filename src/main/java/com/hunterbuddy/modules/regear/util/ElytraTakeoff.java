package com.hunterbuddy.modules.regear.util;

import net.minecraft.client.MinecraftClient;

public class ElytraTakeoff {
    private static final int PREPARE_TICKS = 4;
    private static final int RETRY_COOLDOWN = 25;
    private static final int MAX_ATTEMPTS = 5;

    public enum Phase { IDLE, COOLDOWN, PREPARE, JUMPING, DEPLOYING, BOOSTING }

    public interface Listener {
        void onTakeoffSuccess(double speedBps);

        void onTakeoffFailed(String reason);

        default void onTakeoffDebug(String message) {
        }
    }

    private final TakeoffAttempt attempt = new TakeoffAttempt();
    private final TakeoffControl control = new Transitions();
    private Phase phase = Phase.IDLE;
    private int attempts;
    private int cooldownTicks;
    private int rocketSlot = -1;
    private Listener listener;

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    public Phase getPhase() {
        return phase;
    }

    public void start(int rocketHotbarSlot, Listener listener) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        this.listener = listener;
        rocketSlot = rocketHotbarSlot;
        attempts = 0;
        cooldownTicks = 0;
        attempt.reset();
        phase = Phase.PREPARE;
        RotationUtils.getInstance().clearRotations();
        Utils.releaseJump(mc);
        Utils.setPressed(mc.options.sneakKey, false);
        control.debug("Takeoff started");
    }

    public void cancel() {
        Utils.releaseJump(MinecraftClient.getInstance());
        phase = Phase.IDLE;
        listener = null;
    }

    public void tickPre() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (phase == Phase.JUMPING) {
            TakeoffJumpPhase.pre(mc, attempt, control);
        } else if (phase == Phase.DEPLOYING) {
            TakeoffDeployPhase.pre(mc, attempt);
        }
    }

    public void tickPost() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || phase == Phase.IDLE) return;
        if (phase == Phase.COOLDOWN) {
            if (cooldownTicks-- <= 0) beginAttempt(mc);
            return;
        }
        attempt.phaseTicks++;
        runPhase(mc);
        if (phase == Phase.JUMPING && attempt.jumpHoldTicks > 0) attempt.jumpHoldTicks--;
    }

    private void runPhase(MinecraftClient mc) {
        switch (phase) {
            case PREPARE -> {
                if (attempt.phaseTicks >= PREPARE_TICKS) beginAttempt(mc);
            }
            case JUMPING -> TakeoffJumpPhase.post(mc, attempt, control);
            case DEPLOYING -> TakeoffDeployPhase.post(mc, attempt, control);
            case BOOSTING -> TakeoffBoostPhase.post(mc, attempt, rocketSlot, control);
            default -> {
            }
        }
    }

    private void beginAttempt(MinecraftClient mc) {
        phase = Phase.JUMPING;
        attempt.reset();
        attempts++;
        Utils.releaseJump(mc);
        control.debug("Takeoff attempt " + attempts + "/" + MAX_ATTEMPTS);
    }

    private void finish(Runnable notification) {
        Utils.releaseJump(MinecraftClient.getInstance());
        phase = Phase.IDLE;
        notification.run();
    }

    private final class Transitions implements TakeoffControl {
        @Override
        public void debug(String message) {
            if (listener != null) listener.onTakeoffDebug(message);
        }

        @Override
        public void toDeploying() {
            phase = Phase.DEPLOYING;
            attempt.resetForDeploy();
            debug("Airborne - deploying elytra");
        }

        @Override
        public void enterBoosting() {
            phase = Phase.BOOSTING;
            attempt.resetForBoost();
            Utils.releaseJump(MinecraftClient.getInstance());
            debug("Elytra gliding");
        }

        @Override
        public void retry(String reason) {
            Utils.releaseJump(MinecraftClient.getInstance());
            if (attempts >= MAX_ATTEMPTS) {
                fail(reason + " (max attempts)");
                return;
            }
            phase = Phase.COOLDOWN;
            cooldownTicks = RETRY_COOLDOWN;
            attempt.phaseTicks = 0;
            debug("Retry in " + RETRY_COOLDOWN + " ticks: " + reason);
        }

        @Override
        public void succeed(double speedBps) {
            Listener detached = detach();
            finish(() -> {
                if (detached != null) detached.onTakeoffSuccess(speedBps);
            });
        }

        private void fail(String reason) {
            Listener detached = detach();
            finish(() -> {
                if (detached != null) detached.onTakeoffFailed(reason);
            });
        }

        private Listener detach() {
            Listener detached = listener;
            listener = null;
            return detached;
        }
    }
}
