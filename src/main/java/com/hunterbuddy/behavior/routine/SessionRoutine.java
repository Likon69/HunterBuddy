package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.mc.RoutineSettings;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.StopBot;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class SessionRoutine extends CoreRoutine {
    private BooleanSupplier pauseOnForeignScreen = () -> true;

    public SessionRoutine() {
        super("session", Hook.SESSION);
    }

    @Override
    public void onRegister(SettingGroup settings) {
        pauseOnForeignScreen = RoutineSettings.session(settings);
    }

    private boolean pauseOnForeignScreen() {
        return pauseOnForeignScreen.getAsBoolean();
    }

    @Override
    protected List<HookEntry> buildEntries(RoutineContext context) {
        Node queue = new Selector("session-queue",
            new WaitUntil(new Condition("queue ended", sensors -> !sensors.inQueue()), 18_000),
            new StopBot("Session queue wait exceeded 15 minutes"));
        WaitUntil screenWait = new WaitUntil(new Condition("screen closed", sensors -> !sensors.screenOpen()), 1_200);
        Node screen = new Selector("session-screen", screenWait, StopBot.withReason(ctx -> String.format(Locale.ROOT,
            "Player-owned screen '%s': waiting for %.1f seconds (60-second limit)",
            ctx.sensors().screenType(), screenWait.elapsedMs(ctx) / 1_000.0)));
        return List.of(entry(new Condition("in queue", sensors -> !sensors.dead() && sensors.inQueue()), queue),
            entry(new Condition("foreign screen", sensors -> !sensors.dead() && !sensors.gliding() && sensors.onGround() && pauseOnForeignScreen()
                && sensors.screenOpen() && !sensors.screenOurs()), screen));
    }

    @Override
    protected String readyDetail() {
        return "pause on foreign screen=" + pauseOnForeignScreen();
    }
}
