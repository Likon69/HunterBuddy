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

    private static final class Transfer {
        final GenericContainerScreenHandler handler;
        final int source;
        final int destination;
        final ItemStack stack;
        final int amount;
        int placed;

        Transfer(GenericContainerScreenHandler handler, int source, int destination, ItemStack stack, int amount) {
            this.handler = handler;
            this.source = source;
            this.destination = destination;
            this.stack = stack.copy();
            this.amount = amount;
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
    private Transfer transfer;

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
        transfer = null;
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
        if (transfer != null) {
            return transfer(handler);
        }
        if (!handler.getCursorStack().isEmpty()) {
            return abort("Cursor changed while packing");
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
            transfer = new Transfer(handler, source, reserved, shulker, shulker.getCount());
            click(handler, source, 0);
            return true;
        }
        if (!plan.matches(mc.player.getInventory().getStack(inventory.reservedSlot))) {
            return abort("Packing shulker changed");
        }
        int destination = inventory.firstEmptyCargoSlot(handler);
        if (destination == -1) {
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
            int clicks = amount == stack.getCount() ? 2 : amount + 2;
            if (inventory.openingClicks + clicks > SorterInventory.clickLimit(handler)) {
                return false;
            }
            ItemStack admitted = stack.copy();
            admitted.setCount(amount);
            PackingPlan.insert(space, admitted);
            transfer = new Transfer(handler, source, destination, stack, amount);
            click(handler, source, 0);
            return true;
        }
        return false;
    }

    private boolean transfer(GenericContainerScreenHandler handler) {
        if (transfer.handler != handler) {
            return abort("Packing screen changed");
        }
        ItemStack cursor = handler.getCursorStack();
        ItemStack destination = handler.getSlot(transfer.destination).getStack();
        if (cursor.isEmpty() || !ItemStack.areItemsAndComponentsEqual(cursor, transfer.stack)
            || cursor.getCount() != transfer.stack.getCount() - transfer.placed
            || (transfer.placed == 0 ? !destination.isEmpty()
                : !ItemStack.areItemsAndComponentsEqual(destination, transfer.stack) || destination.getCount() != transfer.placed)) {
            return abort("Server changed packing transfer");
        }
        if (transfer.placed == transfer.amount) {
            ItemStack source = handler.getSlot(transfer.source).getStack();
            if (!source.isEmpty()) {
                return abort("Packing source slot changed");
            }
            click(handler, transfer.source, 0);
            transfer = null;
        } else if (transfer.amount == transfer.stack.getCount()) {
            click(handler, transfer.destination, 0);
            transfer = null;
        } else {
            click(handler, transfer.destination, 1);
            transfer.placed++;
        }
        return true;
    }

    private void click(GenericContainerScreenHandler handler, int slot, int button) {
        inventory.cursorSourceSlot = transfer == null ? slot : transfer.source;
        mc.interactionManager.clickSlot(handler.syncId, slot, button, SlotActionType.PICKUP, mc.player);
        inventory.openingClicks++;
    }

    private boolean abort(String reason) {
        owner.warning("%s; returning packing cargo through normal routing.", reason);
        noProgress = true;
        transfer = null;
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
        transfer = null;
        return progressed;
    }

    void returned() {
        returning = false;
        plan = null;
        space = null;
        transfer = null;
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
