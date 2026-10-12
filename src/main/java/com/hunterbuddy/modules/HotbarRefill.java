package com.hunterbuddy.modules;

import com.hunterbuddy.util.QuietClicks;
import com.hunterbuddy.util.Rockets;
import com.hunterbuddy.HunterBuddyAddon;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class HotbarRefill extends Module {
    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_FIRST = 9;
    private static final int MAIN_LAST = 35;

    public enum SourcePick {
        FirstMatch,
        FullStacks,
        SmallStacks;

        @Override
        public String toString() {
            return switch (this) {
                case FirstMatch -> "First Match";
                case FullStacks -> "Full Stacks";
                case SmallStacks -> "Small Stacks";
            };
        }
    }

    private final SettingGroup sgMain = settings.getDefaultGroup();
    private final SettingGroup sgKinds = settings.createGroup("Items");
    private final SettingGroup sgExtra = settings.createGroup("Advanced");

    private final Setting<Integer> topUpAt = sgMain.add(new IntSetting.Builder()
        .name("threshold")
        .description("A hotbar stack at or below this count gets topped up.")
        .defaultValue(8)
        .min(1)
        .max(63)
        .sliderMin(1)
        .sliderMax(63)
        .build()
    );

    private final Setting<Integer> pauseTicks = sgMain.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Ticks to wait after a top-up before the next one.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderMin(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> waitWhileUsing = sgMain.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Wait while you are using an item, such as eating or drawing a bow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lastItemTrigger = sgMain.add(new BoolSetting.Builder()
        .name("smart-refill")
        .description("Instead of the threshold, top up only when a stack has just dropped to its last item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SourcePick> sourcePick = sgMain.add(new EnumSetting.Builder<SourcePick>()
        .name("stack-preference")
        .description("Which inventory stack is used: the first found, the biggest, or the smallest.")
        .defaultValue(SourcePick.SmallStacks)
        .build()
    );

    private final Setting<Boolean> wantBlocks = sgKinds.add(new BoolSetting.Builder()
        .name("blocks")
        .description("Blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantFood = sgKinds.add(new BoolSetting.Builder()
        .name("food")
        .description("Anything edible.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantTools = sgKinds.add(new BoolSetting.Builder()
        .name("tools")
        .description("Pickaxes, axes, shovels and hoes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> wantWeapons = sgKinds.add(new BoolSetting.Builder()
        .name("weapons")
        .description("Swords, bows and crossbows.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> wantProjectiles = sgKinds.add(new BoolSetting.Builder()
        .name("projectiles")
        .description("Arrows and firework rockets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantPearls = sgKinds.add(new BoolSetting.Builder()
        .name("ender-pearls")
        .description("Ender pearls.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantPotions = sgKinds.add(new BoolSetting.Builder()
        .name("potions")
        .description("Potions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantTotems = sgKinds.add(new BoolSetting.Builder()
        .name("totems")
        .description("Totems of undying.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantApples = sgKinds.add(new BoolSetting.Builder()
        .name("golden-apples")
        .description("Golden and enchanted golden apples.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantRockets = sgKinds.add(new BoolSetting.Builder()
        .name("fireworks")
        .description("Firework rockets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shiftMove = sgExtra.add(new BoolSetting.Builder()
        .name("use-shift-click")
        .description("Move the stack with a shift-click instead of picking it up and placing it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instantMove = sgExtra.add(new BoolSetting.Builder()
        .name("silent-refill")
        .description("Send the shift-click right away instead of queueing it for a quiet moment.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> perTickLimit = sgExtra.add(new IntSetting.Builder()
        .name("max-refills-per-tick")
        .description("Most top-ups done in one tick.")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> keepNames = sgExtra.add(new BoolSetting.Builder()
        .name("respect-custom-names")
        .description("A renamed stack is only topped up with the same name until it is down to its last item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> anyStackable = sgExtra.add(new BoolSetting.Builder()
        .name("refill-all-stackable")
        .description("Top up every stackable item, whatever the boxes above say.")
        .defaultValue(true)
        .build()
    );

    private final Integer[] lastCounts = new Integer[HOTBAR_SIZE];
    private final String[] trackedNames = new String[HOTBAR_SIZE];
    private final Deque<Integer> pendingMoves = new ArrayDeque<>();
    private int waitTicks;

    public HotbarRefill() {
        super(HunterBuddyAddon.LOGISTICS_CATEGORY, "hotbar-refill", "Tops up hotbar stacks from your inventory before they run out.", "replenish");
    }

    @Override
    public void onActivate() {
        java.util.Arrays.fill(lastCounts, null);
        java.util.Arrays.fill(trackedNames, null);
        pendingMoves.clear();
        waitTicks = 0;
    }

    @Override
    public String getInfoString() {
        int queued = pendingMoves.size();
        return queued > 0 ? "Refilling (" + queued + ")" : null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (waitWhileUsing.get() && mc.player.isUsingItem()) return;

        if (!pendingMoves.isEmpty()) {
            processQueue();
            return;
        }

        scanHotbar();
    }

    private void processQueue() {
        if (!inPlayerMenu()) return;
        if (!QuietClicks.readyOrSettle()) return;

        int done = 0;
        while (done < perTickLimit.get() && !pendingMoves.isEmpty()) {
            quickMove(pendingMoves.poll());
            done++;
        }

        if (done > 0) waitTicks = pauseTicks.get();
    }

    private void scanHotbar() {
        int refills = 0;
        int limit = perTickLimit.get();

        for (int h = 0; h < HOTBAR_SIZE && refills < limit; h++) {
            ItemStack stack = mc.player.getInventory().getStack(h);

            if (stack.isEmpty()) {
                trackedNames[h] = null;
                continue;
            }

            if (!isConcerned(stack)) continue;

            if (wantsRefill(h, stack)) {
                if (tryRefill(h, stack)) refills++;
                else if (lastItemTrigger.get()) lastCounts[h] = stack.getCount() + 1;
            }
        }

        if (refills > 0) waitTicks = pauseTicks.get();
    }

    private boolean wantsRefill(int h, ItemStack stack) {
        int count = stack.getCount();

        if (lastItemTrigger.get()) {
            Integer previous = lastCounts[h];
            boolean dropped = previous != null && previous > count && count <= 1;
            lastCounts[h] = count;
            return dropped;
        }

        return count <= topUpAt.get() && count < stack.getMaxCount();
    }

    private boolean isConcerned(ItemStack stack) {
        Item item = stack.getItem();
        String path = Registries.ITEM.getId(item).getPath();

        if (anyStackable.get() && stack.getMaxCount() > 1) return true;
        if (wantTotems.get() && item == Items.TOTEM_OF_UNDYING) return true;
        if (wantPearls.get() && item == Items.ENDER_PEARL) return true;
        if (wantApples.get() && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) return true;
        if (wantRockets.get() && item == Items.FIREWORK_ROCKET) return true;
        if (wantBlocks.get() && item instanceof BlockItem) return true;
        if (wantFood.get() && stack.contains(DataComponentTypes.FOOD)) return true;
        if (wantTools.get() && isTool(item, path)) return true;
        if (wantWeapons.get() && isWeapon(item, path)) return true;
        if (wantProjectiles.get() && (item instanceof ArrowItem || item == Items.FIREWORK_ROCKET)) return true;
        return wantPotions.get() && item instanceof PotionItem;
    }

    private boolean isTool(Item item, String path) {
        return item instanceof ShovelItem || item instanceof AxeItem || item instanceof HoeItem || path.contains("pickaxe");
    }

    private boolean isWeapon(Item item, String path) {
        return path.contains("sword") || item instanceof BowItem || item instanceof CrossbowItem;
    }

    private boolean tryRefill(int h, ItemStack stack) {
        int source = findSource(stack);

        if (keepNames.get() && stack.getMaxCount() > 1) {
            String current = displayName(stack);
            if (trackedNames[h] == null) trackedNames[h] = current;

            if (source == -1) {
                trackedNames[h] = null;
                return false;
            }

            String sourceName = displayName(mc.player.getInventory().getStack(source));
            if (!sourceName.equals(trackedNames[h])) {
                if (stack.getCount() > 1) return false;
                trackedNames[h] = sourceName;
            }
        }

        if (source == -1) return false;

        return moveToHotbar(source, h);
    }

    private boolean moveToHotbar(int source, int h) {
        if (!shiftMove.get()) {
            if (!QuietClicks.readyOrSettle()) return false;
            InvUtils.move().from(source).to(h);
            return true;
        }

        if (!inPlayerMenu()) return false;

        if (instantMove.get()) {
            if (!QuietClicks.readyOrSettle()) return false;
            quickMove(source);
        } else {
            pendingMoves.add(source);
        }

        return true;
    }

    private void quickMove(int source) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, source, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    private boolean inPlayerMenu() {
        return mc.player.currentScreenHandler == mc.player.playerScreenHandler;
    }

    private String displayName(ItemStack stack) {
        Text custom = stack.get(DataComponentTypes.CUSTOM_NAME);
        return custom != null ? custom.getString() : stack.getItem().getName().getString();
    }

    private int findSource(ItemStack target) {
        int best = -1;

        for (int i = MAIN_FIRST; i <= MAIN_LAST; i++) {
            ItemStack candidate = mc.player.getInventory().getStack(i);
            if (!isCandidate(target, candidate)) continue;

            if (best == -1) {
                best = i;
                if (sourcePick.get() == SourcePick.FirstMatch) break;
                continue;
            }

            int bestCount = mc.player.getInventory().getStack(best).getCount();
            if (isBetter(candidate.getCount(), bestCount)) best = i;
        }

        return best;
    }

    private boolean isBetter(int count, int bestCount) {
        return switch (sourcePick.get()) {
            case FullStacks -> count > bestCount;
            case SmallStacks -> count < bestCount;
            case FirstMatch -> false;
        };
    }

    private boolean isCandidate(ItemStack target, ItemStack candidate) {
        if (candidate.isEmpty() || candidate.getItem() != target.getItem()) return false;
        if (target.isOf(Items.FIREWORK_ROCKET) && Rockets.isPlain(target) && !Rockets.isPlain(candidate)) return false;
        if (target.getMaxCount() <= 1) return true;
        return !keepNames.get() || ItemStack.areItemsAndComponentsEqual(target, candidate);
    }
}
