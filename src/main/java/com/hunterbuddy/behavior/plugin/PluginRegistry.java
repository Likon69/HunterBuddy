package com.hunterbuddy.behavior.plugin;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.TraceEvent;
import com.hunterbuddy.behavior.trace.TraceSink;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class PluginRegistry implements TraceSink {
    private final TickContext ticks;
    private final TreeHooks hooks;
    private final Consumer<String> alert;
    private final LongSupplier nanos;
    private final Map<String, Plugin> registered = new LinkedHashMap<>();
    private final Map<String, List<HookEntry>> entries = new LinkedHashMap<>();
    private final Set<String> enabled = new LinkedHashSet<>();
    private final Set<String> slowWarned = new LinkedHashSet<>();
    private final Map<String, SlowPulse> slowPulses = new LinkedHashMap<>();
    private final Set<String> pendingCleanup = new LinkedHashSet<>();

    public PluginRegistry(TickContext ticks, TreeHooks hooks, Consumer<String> alert) {
        this(ticks, hooks, alert, System::nanoTime);
    }

    public PluginRegistry(TickContext ticks, TreeHooks hooks, Consumer<String> alert, LongSupplier nanos) {
        this.ticks = Objects.requireNonNull(ticks);
        this.hooks = Objects.requireNonNull(hooks);
        this.alert = Objects.requireNonNull(alert);
        this.nanos = Objects.requireNonNull(nanos);
    }

    public void register(Plugin plugin, SettingGroup settings) {
        Objects.requireNonNull(plugin);
        Objects.requireNonNull(settings);
        String name = plugin.name();
        if (name == null || name.isBlank()) throw new IllegalArgumentException("empty plugin name");
        if (registered.containsKey(name)) throw new IllegalArgumentException("duplicate plugin: " + name);
        ticks.plugin(owner(name), () -> {
            plugin.onRegister(settings);
            return null;
        });
        registered.put(name, plugin);
    }

    public List<Plugin> registered() {
        return List.copyOf(registered.values());
    }

    public List<String> status() {
        return registered.keySet().stream().map(name -> name + " " + (enabled.contains(name) ? "on" : "off")).toList();
    }

    @Override
    public void accept(TraceEvent event) {
        for (String name : List.copyOf(enabled)) {
            try {
                ticks.plugin(owner(name), () -> {
                    requirePlugin(name).observe(event);
                    return null;
                });
            } catch (RuntimeException ex) {
                disableAndReport(name, "disabled", "trace callback failed: " + ex.getMessage());
            }
        }
    }

    public boolean enabled(String name) {
        requirePlugin(name);
        return enabled.contains(name);
    }

    public void enable(String name) {
        Plugin plugin = requirePlugin(name);
        if (enabled.contains(name)) return;
        List<HookEntry> built = entries.get(name);
        if (built == null) {
            PluginContext context = new PluginContext(ticks, owner(name));
            built = ticks.plugin(context.owner(), () -> List.copyOf(plugin.entries(context)));
        }
        validate(name, built);
        if (!entries.containsKey(name)) {
            built.forEach(entry -> entry.guard().availableWhen(() -> enabled.contains(name)));
        }
        built.forEach(hooks::insert);
        entries.put(name, built);
        enabled.add(name);
        slowWarned.remove(name);
        slowPulses.remove(name);
        pendingCleanup.remove(name);
    }

    private void validate(String name, List<HookEntry> built) {
        for (HookEntry entry : built) {
            if (entry.contributor() != Contributor.PLUGIN || !entry.owner().equals(owner(name))) {
                throw new IllegalArgumentException("invalid plugin entry: " + name);
            }
            if (entry.hook() != Hook.SURVIVAL && entry.hook() != Hook.BOT_PRE && entry.hook() != Hook.BOT_POST) {
                throw new IllegalArgumentException("plugin hook forbidden: " + entry.hook());
            }
        }
        List<HookEntry> combined = new ArrayList<>(hooks.entries().stream()
            .filter(entry -> !entry.owner().equals(owner(name))).toList());
        for (String active : enabled) {
            for (HookEntry entry : entries.get(active)) {
                if (!combined.contains(entry)) combined.add(entry);
            }
        }
        combined.addAll(built);
        TreeValidator.validate(combined);
    }

    public void disable(String name) {
        requirePlugin(name);
        if (!enabled.remove(name)) return;
        hooks.remove(owner(name));
        pendingCleanup.add(name);
        slowWarned.remove(name);
        slowPulses.remove(name);
    }

    public void applyPendingCleanup() {
        for (String name : List.copyOf(pendingCleanup)) {
            if (hooks.entries().stream().anyMatch(entry -> entry.owner().equals(owner(name)))) continue;
            ticks.bb().removeOwner(owner(name));
            ticks.budgets().removeOwner(owner(name));
            pendingCleanup.remove(name);
        }
    }

    public void pulse() {
        for (String name : List.copyOf(enabled)) {
            if (!enabled.contains(name) || !hooks.entries().containsAll(entries.get(name))) continue;
            Plugin plugin = requirePlugin(name);
            long started = nanos.getAsLong();
            try {
                ticks.plugin(owner(name), () -> {
                    plugin.pulse(ticks);
                    return null;
                });
            } catch (RuntimeException ex) {
                disableAndReport(name, "disabled", "pulse failed: " + ex.getClass().getSimpleName()
                    + ": " + Objects.toString(ex.getMessage(), ""));
                continue;
            }
            long elapsed = nanos.getAsLong() - started;
            if (!enabled.contains(name)) continue;
            if (elapsed <= 200_000) {
                slowPulses.remove(name);
                continue;
            }
            SlowPulse previous = slowPulses.get(name);
            int count = previous == null ? 1 : previous.tick() == ticks.tick() ? previous.count()
                : previous.tick() == ticks.tick() - 1 ? previous.count() + 1 : 1;
            slowPulses.put(name, new SlowPulse(ticks.tick(), count));
            if (count >= 3) {
                disableAndReport(name, "suspended", String.format(Locale.ROOT,
                    "pulse exceeded 0.2 ms for three consecutive ticks (%.3f ms)", elapsed / 1_000_000.0));
                continue;
            }
            if (slowWarned.add(name)) {
                alert.accept(String.format(Locale.ROOT, "Plugin %s pulse took %.3f ms (0.2 ms limit)", name, elapsed / 1_000_000.0));
            }
        }
    }

    private void disableAndReport(String name, String reason, String detail) {
        disable(name);
        String message = "Plugin " + name + " " + reason + ": " + detail;
        ticks.event(hooks.build(), Kind.HOOK, null, "plugin " + reason, message);
        alert.accept(message);
    }

    private record SlowPulse(long tick, int count) {
    }

    private Plugin requirePlugin(String name) {
        Plugin plugin = registered.get(Objects.requireNonNull(name));
        if (plugin == null) throw new IllegalArgumentException("unknown plugin: " + name);
        return plugin;
    }

    private String owner(String name) {
        return "plugin:" + name;
    }
}
