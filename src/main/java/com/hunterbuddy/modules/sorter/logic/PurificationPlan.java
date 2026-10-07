package com.hunterbuddy.modules.sorter.logic;

import java.util.ArrayList;
import java.util.List;

public record PurificationPlan(String retainedCategory, List<Integer> extractionSlots) {
    public PurificationPlan {
        extractionSlots = List.copyOf(extractionSlots);
    }

    public int requiredCargoSlots() {
        return extractionSlots.size();
    }

    public static PurificationPlan create(List<ShulkerClassifier.Slot> slots, CategoryRules rules) {
        String retained = ShulkerClassifier.classify(slots, rules, 0.0).category();
        List<Integer> extraction = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ShulkerClassifier.Slot slot = slots.get(i);
            if (slot != null && slot.count() > 0 && !retained.equals(rules.categoryOf(slot.itemId()))) {
                extraction.add(i);
            }
        }
        return new PurificationPlan(retained, extraction);
    }
}
