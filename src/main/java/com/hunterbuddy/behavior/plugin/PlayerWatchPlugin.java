package com.hunterbuddy.behavior.plugin;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.GateWait;
import com.hunterbuddy.behavior.nodes.Guard;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class PlayerWatchPlugin implements Plugin {
    public static final Key<Boolean> PLAYER_VISIBLE = new Key<>("playerVisible", Boolean.class, Scope.OWNER, false);
    private final BooleanSupplier pauseOnPlayer;

    public PlayerWatchPlugin(BooleanSupplier pauseOnPlayer) {
        this.pauseOnPlayer = Objects.requireNonNull(pauseOnPlayer);
    }

    @Override
    public String name() { return "player-watch"; }

    @Override
    public void onRegister(SettingGroup settings) { Objects.requireNonNull(settings); }

    @Override
    public void pulse(TickContext ctx) {
        ctx.bb().set(ctx.owner(), PLAYER_VISIBLE, ctx.sensors().playersVisible() > 0);
    }

    @Override
    public List<HookEntry> entries(PluginContext context) {
        GateWait wait = new GateWait("player visible", ignored -> context.ticks().bb().get(context.owner(), PLAYER_VISIBLE).orElse(false), 120_000);
        Guard guard = new Guard(new Condition("player visible and pause enabled", sensors -> pauseOnPlayer.getAsBoolean()
            && context.ticks().bb().get(context.owner(), PLAYER_VISIBLE).orElse(false)), wait);
        return List.of(new HookEntry(Hook.BOT_PRE, context.owner(), Contributor.PLUGIN, 0, guard, true, wait));
    }
}
