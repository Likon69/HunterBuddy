package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

public class ElytraSwap extends Module {
    private static final int INVENTORY_LAST = 35;
    private static final int HOTBAR_LAST = 8;
    private static final int MAX_FAILURES = 5;
    private static final int ELYTRA_MATCH_DAMAGE = 5;

    private final SettingGroup sgMain = settings.getDefaultGroup();
    private final SettingGroup sgGuard = settings.createGroup("Combat Protection");

    private final Setting<Integer> wornAt = sgMain.add(new IntSetting.Builder()
        .name("Durability Threshold")
        .description("Swap the worn elytra once its durability is at or below this percentage.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> flyingOnly = sgMain.add(new BoolSetting.Builder()
        .name("Only While Flying")
        .description("Only swap a worn-out elytra while gliding.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> holdInMenus = sgMain.add(new BoolSetting.Builder()
        .name("Pause In Inventory")
        .description("Hold every swap while a container or other menu is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> restTicks = sgMain.add(new IntSetting.Builder()
        .name("Swap Cooldown")
        .description("Ticks to wait after a durability swap before checking again.")
        .defaultValue(100)
        .min(20)
        .max(200)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Integer> stepTicks = sgMain.add(new IntSetting.Builder()
        .name("Stage Delay")
        .description("Ticks between two steps of a swap; higher is gentler on the anticheat.")
        .defaultValue(8)
        .min(3)
        .max(20)
        .sliderRange(3, 20)
        .build()
    );

    private final Setting<Boolean> chat = sgMain.add(new BoolSetting.Builder()
        .name("Notify Swap")
        .description("Say in chat what was swapped, and why a swap failed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> guardOnHit = sgGuard.add(new BoolSetting.Builder()
        .name("Swap On Hit")
        .description("Put a chestplate on when something hits you on the ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> guardTicks = sgGuard.add(new IntSetting.Builder()
        .name("Protection Duration")
        .description("Ticks the chestplate stays on after the last hit.")
        .defaultValue(60)
        .min(20)
        .max(200)
        .sliderRange(20, 200)
        .visible(guardOnHit::get)
        .build()
    );

    private final Setting<Boolean> restoreElytra = sgGuard.add(new BoolSetting.Builder()
        .name("Auto Swap Back")
        .description("Put the elytra back on once the protection time is over.")
        .defaultValue(true)
        .visible(guardOnHit::get)
        .build()
    );

    private final Setting<Boolean> netheriteFirst = sgGuard.add(new BoolSetting.Builder()
        .name("Prioritize Netherite")
        .description("Rank netherite chestplates above diamond ones.")
        .defaultValue(true)
        .visible(guardOnHit::get)
        .build()
    );

    private int cooldown;
    private boolean wearActive;
    private int wearStage;
    private int wearTimer;
    private int wearSlot = -1;

    private boolean guarding;
    private int guardLeft;
    private int lastHurt;
    private boolean missingSaid;
    private ItemStack parkedElytra = ItemStack.EMPTY;

    private boolean chestActive;
    private int chestStage;
    private int chestTimer;
    private int chestFailures;
    private int chestSlot = -1;

    private boolean hold;
    private boolean holdTried;

    public ElytraSwap() {
        super(HunterBuddyAddon.LOGISTICS_CATEGORY, "elytra-keeper", "Keeps a working elytra on: swaps in a fresh one when it wears out, puts one on a bare chest, and can wear a chestplate while you are hit or on demand.", "ElytraSwap");
    }

    @Override
    public void onActivate() {
        resetAll();
    }

    @Override
    public void onDeactivate() {
        resetAll();
    }

    public void setChestplateHold(boolean value) {
        if (value == hold) return;
        hold = value;
        holdTried = false;
        if (!value && guarding && guardOnHit.get() && restoreElytra.get()) {
            guardLeft = 0;
        }
    }

    private void resetAll() {
        cooldown = 0;
        resetWear();
        guarding = false;
        guardLeft = 0;
        lastHurt = 0;
        parkedElytra = ItemStack.EMPTY;
        chestActive = false;
        chestStage = 0;
        chestTimer = 0;
        chestFailures = 0;
        chestSlot = -1;
        hold = false;
        holdTried = false;
    }

    private void resetWear() {
        wearActive = false;
        wearStage = 0;
        wearTimer = 0;
        wearSlot = -1;
    }

    private static double percent(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) return 100.0;
        int max = stack.getMaxDamage();
        return (max - stack.getDamage()) / (double) max * 100.0;
    }

    private static boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.ELYTRA;
    }

    private static boolean isWearable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.ELYTRA
            || item == Items.NETHERITE_CHESTPLATE
            || item == Items.DIAMOND_CHESTPLATE
            || item == Items.IRON_CHESTPLATE
            || item == Items.GOLDEN_CHESTPLATE
            || item == Items.CHAINMAIL_CHESTPLATE
            || item == Items.LEATHER_CHESTPLATE;
    }

    private int value(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        int left = stack.getMaxDamage() - stack.getDamage();
        if (item == Items.NETHERITE_CHESTPLATE) return (netheriteFirst.get() ? 1000 : 400) + left;
        if (item == Items.DIAMOND_CHESTPLATE) return 300 + left;
        if (item == Items.IRON_CHESTPLATE) return 200 + left;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 150 + left;
        if (item == Items.GOLDEN_CHESTPLATE) return 100 + left;
        if (item == Items.LEATHER_CHESTPLATE) return 50 + left;
        return 0;
    }

    private int bestChestplate() {
        int best = -1;
        int bestValue = 0;
        for (int i = 0; i <= INVENTORY_LAST; i++) {
            int v = value(mc.player.getInventory().getStack(i));
            if (v > bestValue) {
                bestValue = v;
                best = i;
            }
        }
        return best;
    }

    private static boolean isEssential(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.TOTEM_OF_UNDYING
            || item == Items.GOLDEN_APPLE
            || item == Items.ENCHANTED_GOLDEN_APPLE
            || item == Items.ENDER_PEARL
            || item == Items.CHORUS_FRUIT;
    }

    private boolean menuPaused() {
        return holdInMenus.get() && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
    }

    private ItemStack chest() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST);
    }

    private int bestElytraAbove(double floor) {
        int best = -1;
        double bestPercent = 0;
        for (int i = 0; i <= INVENTORY_LAST; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isElytra(stack)) continue;
            double p = percent(stack);
            if (p > floor && (best == -1 || p > bestPercent)) {
                best = i;
                bestPercent = p;
            }
        }
        return best;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (guardOnHit.get()) tickProtection();

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (menuPaused()) {
            resetWear();
            return;
        }

        if (guarding) return;

        ItemStack worn = chest();
        if (isElytra(worn)) {
            if (flyingOnly.get() && !mc.player.isGliding()) return;
            if (wearActive) {
                advanceWear();
            } else if (percent(worn) <= wornAt.get()) {
                startWear(worn);
            }
        } else if (wearActive) {
            advanceWear();
        } else if (worn.isEmpty()) {
            wearOnBareChest();
        }
    }

    private void startWear(ItemStack worn) {
        int slot = bestElytraAbove(Math.max(percent(worn), wornAt.get()));
        if (slot != -1) {
            wearActive = true;
            wearStage = 1;
            wearTimer = 0;
            wearSlot = slot;
            return;
        }
        if (chat.get()) {
            warning("No better elytra in the inventory (worn one at %.1f%%, threshold %d%%)", percent(worn), wornAt.get());
            cooldown = restTicks.get();
        }
    }

    private void wearOnBareChest() {
        int slot = bestElytraAbove(wornAt.get());
        if (slot == -1) return;
        wearActive = true;
        wearStage = 1;
        wearTimer = 0;
        wearSlot = slot;
        if (chat.get()) {
            info("Bare chest, putting on the elytra at %.1f%% from slot %d", percent(mc.player.getInventory().getStack(slot)), slot);
        }
    }

    private void advanceWear() {
        wearTimer++;
        if (wearTimer < stepTicks.get()) return;

        if (wearStage == 1) {
            if (!isElytra(mc.player.getInventory().getStack(wearSlot))) {
                if (chat.get()) warning("Elytra swap failed: the fresh elytra left slot %d", wearSlot);
                resetAll();
                return;
            }
            InvUtils.move().from(wearSlot).toArmor(2);
            wearStage = 2;
            wearTimer = 0;
            return;
        }

        if (wearStage == 2) {
            if (chat.get()) {
                ItemStack now = chest();
                if (isElytra(now) && percent(now) > wornAt.get()) {
                    info("Now wearing an elytra at %.1f%%, the worn one is in slot %d", percent(now), wearSlot);
                } else {
                    warning("Elytra swap failed: the chest slot did not take the fresh elytra");
                }
            }
            resetWear();
            cooldown = restTicks.get();
        }
    }

    private void tickProtection() {
        int hurt = mc.player.hurtTime;
        boolean freshHit = hurt > 0 && hurt > lastHurt;
        if (freshHit) lastHurt = hurt;

        if (freshHit && (guarding || (mc.player.isOnGround() && !mc.player.isGliding()))) {
            if (isElytra(chest()) && !guarding) {
                putChestplateOn();
            } else if (guarding) {
                guardLeft = guardTicks.get();
            }
        }

        if (hurt < lastHurt) lastHurt = hurt;

        if (hold && restoreElytra.get()) {
            if (!guarding && !holdTried && !mc.player.isGliding() && isElytra(chest())) {
                holdTried = true;
                putChestplateOn();
            }
            if (guarding) guardLeft = guardTicks.get();
        }

        if (chestActive) {
            if (!menuPaused()) advanceChest();
            return;
        }

        if (guarding) {
            guardLeft--;
            if (guardLeft <= 0 && restoreElytra.get() && !menuPaused()) {
                finishProtection();
            }
        }
    }

    private void putChestplateOn() {
        int slot = bestChestplate();
        if (slot == -1) return;
        parkedElytra = chest().copy();
        startChestSwap(slot);
        guarding = true;
        guardLeft = guardTicks.get();
        if (chat.get()) info("Hit, putting the chestplate on.");
    }

    private void finishProtection() {
        if (!isElytra(chest()) && !parkedElytra.isEmpty()) {
            int slot = findReplacementElytra();
            if (slot != -1) {
                missingSaid = false;
                startChestSwap(slot);
                if (chat.get()) info("Protection over, putting the elytra back on.");
            } else if (!missingSaid) {
                missingSaid = true;
                warning("No elytra in the inventory to put back on, waiting for one.");
            }
        } else {
            guarding = false;
            parkedElytra = ItemStack.EMPTY;
        }
    }

    private int findReplacementElytra() {
        int first = -1;
        for (int i = 0; i <= INVENTORY_LAST; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isElytra(stack)) continue;
            if (Math.abs(stack.getDamage() - parkedElytra.getDamage()) <= ELYTRA_MATCH_DAMAGE) return i;
            if (first == -1) first = i;
        }
        return first;
    }

    private void startChestSwap(int slot) {
        chestActive = true;
        chestStage = 1;
        chestTimer = 0;
        chestFailures = 0;
        chestSlot = slot;
    }

    private void advanceChest() {
        chestTimer++;
        if (chestTimer < stepTicks.get()) return;

        if (chestStage == 1) {
            if (chestSlot > HOTBAR_LAST) {
                int hotbar = freeHotbarSlot();
                if (hotbar == -1) {
                    failChestSwap("Chestplate swap failed: no free hotbar slot (attempt %d)");
                    return;
                }
                InvUtils.move().from(chestSlot).toHotbar(hotbar);
                chestSlot = hotbar;
            }
            chestStage = 2;
            chestTimer = 0;
            return;
        }

        if (chestStage == 2) {
            if (!isWearable(mc.player.getInventory().getStack(chestSlot))) {
                failChestSwap("Chestplate swap failed: the item left the hotbar slot (attempt %d)");
                return;
            }
            InvUtils.swap(chestSlot, true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.swapBack();
            chestStage = 3;
            chestTimer = 0;
            return;
        }

        chestActive = false;
        chestStage = 0;
        chestTimer = 0;
        chestFailures = 0;
        chestSlot = -1;
        if (isElytra(chest())) {
            guarding = false;
            parkedElytra = ItemStack.EMPTY;
        }
    }

    private void failChestSwap(String message) {
        chestActive = false;
        chestStage = 0;
        chestFailures++;
        if (chat.get()) warning(message, chestFailures);
        if (chestFailures >= MAX_FAILURES) {
            guarding = false;
            parkedElytra = ItemStack.EMPTY;
            chestFailures = 0;
            if (chat.get()) warning("Giving up on the chestplate swap after %d failed attempts.", MAX_FAILURES);
        }
    }

    private int freeHotbarSlot() {
        int best = -1;
        int bestRank = 0;
        for (int i = 0; i <= HOTBAR_LAST; i++) {
            int rank = hotbarRank(mc.player.getInventory().getStack(i));
            if (rank > bestRank) {
                bestRank = rank;
                best = i;
            }
        }
        return best;
    }

    private static int hotbarRank(ItemStack stack) {
        if (stack.isEmpty()) return 3;
        if (stack.getItem() == Items.OBSIDIAN) return 2;
        boolean protectedItem = isEssential(stack)
            || stack.isIn(ItemTags.SWORDS)
            || stack.isIn(ItemTags.AXES)
            || stack.getItem() == Items.MACE
            || stack.getItem() == Items.TRIDENT
            || stack.isIn(ItemTags.PICKAXES)
            || stack.getItem() == Items.FIREWORK_ROCKET;
        return protectedItem ? 0 : 1;
    }
}
