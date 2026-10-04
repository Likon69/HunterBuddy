package com.hunterbuddy.modules.tradingsystem.stages;

import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;
import com.hunterbuddy.modules.tradingsystem.utilites.Stage;

public class EmptyStage extends Stage {
    public EmptyStage(ExperienceTraderModule main) {
        super(main);
    }

    @Override
    public boolean work() {
        return false;
    }

    @Override
    public void reset() {

    }
}
