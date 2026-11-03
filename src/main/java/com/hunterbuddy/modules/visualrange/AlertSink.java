package com.hunterbuddy.modules.visualrange;

import com.hunterbuddy.modules.regear.util.MsgUtil;
import com.hunterbuddy.util.HuntFeed;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.util.math.BlockPos;

public final class AlertSink {
    private final PlayerRangeSettings players;
    private final RangeSound sound;
    private final DiscordReporter discord;

    public AlertSink(PlayerRangeSettings players, RangeSound sound, DiscordReporter discord) {
        this.players = players;
        this.sound = sound;
        this.discord = discord;
    }

    public void feed(HuntFeed.Type type, String label, BlockPos position) {
        HuntFeed.get().publish(type, label, position);
    }

    public void chat(String message) {
        if (players.chatNotify.get()) MsgUtil.sendMsg(message);
    }

    public void ding() {
        sound.play();
    }

    public void discord(Setting<Boolean> category, String title, String message) {
        if (discord.ready(category)) discord.send(title, message);
    }
}
