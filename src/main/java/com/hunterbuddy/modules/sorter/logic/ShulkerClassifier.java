package com.hunterbuddy.modules.sorter.logic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class ShulkerClassifier {
    private ShulkerClassifier() {
    }


    public record Slot(String itemId, int count) {
    }

    public record Result(String category, double purity, Map<String, Integer> slotsByCategory, boolean empty) {
        public boolean isMixed() {
            return CategoryRules.MIXED.equals(category);
        }


        public String describe() {
            if (empty) {
                return "empty";
            }
            String percent = Math.round(purity * 100) + "%";
            if (!isMixed()) {
                return category + " " + percent;
            }
            StringBuilder sb = new StringBuilder("mixed (");
            int total = slotsByCategory.values().stream().mapToInt(Integer::intValue).sum();
            int shown = 0;
            for (Map.Entry<String, Integer> e : slotsByCategory.entrySet()) {
                if (shown++ == 3) {
                    sb.append("...");
                    break;
                }
                if (shown > 1) {
                    sb.append(", ");
                }
                sb.append(e.getKey()).append(' ').append(Math.round(100.0 * e.getValue() / total)).append('%');
            }
            return sb.append(')').toString();
        }
    }


    public static Result classify(List<Slot> slots, CategoryRules rules, double purityThreshold) {
        if (slots == null || slots.isEmpty()) {
            return new Result(CategoryRules.SHULKERS, 1.0, Collections.emptyMap(), true);
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : slots) {
            if (slot == null || slot.count() <= 0) {
                continue;
            }
            counts.merge(rules.categoryOf(slot.itemId()), 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return new Result(CategoryRules.SHULKERS, 1.0, Collections.emptyMap(), true);
        }

        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map.Entry<String, Integer> top = sorted.entrySet().iterator().next();
        double purity = (double) top.getValue() / total;
        String category = purity >= purityThreshold ? top.getKey() : CategoryRules.MIXED;
        return new Result(category, purity, sorted, false);
    }
}
