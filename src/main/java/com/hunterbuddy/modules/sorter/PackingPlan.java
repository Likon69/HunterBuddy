package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.collection.DefaultedList;

final class PackingPlan {
    private final String category;
    private final List<ItemStack> source;
    private final boolean emptySource;

    PackingPlan(String category, ItemStack shulker) {
        this.category = category;
        source = contents(shulker);
        emptySource = source.stream().allMatch(ItemStack::isEmpty);
    }

    String category() {
        return category;
    }

    List<ItemStack> space() {
        return copy(source);
    }

    boolean matches(ItemStack stack) {
        return ShulkerSorter.isShulker(stack) && matches(contents(stack));
    }

    boolean matches(List<ItemStack> stacks) {
        return accepts(stacks) && equal(source, stacks);
    }

    boolean accepts(List<ItemStack> stacks) {
        return pure(stacks, category) || emptySource && stacks.stream().allMatch(ItemStack::isEmpty);
    }

    boolean progressed(ItemStack stack) {
        List<ItemStack> after = contents(stack);
        return ShulkerSorter.isShulker(stack) && pure(after, category) && fullness(after) > fullness(source) + 1.0E-9;
    }

    static List<ItemStack> contents(ItemStack shulker) {
        DefaultedList<ItemStack> stacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
        shulker.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(stacks);
        return copy(stacks);
    }

    static List<ItemStack> contents(ScreenHandler handler) {
        List<ItemStack> stacks = new ArrayList<>(27);
        for (int i = 0; i < 27; i++) {
            stacks.add(handler.getSlot(i).getStack().copy());
        }
        return stacks;
    }

    static boolean loose(ItemStack stack, String category) {
        return !stack.isEmpty() && !ShulkerSorter.isShulker(stack) && stack.getItem().canBeNested()
            && category.equals(SorterStore.get().rules.categoryOf(Registries.ITEM.getId(stack.getItem()).toString()));
    }

    static boolean empty(ItemStack stack) {
        return ShulkerSorter.isShulker(stack) && contents(stack).stream().allMatch(ItemStack::isEmpty);
    }

    static boolean partial(ItemStack stack, String category) {
        if (!ShulkerSorter.isShulker(stack)) {
            return false;
        }
        List<ItemStack> stacks = contents(stack);
        return pure(stacks, category) && !full(stacks);
    }

    static boolean pure(List<ItemStack> stacks, String category) {
        if (CategoryRules.MIXED.equals(category)) {
            return false;
        }
        boolean occupied = false;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                occupied = true;
                if (!category.equals(SorterStore.get().rules.categoryOf(Registries.ITEM.getId(stack.getItem()).toString()))) {
                    return false;
                }
            }
        }
        return occupied;
    }

    static boolean full(List<ItemStack> stacks) {
        return stacks.size() == 27 && stacks.stream().allMatch(stack -> !stack.isEmpty() && stack.getCount() == stack.getMaxCount());
    }

    static double fullness(List<ItemStack> stacks) {
        double filled = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                filled += Math.min(1.0, (double) stack.getCount() / Math.max(1, stack.getMaxCount()));
            }
        }
        return filled;
    }

    static int capacity(List<ItemStack> stacks, ItemStack stack) {
        return insert(copy(stacks), stack);
    }

    static int insert(List<ItemStack> stacks, ItemStack stack) {
        int left = stack.getCount();
        if (stack.isStackable()) {
            for (ItemStack existing : stacks) {
                if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                    int moved = Math.min(left, Math.max(0, existing.getMaxCount() - existing.getCount()));
                    existing.increment(moved);
                    left -= moved;
                }
            }
        }
        for (int i = 0; i < stacks.size() && left > 0; i++) {
            if (stacks.get(i).isEmpty()) {
                int moved = Math.min(left, stack.getMaxCount());
                ItemStack added = stack.copy();
                added.setCount(moved);
                stacks.set(i, added);
                left -= moved;
            }
        }
        return stack.getCount() - left;
    }

    static boolean equal(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (!ItemStack.areEqual(first.get(i), second.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
    }
}
