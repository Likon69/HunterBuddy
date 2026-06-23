/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.stages;

import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.util.math.BlockPos;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;
import org.linytech.trader.modules.tradingsystem.utilites.Stage;
import org.linytech.trader.modules.tradingsystem.utilites.TraderUtils;

public class ValidateStage extends Stage {
    public ValidateStage(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        if (TraderUtils.getNearestCleric(getPlayer(), super.mainModule.blocked, super.mainModule.searchingRadius.get()) == null) {
            super.mainModule.info("Торговля закончилась!");
            super.mainModule.toggle();

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Координаты базовой таргет-позиция: " + super.mainModule.forwardingBackPosition.get().toShortString());
            }
            if (!super.mainModule.forwardingBackPosition.get().equals(BlockPos.ORIGIN)) {
                if (super.mainModule.doOutput()) {
                    super.mainModule.info("Установлена базовая таргет-позиция: " + super.mainModule.forwardingBackPosition.get().toShortString());
                }
                MeteorExecutor.execute(() -> getGoalProcess().setGoalAndPath(new GoalBlock(super.mainModule.forwardingBackPosition.get())));
            }
        }

        return true;
    }

    @Override
    public void reset() {

    }
}
