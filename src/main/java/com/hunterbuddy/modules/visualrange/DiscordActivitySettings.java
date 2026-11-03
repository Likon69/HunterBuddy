package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.settings.Setting;

public final class DiscordActivitySettings {
    public final Setting<Boolean> pvp;
    public final Setting<Boolean> death;
    public final Setting<Boolean> disconnect;

    public DiscordActivitySettings(DiscordSettings discord) {
        pvp = discord.group().add(
            discord.switchUnderMaster("discord-pvp", "Sends the PvP activity alerts to Discord."));
        death = discord.group().add(
            discord.switchUnderMaster("discord-death", "Sends your own death to Discord with the server wording."));
        disconnect = discord.group().add(discord.switchUnderMaster(
            "discord-disconnect", "Sends every disconnect to Discord, including kicks, timeouts and menu exits."));
    }
}
