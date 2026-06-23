/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

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
