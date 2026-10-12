package com.hunterbuddy.behavior.command;

import com.hunterbuddy.behavior.core.BehaviorStatus;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.lab.LabScenario;
import com.hunterbuddy.behavior.lab.SelfCheck;
import com.hunterbuddy.behavior.lab.SelfCheckReport;
import com.hunterbuddy.modules.TreeJava;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Locale;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public final class BehaviorCommand extends Command {
    public BehaviorCommand() {
        super("bt", "Control TreeJava, live walk/look and simulated laboratory scenarios.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("selfcheck").executes(context -> selfcheck()));
        builder.then(literal("bots").executes(context -> control(() ->
            info("Bots: %s", String.join(", ", module().botNames())))));
        builder.then(literal("start").then(literal("lab").executes(context -> control(() -> {
            module().startLab();
            info("Started lab (simulated).");
        }))));
        builder.then(literal("switch").then(literal("lab").executes(context -> control(() -> {
            module().switchLab();
            info("Switch to lab requested.");
        }))));
        builder.then(literal("stop").executes(context -> control(() ->
            module().stopEngine(ExitReason.STOPPED, "Stopped by .bt stop"))));
        builder.then(literal("status").executes(context -> control(() ->
            BehaviorStatus.lines(module().snapshot()).forEach(line -> info("%s", line)))));
        builder.then(literal("stats").executes(context -> control(() ->
            BehaviorStatus.statsLines(module().snapshot()).forEach(line -> info("%s", line)))));
        builder.then(literal("outputs").executes(context -> control(() ->
            module().outputStatus().forEach(line -> info("%s", line)))));
        LiteralArgumentBuilder<CommandSource> lab = literal("lab");
        lab.then(literal("walk").then(argument("x", DoubleArgumentType.doubleArg())
            .then(argument("y", DoubleArgumentType.doubleArg()).then(argument("z", DoubleArgumentType.doubleArg())
                .executes(context -> control(() -> {
                    module().walkTo(DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"),
                        DoubleArgumentType.getDouble(context, "z"));
                    info("Live lab walk requested.");
                }))))));
        lab.then(literal("look").then(argument("yaw", DoubleArgumentType.doubleArg())
            .then(argument("pitch", DoubleArgumentType.doubleArg()).executes(context -> control(() -> {
                module().lookAt(DoubleArgumentType.getDouble(context, "yaw"), DoubleArgumentType.getDouble(context, "pitch"));
                info("Live lab look requested.");
            })))));
        lab.then(literal("stop").executes(context -> control(() ->
            module().stopEngine(ExitReason.STOPPED, "Stopped by .bt lab stop"))));
        for (LabScenario scenario : LabScenario.values()) {
            lab.then(literal(scenario.name().toLowerCase(Locale.ROOT)).executes(context -> control(() -> {
                module().selectScenario(scenario);
                info("Lab scenario requested: %s (simulated).", scenario);
            })));
        }
        builder.then(lab);
    }

    private TreeJava module() {
        TreeJava module = Modules.get().get(TreeJava.class);
        if (module == null) throw new IllegalStateException("TreeJava is not registered");
        return module;
    }

    private int control(Runnable action) {
        try {
            action.run();
            return SINGLE_SUCCESS;
        } catch (RuntimeException ex) {
            error("%s", ex.getMessage());
            return 0;
        }
    }

    private int selfcheck() {
        try {
            SelfCheckReport report = SelfCheck.run();
            info("Foundation OK: %d checks, %d simulated ticks. Live scenarios require in-game acceptance.", report.checks().size(), report.ticks());
            return SINGLE_SUCCESS;
        } catch (RuntimeException ex) {
            error("SelfCheck failed: %s", ex.getMessage());
            return 0;
        }
    }
}
