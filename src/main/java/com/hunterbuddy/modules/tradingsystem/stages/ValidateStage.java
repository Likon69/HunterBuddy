package com.hunterbuddy.modules.tradingsystem.stages;

import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.util.math.BlockPos;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.utilites.Stage;
import com.hunterbuddy.modules.tradingsystem.utilites.TraderUtils;

public class ValidateStage extends Stage {
    public ValidateStage(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        if (TraderUtils.getNearestCleric(getPlayer(), super.mainModule.blocked, super.mainModule.searchingRadius.get()) == null) {
            super.mainModule.info("Trading finished.");
            super.mainModule.toggle();

            if (super.mainModule.doOutput()) {
                super.mainModule.info("Home position: " + super.mainModule.forwardingBackPosition.get().toShortString());
            }
            if (!super.mainModule.forwardingBackPosition.get().equals(BlockPos.ORIGIN)) {
                if (super.mainModule.doOutput()) {
                    super.mainModule.info("Home goal set: " + super.mainModule.forwardingBackPosition.get().toShortString());
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
