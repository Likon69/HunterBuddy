package com.hunterbuddy.behavior.command;

import com.hunterbuddy.behavior.core.BehaviorStatus;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.lab.LabScenario;
import com.hunterbuddy.modules.TreeJava;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Locale;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public final class BehaviorCommand extends Command {
    public BehaviorCommand() {
        super("bt", "Control TreeJava and its live or simulated laboratory scenarios.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("bots").executes(context -> control(() ->
            info("Bots: %s", String.join(", ", module().botNames())))));
        builder.then(literal("start").then(literal("lab").executes(context -> control(() -> {
            module().startLab();
            info("Started lab (simulated).");
        }))).then(literal("farmerbuddy").executes(context -> control(() -> {
            module().startBot("farmerbuddy");
            info("Started farmerbuddy.");
        }))));
        builder.then(literal("switch").then(literal("lab").executes(context -> control(() -> {
            module().switchLab();
            info("Switch to lab requested.");
        }))).then(literal("farmerbuddy").executes(context -> control(() -> {
            module().switchBot("farmerbuddy");
            info("Switch to farmerbuddy requested.");
        }))));
        builder.then(literal("stop").executes(context -> control(() ->
            module().stopEngine(ExitReason.STOPPED, "Stopped by .bt stop"))));
        builder.then(literal("status").executes(context -> control(() ->
            BehaviorStatus.lines(module().snapshot()).forEach(line -> info("%s", line)))));
        builder.then(literal("stats").executes(context -> control(() ->
            BehaviorStatus.statsLines(module().snapshot()).forEach(line -> info("%s", line))))
            .then(literal("reset").executes(context -> control(() -> { module().resetStats(); info("Statistics reset."); }))));
        builder.then(literal("why").executes(context -> control(() -> module().why().forEach(line -> info("%s", line)))));
        builder.then(literal("routines").executes(context -> control(() -> module().routineStatus().forEach(line -> info("%s", line)))));
        builder.then(literal("plugins").executes(context -> control(() -> module().pluginStatus().forEach(line -> info("%s", line)))));
        builder.then(literal("routine").then(argument("slot", StringArgumentType.word())
            .then(argument("name", StringArgumentType.word()).executes(context -> control(() -> {
                module().selectRoutine(StringArgumentType.getString(context, "slot"), StringArgumentType.getString(context, "name"));
                info("Routine change requested.");
            })))));
        builder.then(literal("plugin").then(argument("name", StringArgumentType.word())
            .then(literal("on").executes(context -> control(() -> module().setPlugin(StringArgumentType.getString(context, "name"), true))))
            .then(literal("off").executes(context -> control(() -> module().setPlugin(StringArgumentType.getString(context, "name"), false))))));
        builder.then(literal("outputs").executes(context -> control(() ->
            module().outputStatus().forEach(line -> info("%s", line)))));
        builder.then(literal("farmer").then(literal("zone").executes(context -> control(() ->
            module().farmerZoneStatus().forEach(line -> info("%s", line)))))
            .then(literal("survey").executes(context -> control(() -> {
                module().farmerSurvey();
                info("Prospection de la zone de l'étape FarmerBuddy courante demandée.");
            }))));
        LiteralArgumentBuilder<CommandSource> lab = literal("lab");
        lab.then(literal("flyto").then(argument("x", IntegerArgumentType.integer())
            .then(argument("z", IntegerArgumentType.integer()).executes(context -> control(() -> {
                module().flyToLab(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z"));
                info("Live flight round trip requested.");
            })))));
        for (String scenario : java.util.List.of("store", "regear")) {
            lab.then(literal(scenario).then(argument("profile", StringArgumentType.word()).executes(context -> control(() -> {
                module().logisticsLab(scenario, StringArgumentType.getString(context, "profile"));
                info("Live logistics lab %s requested.", scenario);
            }))));
        }
        for (String scenario : java.util.List.of("echest", "shulker")) {
            lab.then(literal(scenario).executes(context -> control(() -> {
                module().inventoryLab(scenario); info("Live inventory lab %s requested.", scenario);
            })));
        }
        LiteralArgumentBuilder<CommandSource> armor = literal("armor");
        for (String piece : java.util.List.of("elytra", "chestplate")) {
            armor.then(literal(piece).executes(context -> control(() -> {
                module().inventoryLab("armor " + piece); info("Live armor lab %s requested.", piece);
            })));
        }
        lab.then(armor);
        for (String scenario : java.util.List.of("exitwater", "takeoff")) {
            lab.then(literal(scenario).executes(context -> control(() -> {
                module().flightLab(scenario); info("Live lab %s requested.", scenario);
            })));
        }
        lab.then(literal("open").then(argument("x", IntegerArgumentType.integer())
            .then(argument("y", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer())
                .executes(context -> control(() -> {
                    module().openAt(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
                        IntegerArgumentType.getInteger(context, "z"));
                    info("Live lab open requested.");
                }))))));
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
        lab.then(literal("goto").then(argument("x", DoubleArgumentType.doubleArg())
            .then(argument("y", DoubleArgumentType.doubleArg()).then(argument("z", DoubleArgumentType.doubleArg())
                .executes(context -> control(() -> {
                    module().goTo(DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"),
                        DoubleArgumentType.getDouble(context, "z"));
                    info("Live lab goto requested.");
                }))))));
        lab.then(literal("breakcolumn").then(argument("height", IntegerArgumentType.integer(1, 24))
            .executes(context -> control(() -> {
                module().breakColumn(IntegerArgumentType.getInteger(context, "height"));
                info("Live lab breakcolumn requested.");
            }))));
        lab.then(literal("place").then(argument("x", DoubleArgumentType.doubleArg())
            .then(argument("y", DoubleArgumentType.doubleArg()).then(argument("z", DoubleArgumentType.doubleArg())
                .executes(context -> control(() -> {
                    module().placeAt(DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "y"),
                        DoubleArgumentType.getDouble(context, "z"));
                    info("Live lab place requested.");
                }))))));
        lab.then(literal("pillar").then(argument("height", IntegerArgumentType.integer(1, 24))
            .executes(context -> control(() -> {
                module().pillar(IntegerArgumentType.getInteger(context, "height"));
                info("Live lab pillar requested.");
            }))));
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
}
