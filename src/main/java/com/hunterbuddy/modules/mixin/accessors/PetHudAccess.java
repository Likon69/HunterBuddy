package com.hunterbuddy.modules.mixin.accessors;

import java.util.List;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Hud.class, remap = false)
public interface PetHudAccess {
    @Accessor("elements")
    List<HudElement> hunterbuddy$elements();

    @Invoker("shouldHideHud")
    boolean hunterbuddy$shouldHideHud();
}
