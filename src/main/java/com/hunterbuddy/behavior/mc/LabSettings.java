package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.lab.LabScenario;
import java.util.Objects;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class LabSettings {
    private final LabScenario defaultScenario;
    private final Consumer<LabScenario> changed;
    private Setting<LabScenario> scenario;

    public LabSettings(LabScenario defaultScenario) {
        this(defaultScenario, null);
    }

    public LabSettings(LabScenario defaultScenario, Consumer<LabScenario> changed) {
        this.defaultScenario = Objects.requireNonNull(defaultScenario);
        this.changed = changed;
    }

    public void register(SettingGroup settings) {
        Objects.requireNonNull(settings);
        if (scenario != null) throw new IllegalStateException("Lab settings already registered");
        EnumSetting.Builder<LabScenario> builder = new EnumSetting.Builder<LabScenario>()
            .name("lab-scenario").description("Simulated LabBot scenario; never controls Minecraft actions.")
            .defaultValue(defaultScenario);
        if (changed != null) builder.onChanged(changed);
        scenario = settings.add(builder.build());
    }

    public LabScenario scenario() {
        return scenario == null ? defaultScenario : scenario.get();
    }

    public void selectScenario(LabScenario next) {
        Objects.requireNonNull(scenario, "Lab settings are not registered").set(Objects.requireNonNull(next));
    }
}
