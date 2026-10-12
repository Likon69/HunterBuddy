package com.hunterbuddy.behavior.plugin;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
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

public final class PluginRegistry {
    private final TickContext ticks;
    private final TreeHooks hooks;
    private final Consumer<String> alert;
    private final LongSupplier nanos;
    private final Map<String, Plugin> registered = new LinkedHashMap<>();
    private final Map<String, List<HookEntry>> entries = new LinkedHashMap<>();
    private final Set<String> enabled = new LinkedHashSet<>();
    private final Set<String> slowWarned = new LinkedHashSet<>();
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
        built.forEach(hooks::insert);
        entries.put(name, built);
        enabled.add(name);
        slowWarned.remove(name);
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
                throw new IllegalStateException("Plugin " + name + " pulse: " + ex.getClass().getSimpleName()
                    + ": " + Objects.toString(ex.getMessage(), ""), ex);
            }
            long elapsed = nanos.getAsLong() - started;
            if (elapsed > 200_000 && slowWarned.add(name)) {
                alert.accept(String.format(Locale.ROOT, "Plugin %s pulse took %.3f ms (0.2 ms limit)", name, elapsed / 1_000_000.0));
            }
        }
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
