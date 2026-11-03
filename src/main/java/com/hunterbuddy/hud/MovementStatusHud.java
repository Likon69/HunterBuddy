package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.movement.MovementStatusOptions;
import com.hunterbuddy.hud.movement.MovementStatusView;
import com.hunterbuddy.hud.movement.Size;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;

public class MovementStatusHud extends HudElement {
    public static final HudElementInfo<MovementStatusHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "movement-status",
        "Shows whether you are sneaking or sprinting.",
        MovementStatusHud::new);

    private final MovementStatusOptions options = new MovementStatusOptions(settings);
    private final MovementStatusView view = new MovementStatusView(options);

    public MovementStatusHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        Size size = isInEditor() ? view.renderPreview(renderer, x, y) : view.renderLive(renderer, x, y);
        if (size != null) {
            setSize(size.width(), size.height());
        }
    }

    public enum DisplayMode {
        Text,
        Icon,
        ActionBar
    }

    public enum Layout {
        Vertical,
        Horizontal
    }
}
