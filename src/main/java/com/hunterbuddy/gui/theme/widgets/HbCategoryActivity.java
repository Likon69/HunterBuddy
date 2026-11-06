package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.List;

public final class HbCategoryActivity {
    private final Category category;
    private int total;
    private int active;
    private int ordinal = -1;
    private String number = "";
    private String counter = "0/0";
    private String capacity = "0/0";

    public HbCategoryActivity(Category category) {
        this.category = category;
    }

    public void update(List<Module> modules, List<Module> hidden) {
        int nextTotal = 0;
        int nextActive = 0;
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (hidden.contains(module)) continue;
            nextTotal++;
            if (module.isActive()) nextActive++;
        }
        if (total != nextTotal) capacity = nextTotal + "/" + nextTotal;
        if (total != nextTotal || active != nextActive) counter = nextActive + "/" + nextTotal;
        total = nextTotal;
        active = nextActive;
    }

    public void number(int ordinal) {
        if (this.ordinal == ordinal) return;
        this.ordinal = ordinal;
        number = ordinal < 10 ? "0" + ordinal : Integer.toString(ordinal);
    }

    public String name() { return category.name; }
    public int total() { return total; }
    public boolean any() { return active != 0; }
    public String number() { return number; }
    public String counter() { return counter; }
    public String capacity() { return capacity; }
}
