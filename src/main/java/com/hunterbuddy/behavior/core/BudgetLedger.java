package com.hunterbuddy.behavior.core;

import java.util.HashMap;
import java.util.Map;

public final class BudgetLedger {
    private final Map<String, Map<String, Map<String, Integer>>> owners = new HashMap<>();

    public boolean spend(String owner, String path, String name, String target, int max) {
        String budget = path + "#" + name;
        Map<String, Integer> counts = owners.computeIfAbsent(owner, ignored -> new HashMap<>())
            .computeIfAbsent(budget, ignored -> new HashMap<>());
        int spent = counts.getOrDefault(target, 0);
        if (spent >= max) return false;
        counts.put(target, spent + 1);
        return true;
    }

    public void reset(String owner, String name) {
        Map<String, Map<String, Integer>> budgets = owners.get(owner);
        if (budgets == null) return;
        if (name.equals("all")) budgets.clear();
        else budgets.keySet().removeIf(key -> key.endsWith("#" + name));
    }

    public void removeOwner(String owner) {
        owners.remove(owner);
    }
}
