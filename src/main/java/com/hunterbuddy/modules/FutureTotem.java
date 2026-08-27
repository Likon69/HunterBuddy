package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Keeps a totem of undying in the off hand, the way Future 2.9 did it.
 *
 * <p>Ported from {@code combat/autoTotem} rather than reinvented, because the
 * shape of that code is the good part: it holds almost no state. Every tick it
 * looks at where the cursor is and what the off hand holds, and from that alone
 * decides which single click comes next. A click that never arrives, a slot the
 * server moved under it, a screen opened halfway through -- none of it needs
 * handling, because the next tick asks the same question again and gets the right
 * answer for the new situation. A state machine that remembered "I am on step two"
 * would be the version that gets stuck.
 *
 * <p>The three clicks are the ordinary swap: take the totem, drop it on the off
 * hand slot, put whatever came out back where the totem was. With the delay at
 * zero all three run in the same tick, which is what makes it instant; the delay
 * exists for anyone who wants the clicks spread out.
 *
 * <p>Two things were tightened on the way across. Future started the sequence
 * whenever the cursor was not already holding a totem, which meant it would grab
 * an item you were dragging; here the cursor has to be empty. And the container
 * check is on the screen handler rather than the screen class, because in this
 * version an open chest renumbers every slot -- clicking slot 45 with a shulker
 * open is not the off hand, it is somebody's storage.
 */
public class FutureTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("Seconds between the three clicks of the swap, and before the first one. Zero puts all three in one tick, which is the point of the module; anything above delays the replacement itself and risks being caught mid-swap with the totem on your cursor.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderRange(0.0, 2.0)
        .build()
    );

    private final Setting<Boolean> hotbarFirst = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar-first")
        .description("Take the totem nearest the hotbar rather than the first one in the inventory. It empties the stacks you can see before the ones you cannot, and it is what the original did by scanning the container backwards.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseInCreative = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-creative")
        .description("Do nothing in creative, where a totem is not what stands between you and the respawn screen.")
        .defaultValue(true)
        .build()
    );

    /** Player screen handler layout: 0 result, 1-4 craft, 5-8 armour, 9-35 main, 36-44 hotbar, 45 off hand. */
    private static final int FIRST_SLOT = 9;
    private static final int LAST_SLOT = 44;
    private static final int OFFHAND_SLOT = 45;

    private long startedMs;
    private boolean busy;
    private int fromSlot = -1;
    private boolean warnedFull;

    public FutureTotem() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "f-totem",
            "Keeps a totem in your off hand, replaced the moment it is used.");
    }

    @Override
    public void onActivate() {
        reset();
        warnedFull = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!mc.player.isAlive()) return;
        if (pauseInCreative.get() && mc.player.isCreative()) return;

        // Any container other than your own inventory renumbers the slots, so the
        // indices below would point at somebody else's chest.
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            reset();
            return;
        }

        // Your own inventory is playerScreenHandler too, so the check above lets it
        // through -- and the module then clicks slots on the same ticks you are
        // clicking them yourself, which reads as an inventory that refuses to let
        // anything be moved. Only slot-carrying screens count: the pause menu and
        // the chat box have no slots to fight over, and standing down for them
        // would leave you unarmed for no reason. A swap already under way still
        // finishes, or its item would stay stuck to the pointer.
        if (mc.currentScreen instanceof HandledScreen<?> && fromSlot == -1) return;

        ScreenHandler handler = mc.player.playerScreenHandler;
        ItemStack cursor = handler.getCursorStack();

        // Armed and empty handed is the finished state, and the only one worth
        // leaving on. Armed with something still in the cursor is the middle of a
        // swap, not the end of one -- the original bailed out here and left the
        // displaced item stuck to the pointer for the rest of the session.
        if (armed() && cursor.isEmpty()) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (!busy) {
            busy = true;
            startedMs = now;
        }

        long since = now - startedMs;
        long step = (long) (delay.get() * 1000.0);

        // Three clicks, each one re-reading the cursor the previous one changed.
        // The client applies a click locally as it sends it, so at a delay of zero
        // all three conditions come true in this single pass and the swap is
        // instant; above zero the pass falls through and the next tick picks up
        // wherever this one stopped.
        if (cursor.isEmpty() && !armed() && since >= step) {
            int source = findTotem();

            if (source == -1) {
                reset();
                return;
            }

            fromSlot = source;
            click(source);
            cursor = handler.getCursorStack();
        }

        if (cursor.getItem() == Items.TOTEM_OF_UNDYING && !armed() && since >= step * 2) {
            click(OFFHAND_SLOT);
            cursor = handler.getCursorStack();

            if (cursor.isEmpty()) {
                reset();
                return;
            }
        }

        if (!cursor.isEmpty() && armed() && since >= step * 3) {
            int back = putBackSlot();

            if (back == -1) {
                if (!warnedFull) {
                    warnedFull = true;
                    HunterBuddyAddon.LOG.warn("FutureTotem: nowhere to put {} back, it stays on the cursor",
                        cursor.getItem());
                }

                return;
            }

            warnedFull = false;
            click(back);
            reset();
        }
    }

    private boolean armed() {
        return mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;
    }

    /** Where the item that came out of the off hand goes: its own slot if it is still free, else any. */
    private int putBackSlot() {
        if (fromSlot != -1 && mc.player.playerScreenHandler.getSlot(fromSlot).getStack().isEmpty()) {
            return fromSlot;
        }

        for (int i = FIRST_SLOT; i <= LAST_SLOT; i++) {
            if (mc.player.playerScreenHandler.getSlot(i).getStack().isEmpty()) return i;
        }

        return -1;
    }

    private void reset() {
        busy = false;
        startedMs = 0;
        fromSlot = -1;
    }

    private void click(int slot) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 0,
            SlotActionType.PICKUP, mc.player);
    }

    private int findTotem() {
        if (hotbarFirst.get()) {
            for (int i = LAST_SLOT; i >= FIRST_SLOT; i--) {
                if (isTotem(i)) return i;
            }

            return -1;
        }

        for (int i = FIRST_SLOT; i <= LAST_SLOT; i++) {
            if (isTotem(i)) return i;
        }

        return -1;
    }

    private boolean isTotem(int slot) {
        return mc.player.playerScreenHandler.getSlot(slot).getStack().getItem() == Items.TOTEM_OF_UNDYING;
    }

    /** How many totems are left, counting the one in your off hand. */
    public int count() {
        if (mc.player == null) return 0;

        int total = 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) total++;
        }

        return total;
    }

    @Override
    public String getInfoString() {
        return String.valueOf(count());
    }
}
