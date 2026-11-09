package com.hunterbuddy.hud.toast;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class ModuleToastFeed {
    public static final ModuleToastFeed INSTANCE = new ModuleToastFeed();
    public final ModuleToastHistory history = new ModuleToastHistory();
    private boolean initialized;

    private ModuleToastFeed() {
    }

    public void init() {
        if (initialized) return;
        initialized = true;
        MeteorClient.EVENT_BUS.subscribe(this);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ModuleToastBlur.INSTANCE.close());
    }

    public void record(Module module) {
        if (!ModuleToastInput.current().accepts() || !Utils.canUpdate()) return;
        history.record(new ModuleToastTimeline.Notice(module.title, module.keybind.isSet() ? module.keybind.toString() : "Unbound", module.isActive()));
    }

    @EventHandler
    private void onLeft(GameLeftEvent event) {
        history.reset();
    }
}
