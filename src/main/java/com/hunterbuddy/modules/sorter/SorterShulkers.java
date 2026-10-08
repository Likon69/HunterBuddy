package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.regear.util.ShulkerDataParser;
import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class SorterShulkers {
    private SorterShulkers() {
    }

    static String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    public static ShulkerClassifier.Result classify(ItemStack stack, CategoryRules rules, double purity) {
        List<ShulkerClassifier.Slot> slots = new ArrayList<>();
        for (ItemStack inner : ShulkerDataParser.parseShulkerContentsAsList(stack)) {
            slots.add(new ShulkerClassifier.Slot(itemId(inner), inner.getCount()));
        }
        return ShulkerClassifier.classify(slots, rules, purity);
    }
}
