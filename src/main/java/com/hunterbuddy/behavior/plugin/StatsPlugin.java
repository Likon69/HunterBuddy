package com.hunterbuddy.behavior.plugin;

import com.hunterbuddy.behavior.bot.BotStats;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.trace.TraceEvent;
import com.hunterbuddy.behavior.trace.TraceReasons;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class StatsPlugin implements Plugin {
    private final Supplier<BotStats> stats;
    private Sensors previous;
    private BotStats sampled;

    public StatsPlugin(Supplier<BotStats> stats) { this.stats = Objects.requireNonNull(stats); }

    @Override
    public String name() { return "stats"; }

    @Override
    public void onRegister(SettingGroup settings) { Objects.requireNonNull(settings); }

    @Override
    public List<HookEntry> entries(PluginContext context) { return List.of(); }

    @Override
    public void pulse(TickContext ctx) {
        BotStats current = stats.get();
        Sensors now = ctx.sensors();
        if (current != null && current == sampled && previous != null && now.worldId() == previous.worldId()
            && now.playerId() == previous.playerId() && now.dimensionKey().equals(previous.dimensionKey())) {
            current.add("blocksMined", Math.max(0, now.blocksMined() - previous.blocksMined()));
            current.add("pickups", Math.max(0, now.pickups() - previous.pickups()));
            double dx = now.x() - previous.x();
            double dy = now.y() - previous.y();
            double dz = now.z() - previous.z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!now.serverLagging() && now.setbacks() == previous.setbacks() && distance < 16) current.add("distance", distance);
        }
        previous = now;
        sampled = current;
    }

    @Override
    public void observe(TraceEvent event) {
        BotStats current = stats.get();
        if (current != null) current.observe(event);
        if (event.kind() == com.hunterbuddy.behavior.trace.Kind.BOT && (event.reason().equals(TraceReasons.STARTED) || event.reason().equals(TraceReasons.SWITCHED))) previous = null;
    }
}
