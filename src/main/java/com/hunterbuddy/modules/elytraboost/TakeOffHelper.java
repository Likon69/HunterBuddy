package com.hunterbuddy.modules.elytraboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Auto take-off helper for elytra flight.
 *
 * <p>State machine:
 * <pre>
 *   IDLE
 *     -> EQUIPPING  (verify / equip elytra to chest slot)
 *       -> JUMPING  (first jump)
 *         -> WAITING_FOR_FLIGHT
 *           -> IDLE                  (mc.player.isFallFlying() == true)
 *           -> ERROR_NO_ELYTRA       (elytra not found in inventory)
 *           -> ERROR_TIMEOUT         (flight never started)
 * </pre>
 *
 * <p>Drive it from the module's tick: call {@link #start()} to begin a sequence,
 * then {@link #tick()} every tick while {@link #isRunning()} returns true.
 */
public class TakeOffHelper {
    public enum State {
        IDLE,
        EQUIPPING,
        JUMPING,
        WAITING_FOR_FLIGHT,
        ERROR_NO_ELYTRA,
        ERROR_TIMEOUT
    }

    private static final EquipmentSlot CHEST = EquipmentSlot.CHEST;
    private static final int MAX_WAIT_TICKS = 20;

    // PlayerInventory global slot indices: 0..8 hotbar, 9..35 main inventory,
    // 36..39 armor (boots, leggings, chestplate, helmet), 40 offhand.
    // The chestplate slot is index 2 inside the 4-slot armor array, which is
    // global slot 36 + 2 = 38.
    private static final int CHEST_SLOT_INDEX = 36 + 2;

    private final MinecraftClient mc;
    private State state = State.IDLE;
    private int ticksInState = 0;

    public TakeOffHelper() {
        this.mc = MinecraftClient.getInstance();
    }

    /** Begin a new take-off sequence. Idempotent if already running. */
    public void start() {
        if (state != State.ERROR_NO_ELYTRA && state != State.ERROR_TIMEOUT) {
            state = State.EQUIPPING;
            ticksInState = 0;
        }
    }

    public boolean isRunning() {
        return state == State.EQUIPPING
            || state == State.JUMPING
            || state == State.WAITING_FOR_FLIGHT;
    }

    public State getState() {
        return state;
    }

    /** Reset to IDLE. Call this after a successful take-off, or to abort. */
    public void reset() {
        state = State.IDLE;
        ticksInState = 0;
    }

    /** Tick the state machine forward. No-op when {@link #isRunning()} is false. */
    public void tick() {
        if (mc.player == null) return;
        if (!isRunning()) return;
        ticksInState++;

        switch (state) {
            case EQUIPPING -> {
                if (equipElytraToChest()) {
                    state = State.JUMPING;
                    ticksInState = 0;
                } else {
                    state = State.ERROR_NO_ELYTRA;
                }
            }

            case JUMPING -> {
                // Single jump to engage elytra flight; the actual 'is gliding?' check
                // belongs to WAITING_FOR_FLIGHT, where it's actually meaningful.
                mc.player.jump();
                state = State.WAITING_FOR_FLIGHT;
                ticksInState = 0;
            }

            case WAITING_FOR_FLIGHT -> {
                if (mc.player.isFallFlying()) {
                    reset();
                } else if (ticksInState == 3) {
                    // Minecraft elytra flight requires a double-tap of jump while falling.
                    // Retry if the first tap didn't take.
                    mc.player.jump();
                } else if (ticksInState >= MAX_WAIT_TICKS) {
                    state = State.ERROR_TIMEOUT;
                }
            }

            // Terminal states, handled outside.
            case IDLE, ERROR_NO_ELYTRA, ERROR_TIMEOUT -> {}
        }
    }

    /**
     * Ensure the chest armor slot is an elytra. Searches the inventory for one
     * and swaps the previous chest content into the elytra's old slot.
     *
     * @return true if the chest is now equipped with an elytra (or already was).
     */
    private boolean equipElytraToChest() {
        ItemStack current = mc.player.getEquippedStack(CHEST);
        if (current.getItem() == Items.ELYTRA) return true;

        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() != Items.ELYTRA) continue;
            // Don't accidentally read the chest slot as an inventory slot
            // (PlayerInventory slots are global; see CHEST_SLOT_INDEX).
            if (slot == CHEST_SLOT_INDEX) continue;

            ItemStack chestBefore = current.copy();
            ItemStack elytra = stack.copy();

            mc.player.getInventory().setStack(slot, chestBefore);
            mc.player.equipStack(CHEST, elytra);
            mc.player.getInventory().markDirty();
            return true;
        }
        return false;
    }
}
