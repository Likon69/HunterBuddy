package com.hunterbuddy.behavior.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.behavior.core.BehaviorStatus;
import com.hunterbuddy.behavior.core.EngineSnapshot;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.modules.TreeJava;
import java.util.List;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class BehaviorHud extends HudElement {
    public static final HudElementInfo<BehaviorHud> INFO = new HudElementInfo<>(HunterBuddyAddon.HUD_GROUP,
        "TreeJava", "Behavior state, active hook and path, current deadlines, leases and recent events.", BehaviorHud::new);

    public BehaviorHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        TreeJava module = Modules.get().get(TreeJava.class);
        if (module == null || !module.hudEnabled()) {
            setSize(0, 0);
            return;
        }
        EngineSnapshot snapshot = module.snapshot();
        List<String> lines = BehaviorStatus.lines(snapshot);
        Color color = snapshot.recoveryLevel() >= 4 || snapshot.state() == RootState.STOPPED ? new Color(255, 90, 90)
            : snapshot.state() == RootState.RUNNING && snapshot.noProgressRemainingMs() < snapshot.noProgressWindowMs() / 5
                ? new Color(255, 190, 70) : new Color(235, 235, 235);
        double height = renderer.textHeight(true) + 2;
        double width = 0;
        for (int i = 0; i < lines.size(); i++) {
            width = Math.max(width, renderer.textWidth(lines.get(i), true));
            renderer.text(lines.get(i), x, y + i * height, color, true);
        }
        setSize(width, lines.size() * height);
    }
}
