package org.linytech.trader;


import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

import org.linytech.trader.modules.AireForce;
import org.linytech.trader.modules.SandMineAddon;
import org.linytech.trader.modules.ItemsSucker;
import org.linytech.trader.modules.AnvilRename;
import org.linytech.trader.modules.AutoFarm;
import org.linytech.trader.modules.ShulkerColor;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderStarterModule;
import org.slf4j.Logger;

public class TraderAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category TRADER_CATEGORY = new Category("Trader");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Trader Addon");

        // Modules
        Modules.get().add(new AireForce(TRADER_CATEGORY));
        Modules.get().add(new SandMineAddon(TRADER_CATEGORY));
        Modules.get().add(new ShulkerColor(TRADER_CATEGORY));
        Modules.get().add(new ItemsSucker (TRADER_CATEGORY));
        Modules.get().add(new AutoFarm (TRADER_CATEGORY));
        Modules.get().add(new AnvilRename(TRADER_CATEGORY));
        ExperienceTraderModule module = new ExperienceTraderModule(TRADER_CATEGORY);
        Modules.get().add(module);
        Modules.get().add(new ExperienceTraderStarterModule(TRADER_CATEGORY, module));
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(TRADER_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "org.linytech.trader";
    }
}
