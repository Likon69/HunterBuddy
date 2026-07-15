package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

// Replaces the RusherHack "ClientSideTime" / "TimeChanger" feature.
// Sets the client-side world time of day each tick so the server time is
// never used visually. Server-side time (mob spawning, plant growth, day/night
// cycle) is unaffected — only the rendered sky/light is changed for the local
// player.
public class ClientSideTime extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> time = sgGeneral.add(new IntSetting.Builder()
        .name("time")
        .description("Time of day to display on the client (0 = dawn, 6000 = noon, 12000 = dusk, 18000 = midnight).")
        .defaultValue(6000)
        .min(0)
        .max(24000)
        .sliderRange(0, 24000)
        .build()
    );

    public ClientSideTime() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "client-side-time", "Sets the client-side time of day. Server time and mob spawning are unaffected.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world != null) {
            long t = time.get();
            mc.world.setTime(t, t, false);
        }
    }
}