package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.scaffold.ScaffoldEngine;
import com.hunterbuddy.modules.scaffold.RegearMode;
import com.hunterbuddy.modules.scaffold.ScaffoldSettings;
import meteordevelopment.meteorclient.events.entity.player.ClipAtLedgeEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class GrimScaffold extends Module {
    private final ScaffoldSettings config = new ScaffoldSettings(settings);
    private final RegearMode regear = new RegearMode();
    private final ScaffoldEngine scaffold = new ScaffoldEngine(config, regear);

    public GrimScaffold() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "grim-scaffold", "Places blocks under you using GrimAC bypass.");
    }

    public void setRegearMode(boolean enabled) {
        regear.setEnabled(enabled);
        scaffold.resetDelay();
    }

    public void setRegearHotbarSlot(int slot) {
        regear.setHotbarSlot(slot);
    }

    public boolean isRegearMode() {
        return regear.enabled();
    }

    @Override
    public void onActivate() {
        scaffold.reset();
    }

    @Override
    public void onDeactivate() {
        regear.reset();
        RotationUtils.getInstance().clearRotations();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (config.onlyOnClick.get() && !mc.options.useKey.isPressed()) return;
        scaffold.tick();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        scaffold.render(event);
    }

    @EventHandler
    private void onClipAtLedge(ClipAtLedgeEvent event) {
        if (config.safewalk.get()) event.setClip(true);
    }
}
