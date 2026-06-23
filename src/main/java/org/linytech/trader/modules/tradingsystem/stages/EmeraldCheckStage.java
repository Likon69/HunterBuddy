/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.stages;

import net.minecraft.item.Items;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;

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
                super.mainModule.info("Запущено пополнение инвентаря при количестве изумрудов: " + emeraldCount);
            }
        }

        return true;
    }

    @Override
    public void reset() {

    }
}
