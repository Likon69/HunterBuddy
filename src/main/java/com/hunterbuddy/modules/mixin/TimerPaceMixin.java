package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.util.TimerPace;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Timer.class, remap = false)
public abstract class TimerPaceMixin extends Module {
    @Shadow
    @Final
    private SettingGroup sgGeneral;

    @Shadow
    @Final
    private Setting<Double> multiplier;

    @Unique
    private TimerPace hunterBuddy$pace;

    public TimerPaceMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void hunterBuddy$createPace(CallbackInfo ci) {
        hunterBuddy$pace = new TimerPace(sgGeneral, multiplier);
    }

    @Override
    public void onActivate() {
        if (hunterBuddy$pace != null) {
            hunterBuddy$pace.reset();
        }
    }

    @Unique
    @EventHandler
    private void hunterBuddy$onTick(TickEvent.Pre event) {
        if (hunterBuddy$pace != null) {
            hunterBuddy$pace.tick();
        }
    }
}
