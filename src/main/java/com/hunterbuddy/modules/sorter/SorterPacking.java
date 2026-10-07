package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Pos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import static meteordevelopment.meteorclient.MeteorClient.mc;

final class SorterPacking {
    private static final class Category {
        final String name;
        final List<Pos> chests = new ArrayList<>();
        long limit;
        long trips;
        boolean scanned;

        Category(String name) {
            this.name = name;
        }
    }

    private final ShulkerSorter owner;
    private final SorterInventory inventory;
    private final Map<Pos, List<ItemStack>> observations = new LinkedHashMap<>();
    private final List<Category> categories = new ArrayList<>();
    private final Set<Pos> visited = new HashSet<>();
    private int categoryIndex;
    private boolean started;
    private boolean finished;
    private boolean collecting;
    private boolean returning;
    private boolean noProgress;
    private PackingPlan plan;
    private List<ItemStack> space;
    private boolean takingShulker;

    SorterPacking(ShulkerSorter owner, SorterInventory inventory) {
        this.owner = owner;
        this.inventory = inventory;
    }

    void reset() {
        observations.clear();
        categories.clear();
        visited.clear();
        categoryIndex = 0;
        started = false;
        finished = false;
        collecting = false;
        returning = false;
        noProgress = false;
        plan = null;
        space = null;
        takingShulker = false;
    }

    void begin(SorterProfile profile) {
        started = true;
        Map<String, Category> grouped = new LinkedHashMap<>();
        for (SorterProfile.Destination destination : profile.destinations) {
            if (!CategoryRules.MIXED.equals(destination.category)) {
                Category category = grouped.computeIfAbsent(destination.category, Category::new);
                category.chests.addAll(destination.chests);
            }
        }
        for (Category category : grouped.values()) {
            Set<Pos> unique = new LinkedHashSet<>(category.chests);
            category.chests.clear();
            category.chests.addAll(unique);
            categories.add(category);
        }
    }

    void finish() {
        finished = true;
    }

    boolean started() {
        return started;
    }

    boolean inProgress() {
        return started && !finished;
    }

    String category() {
        return categoryIndex < categories.size() ? categories.get(categoryIndex).name : "";
    }

    PackingPlan plan() {
        return plan;
    }

    boolean returning() {
        return returning;
    }

    void observe(Pos pos, GenericContainerScreenHandler handler) {
        List<ItemStack> stacks = new ArrayList<>(handler.getRows() * 9);
        for (int i = 0; i < handler.getRows() * 9; i++) {
            stacks.add(handler.getSlot(i).getStack().copy());
        }
        observations.put(pos, stacks);
        if (started) {
            for (Category category : categories) {
                if (!category.chests.contains(pos) && SorterStore.get().destinationsFor(category.name).stream()
                    .anyMatch(destination -> destination.chests.contains(pos))) {
                    category.chests.add(pos);
                }
            }
        }
    }

    Pos nextChest(Set<Pos> excluded) {
        if (plan != null) {
            if (noProgress || inventory.freeCargoSlots() == 0 || PackingPlan.full(space)) {
                return null;
            }
            for (Pos pos : categories.get(categoryIndex).chests) {
                if (!excluded.contains(pos) && !visited.contains(pos) && looseStacks(pos, category()) > 0) {
                    visited.add(pos);
                    return pos;
                }
            }
            return null;
        }
        while (categoryIndex < categories.size()) {
            Category category = categories.get(categoryIndex);
            collecting = false;
            for (Pos pos : category.chests) {
                if (!excluded.contains(pos) && !observations.containsKey(pos)) {
                    return pos;
                }
            }
            if (!category.scanned) {
                category.scanned = true;
                category.limit = 1;
                for (Pos pos : category.chests) {
                    category.limit += looseStacks(pos, category.name);
                }
            }
            if (!noProgress && category.trips < category.limit) {
                for (Pos pos : category.chests) {
                    if (!excluded.contains(pos) && looseStacks(pos, category.name) > 0 && bestPartial(pos, category.name) >= 0) {
                        category.trips++;
                        collecting = true;
                        visited.clear();
                        visited.add(pos);
                        return pos;
                    }
                }
            }
            categoryIndex++;
            noProgress = false;
        }
        finished = true;
        return null;
    }

    boolean take(GenericContainerScreenHandler handler, Pos pos) {
        if (!collecting || noProgress) {
            return false;
        }
        if (!handler.getCursorStack().isEmpty()) {
            if (takingShulker) {
                int slot = SorterInventory.handlerSlotOf(handler.getRows() * 9, inventory.reservedSlot);
                if (!plan.matches(handler.getCursorStack()) || !handler.getSlot(slot).getStack().isEmpty()) {
                    return abort("Packing shulker or reserved slot changed");
                }
                click(handler, slot);
                takingShulker = false;
                return true;
            }
            return inventory.emptyCursor(handler);
        }
        if (takingShulker) {
            return abort("Packing shulker pickup changed");
        }
        if (plan == null) {
            int source = bestPartial(pos, category());
            int reserved = SorterInventory.handlerSlotOf(handler.getRows() * 9, inventory.reservedSlot);
            if (source < 0 || looseStacks(pos, category()) == 0) {
                return false;
            }
            if (!inventory.isCargoSlot(inventory.reservedSlot) || !handler.getSlot(reserved).getStack().isEmpty()) {
                return abort("Reserved hotbar slot changed");
            }
            ItemStack shulker = handler.getSlot(source).getStack();
            if (!PackingPlan.partial(shulker, category())) {
                return false;
            }
            plan = new PackingPlan(category(), shulker);
            space = plan.space();
            for (int inv = 0; inv < 36; inv++) {
                ItemStack cargo = mc.player.getInventory().getStack(inv);
                if (inv != inventory.reservedSlot && inventory.isCargoSlot(inv) && PackingPlan.loose(cargo, category())) {
                    PackingPlan.insert(space, cargo);
                }
            }
            takingShulker = true;
            click(handler, source);
            return true;
        }
        if (!plan.matches(mc.player.getInventory().getStack(inventory.reservedSlot))) {
            return abort("Packing shulker changed");
        }
        int destination = inventory.firstEmptyCargoSlot(handler);
        if (destination == -1 || PackingPlan.full(space)) {
            return false;
        }
        for (int source = 0; source < handler.getRows() * 9; source++) {
            ItemStack stack = handler.getSlot(source).getStack();
            if (!PackingPlan.loose(stack, category())) {
                continue;
            }
            int amount = PackingPlan.capacity(space, stack);
            if (amount == 0) {
                continue;
            }
            if (inventory.openingClicks + 2 > SorterInventory.clickLimit(handler)) {
                return false;
            }
            PackingPlan.insert(space, stack);
            click(handler, source);
            return true;
        }
        return false;
    }

    private void click(GenericContainerScreenHandler handler, int slot) {
        if (handler.getCursorStack().isEmpty()) inventory.cursorSourceSlot = slot;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        inventory.openingClicks++;
    }

    private boolean abort(String reason) {
        owner.warning("%s; returning packing cargo through normal routing.", reason);
        noProgress = true;
        takingShulker = false;
        return false;
    }

    boolean canFill() {
        if (plan == null || noProgress || !plan.matches(mc.player.getInventory().getStack(inventory.reservedSlot))) {
            return false;
        }
        for (int inv = 0; inv < 36; inv++) {
            ItemStack stack = mc.player.getInventory().getStack(inv);
            if (inv != inventory.reservedSlot && inventory.isCargoSlot(inv) && PackingPlan.loose(stack, category())
                && PackingPlan.capacity(plan.space(), stack) > 0) {
                return true;
            }
        }
        return false;
    }

    boolean completed(ItemStack stack, boolean success) {
        boolean progressed = success && plan != null && plan.progressed(stack);
        noProgress = !progressed;
        returning = true;
        takingShulker = false;
        return progressed;
    }

    void returned() {
        returning = false;
        plan = null;
        space = null;
        takingShulker = false;
        collecting = false;
    }

    private long looseStacks(Pos pos, String category) {
        return observations.getOrDefault(pos, List.of()).stream().filter(stack -> PackingPlan.loose(stack, category)).count();
    }

    private int bestPartial(Pos pos, String category) {
        List<ItemStack> stacks = observations.getOrDefault(pos, List.of());
        int best = -1;
        double filled = -1;
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (PackingPlan.partial(stack, category)) {
                double candidate = PackingPlan.fullness(PackingPlan.contents(stack));
                if (candidate > filled) {
                    best = i;
                    filled = candidate;
                }
            }
        }
        return best;
    }
}
