package com.hunterbuddy.modules.vanityesp;

import com.hunterbuddy.modules.regear.util.MapUtil;
import com.hunterbuddy.modules.regear.util.MsgUtil;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;

final class AlertDispatcher {
    static final String CATEGORY = "VanityESP";

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final AlertOptions options;

    AlertDispatcher(AlertOptions options) {
        this.options = options;
    }

    void sound(SoundEvent event, float pitch) {
        if (!options.sound.get()) return;
        mc.getSoundManager().play(PositionedSoundInstance.master(event, pitch, options.volume.get().floatValue()));
    }

    void chat(String message, BlockPos pos) {
        if (!options.chat.get()) return;
        String text = options.showCoords.get() ? message + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() : message;
        MsgUtil.sendModuleMsg(text, CATEGORY);
    }

    void waypoint(BlockPos pos, String name, String initials, MapUtil.WpColor color) {
        if (!Utils.XAERO_AVAILABLE || !options.addWaypoints.get()) return;
        XaeroWaypointManager.addWaypoint(CATEGORY, pos, name, initials, color, options.temporaryWaypoints.get());
    }

    void removeWaypoint(BlockPos pos) {
        if (Utils.XAERO_AVAILABLE && options.addWaypoints.get()) {
            XaeroWaypointManager.removeWaypoint(CATEGORY, pos);
        }
    }
}
