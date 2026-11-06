package com.hunterbuddy.gui.theme;

import com.hunterbuddy.gui.theme.widgets.HbCategoryActivity;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.ArrayList;
import java.util.List;

public final class HbCategoryRegistry {
    private final ArrayList<HbCategoryActivity> activities = new ArrayList<>();

    public void update() {
        List<?> registered = (List<?>) Modules.loopCategories();
        while (activities.size() < registered.size()) {
            activities.add(new HbCategoryActivity((Category) registered.get(activities.size())));
        }
        List<Module> hidden = Config.get().hiddenModules.get();
        for (int i = 0; i < activities.size(); i++) {
            HbCategoryActivity activity = activities.get(i);
            activity.update(Modules.get().getGroup((Category) registered.get(i)), hidden);
        }
    }

    public HbCategoryActivity find(String name) {
        update();
        for (int i = 0; i < activities.size(); i++) {
            HbCategoryActivity activity = activities.get(i);
            if (activity.name().equals(name)) return activity;
        }
        return null;
    }
}
