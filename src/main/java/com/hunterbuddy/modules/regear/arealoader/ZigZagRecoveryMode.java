package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.regear.arealoader.modes.ZigZag;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

final class ZigZagRecoveryMode implements RecoveryMode {
    private static final String MODE_FILE = "ZigZag";
    private static final int MIN_LEG_LENGTH = 100;
    private static final int MAX_LEG_LENGTH = 100000;
    private static final int MIN_ROW_GAP = 16;
    private static final int MAX_ROW_GAP = 1000;
    private static final float QUARTER_TURN = 90.0f;
    private static final float REVERSE_TURN = 180.0f;

    private final AreaLoader area;
    private final RecoveryForm form;
    private WIntEdit legLength;
    private WIntEdit rowGap;
    private WDropdown<AreaRecoveryScreen.CardinalDirection> direction;

    ZigZagRecoveryMode(AreaLoader area, RecoveryForm form) {
        this.area = area;
        this.form = form;
    }

    @Override
    public void buildSection(GuiTheme theme, WContainer section) {
        section.add(theme.label("Origin is where the zigzag started, resume is your current position."));
        section.add(theme.label("The leg length, row gap and direction must match the original run."));
        WHorizontalList sizesRow = section.add(theme.horizontalList()).expandX().widget();
        sizesRow.add(theme.label("Leg length"));
        legLength = sizesRow.add(theme.intEdit(area.zigzagLegLength.get(), MIN_LEG_LENGTH, MAX_LEG_LENGTH, true)).widget();
        sizesRow.add(theme.label("Row gap"));
        rowGap = sizesRow.add(theme.intEdit(area.zigzagRowGap.get(), MIN_ROW_GAP, MAX_ROW_GAP, true)).widget();
        WHorizontalList directionRow = section.add(theme.horizontalList()).expandX().widget();
        directionRow.add(theme.label("Main direction"));
        direction = directionRow.add(theme.dropdown(AreaRecoveryScreen.CardinalDirection.SOUTH)).widget();
    }

    private float mainYaw() {
        return direction.get().yaw;
    }

    private ZigZagRecoveryPlan plan() {
        ZigZagRecoveryPlan.Sizes sizes = new ZigZagRecoveryPlan.Sizes(legLength.get(), rowGap.get());
        return new ZigZagRecoveryPlan(form.origin(), form.resume(), Heading.of(mainYaw()), sizes);
    }

    @Override
    public void validate() {
        ZigZagRecoveryPlan plan = plan();
        ChatUtils.info("Main progress %d blocks, side progress %d blocks", plan.mainProgress(), plan.sideProgress());
        ChatUtils.info("About %d legs completed, %d blocks into the current leg", plan.legsCompleted(), plan.positionInLeg());
        ChatUtils.info(plan.isLikelyOnMainLeg() ? "You are probably on a main leg" : "You are probably on a side leg");
    }

    @Override
    public void snapToPath() {
        RecoveryPoint snapped = plan().snapped();
        form.setResume(snapped);
        ChatUtils.info("Resume position aligned to %s", area.coords(snapped.x(), snapped.z()));
    }

    @Override
    public boolean apply() {
        ZigZag.PathingDataZigZag data = describeProgress(plan().legsCompleted());
        if (!RecoverySaveWriter.write(area, MODE_FILE, data)) return false;
        area.zigzagLegLength.set(legLength.get());
        area.zigzagRowGap.set(rowGap.get());
        return true;
    }

    private ZigZag.PathingDataZigZag describeProgress(int legsCompleted) {
        boolean goingForward = legsCompleted % 2 == 0;
        float yaw = goingForward ? mainYaw() : Heading.normalize(mainYaw() + REVERSE_TURN);
        float sideYaw = Heading.normalize(mainYaw() + QUARTER_TURN);
        return new ZigZag.PathingDataZigZag(form.origin().atPathLevel(), form.resume().atPathLevel(), yaw, mainYaw(),
            sideYaw, goingForward, true, legsCompleted);
    }
}
