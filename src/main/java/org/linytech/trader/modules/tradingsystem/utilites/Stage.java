/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package org.linytech.trader.modules.tradingsystem.utilites;

import baritone.api.BaritoneAPI;
import baritone.api.process.ICustomGoalProcess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.linytech.trader.modules.tradingsystem.ExperienceTraderModule;

import java.util.Objects;

public abstract class Stage {
    protected ExperienceTraderModule mainModule;
    private static ClientPlayerEntity player;
    protected static ICustomGoalProcess goalProcess;

    protected static MinecraftClient client = MinecraftClient.getInstance();

    protected static @NotNull ClientPlayerEntity getPlayer() {
        if (player == null)
            player = Objects.requireNonNull(client.player);

        return player;
    }

    public static ICustomGoalProcess getGoalProcess() {
        if (goalProcess == null) {
            goalProcess = BaritoneAPI.getProvider().getBaritoneForPlayer(getPlayer()).getCustomGoalProcess();
        }

        return goalProcess;
    }

    @NotNull
    protected static <O> O requireNonNull(O obj) {
        return Objects.requireNonNull(obj);
    }

    public abstract boolean work();
    public abstract void reset();

    public Stage(ExperienceTraderModule main) {
        this.mainModule = main;
    }

    public String toShortString() {
        return this.getClass().getSimpleName();
    }
}
