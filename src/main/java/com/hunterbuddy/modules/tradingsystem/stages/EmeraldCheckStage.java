package com.hunterbuddy.modules.tradingsystem.stages;

import net.minecraft.item.Items;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.utilites.Stage;

public class EmeraldCheckStage extends Stage {
    public EmeraldCheckStage(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        int emeraldCount = getPlayer().getInventory().count(Items.EMERALD);

        if (emeraldCount <= super.mainModule.minEmeraldsCount.get()) {
            super.mainModule.setTimeToClear(true);
            if (super.mainModule.doOutput()) {
                super.mainModule.info("Restocking emeralds: " + emeraldCount);
            }
        }

        return true;
    }

    @Override
    public void reset() {

    }
}
