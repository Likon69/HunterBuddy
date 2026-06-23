package com.hunterbuddy;


import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

import com.hunterbuddy.modules.AireForce;
import com.hunterbuddy.modules.SandMineAddon;
import com.hunterbuddy.modules.ItemsSucker;
import com.hunterbuddy.modules.AnvilRename;
import com.hunterbuddy.modules.AutoFarm;
import com.hunterbuddy.modules.ShulkerColor;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderStarterModule;
import org.slf4j.Logger;

public class HunterBuddyAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category HUNTER_BUDDY_CATEGORY = new Category("HunterBuddy");

    @Override
    public void onInitialize() {
        LOG.info("Initializing HunterBuddy Addon");

        // Modules
        Modules.get().add(new AireForce(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new SandMineAddon(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ShulkerColor(HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new ItemsSucker (HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new AutoFarm (HUNTER_BUDDY_CATEGORY));
        Modules.get().add(new AnvilRename(HUNTER_BUDDY_CATEGORY));
        ExperienceTraderModule module = new ExperienceTraderModule(HUNTER_BUDDY_CATEGORY);
        Modules.get().add(module);
        Modules.get().add(new ExperienceTraderStarterModule(HUNTER_BUDDY_CATEGORY, module));
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(HUNTER_BUDDY_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.hunterbuddy";
    }
}