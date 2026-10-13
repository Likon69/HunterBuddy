package com.hunterbuddy.behavior.mc;

import baritone.api.utils.input.Input;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.ConsumablePolicy;
import com.hunterbuddy.behavior.resource.Lease;
import com.hunterbuddy.behavior.resource.LeaseState;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.util.Rockets;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class FlightBridge {
    private final WorldReader world;
    private final ConsumablePolicy consumables;
    private final Outputs outputs;
    private final BaritoneBridge baritone;
    private final ElytraTakeoff takeoff = new ElytraTakeoff();
    private Lease lease;
    private Stage stage = Stage.IDLE;
    private String failure;
    private long preAt = -1;
    private long postAt = -1;
    private boolean waterJump;

    public FlightBridge(WorldReader world, ConsumablePolicy consumables, Outputs outputs, BaritoneBridge baritone) {
        this.world = world; this.consumables = consumables; this.outputs = outputs; this.baritone = baritone;
    }

    public void require(TickContext ctx, Resource... resources) {
        for (Resource resource : resources) if (ctx.leases().holder(resource).filter(holder -> holder.state() == LeaseState.GRANTED && holder.owner().equals(ctx.owner())).isEmpty()) {
            throw new IllegalStateException("flight action without " + resource + " lease");
        }
    }

    public void start(TickContext ctx, int slot) {
        require(ctx, Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.ROCKETS);
        if (!canTakeoff(slot)) throw new IllegalStateException("usable elytra and reserved plain rockets required");
        if (takeoff.isActive()) throw new IllegalStateException("takeoff already active");
        lease = ctx.leases().holder(Resource.ROCKETS).orElseThrow();
        stage = Stage.ACTIVE;
        failure = null;
        takeoff.start(slot, new ElytraTakeoff.Listener() {
            @Override
            public void onTakeoffSuccess(double speedBps) { stage = Stage.SUCCEEDED; }
            @Override
            public void onTakeoffFailed(String reason) { failure = reason; stage = Stage.FAILED; }
        });
    }

    public boolean canTakeoff(int slot) {
        return world.elytraEquipped() && world.matchesSlot(slot, Rockets::isPlain) && consumables.rocket();
    }

    public void pre(TickContext ctx) {
        if (stage != Stage.ACTIVE || preAt == ctx.tick()) return;
        require(ctx, Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.ROCKETS);
        preAt = ctx.tick();
        if (!consumables.rocket()) { cancel(); failure = "rocket reserve reached"; stage = Stage.FAILED; return; }
        takeoff.tickPre();
    }

    public void post(TickContext ctx) {
        if (stage != Stage.ACTIVE || preAt != ctx.tick() || postAt == ctx.tick()) return;
        require(ctx, Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.ROCKETS);
        postAt = ctx.tick();
        if (!consumables.rocket()) { cancel(); failure = "rocket reserve reached"; stage = Stage.FAILED; return; }
        takeoff.tickPost();
    }

    public void jump(TickContext ctx, boolean pressed) {
        require(ctx, Resource.MOVEMENT, Resource.LOOK);
        waterJump = pressed;
        if (pressed) Utils.holdJump(world.client()); else Utils.releaseJump(world.client());
    }

    public boolean rocket(TickContext ctx, int slot) {
        require(ctx, Resource.SLOT, Resource.ROCKETS, Resource.INTERACT);
        if (!world.available() || !world.client().player.isGliding() || !world.matchesSlot(slot, Rockets::isPlain) || !consumables.rocket()) return false;
        return world.client().interactionManager.interactItem(world.client().player, Hand.MAIN_HAND).isAccepted();
    }

    public void cancel() {
        if (takeoff.isActive()) takeoff.cancel();
        if (waterJump) Utils.releaseJump(world.client());
        waterJump = false;
        lease = null;
        stage = Stage.IDLE;
    }

    public void release(Lease candidate) { if (candidate == lease) cancel(); }
    public void endJump() { if (waterJump) Utils.releaseJump(world.client()); waterJump = false; }
    public boolean jumpPressed() { return baritone.inputs().map(input -> input.isInputForcedDown(Input.JUMP)).orElse(false); }

    public boolean ownsMovement() { return takeoff.isActive() || waterJump; }
    public boolean succeeded() { return stage == Stage.SUCCEEDED; }
    public boolean running() { return stage == Stage.ACTIVE; }
    public String failure() { return stage == Stage.FAILED ? failure : null; }

    @EventHandler(priority = EventPriority.LOWEST)
    private void interact(InteractItemEvent event) {
        if (!world.available() || world.legacyRegear() || !world.client().player.getStackInHand(event.hand).isOf(Items.FIREWORK_ROCKET)) return;
        boolean owned = outputs.ownElytra() || stage == Stage.ACTIVE;
        if (owned && (!Rockets.isPlain(world.client().player.getStackInHand(event.hand)) || !consumables.rocket() || outputs.landingFlight()
            || stage == Stage.ACTIVE && (lease == null || lease.state() != LeaseState.GRANTED))) {
            event.toReturn = ActionResult.FAIL;
        }
    }

    private enum Stage { IDLE, ACTIVE, SUCCEEDED, FAILED }
}
