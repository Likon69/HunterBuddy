package com.hunterbuddy.modules.tradingsystem.utilites;

import baritone.api.BaritoneAPI;
import baritone.api.process.ICustomGoalProcess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.NotNull;
import com.hunterbuddy.modules.tradingsystem.ExperienceTraderModule;

import java.util.Objects;

public abstract class Stage {
    protected ExperienceTraderModule mainModule;

    protected static MinecraftClient client = MinecraftClient.getInstance();

    protected static ClientPlayerEntity getPlayer() {
        return client.player;
    }

    public static ICustomGoalProcess getGoalProcess() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
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
