package com.hunterbuddy.commands;

import com.hunterbuddy.modules.messenger.Messenger;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public class MessengerCommand extends Command {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public MessengerCommand() {
        super("messenger", "Open, clear or ignore private message conversations.", new String[0]);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            info("Usage: open <name> | clear | ignore <name> | unignore <name>");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("open").then(argument("name", StringArgumentType.word()).executes(context -> {
            Messenger messenger = active();
            String name = name(context);
            if (messenger == null || name == null) return SINGLE_SUCCESS;

            mc.send(() -> messenger.openChat(name));
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("clear").executes(context -> {
            Messenger messenger = active();
            if (messenger == null) return SINGLE_SUCCESS;

            messenger.clearAll();
            info("All conversations cleared.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("ignore").then(argument("name", StringArgumentType.word()).executes(context -> {
            Messenger messenger = active();
            String name = name(context);
            if (messenger == null || name == null) return SINGLE_SUCCESS;

            if (messenger.isIgnored(name)) {
                warning("%s is already ignored.", name);
                return SINGLE_SUCCESS;
            }

            messenger.ignore(name);
            info("Ignoring %s.", name);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("unignore").then(argument("name", StringArgumentType.word()).executes(context -> {
            Messenger messenger = active();
            String name = name(context);
            if (messenger == null || name == null) return SINGLE_SUCCESS;

            if (messenger.unignore(name)) info("No longer ignoring %s.", name);
            else warning("%s was not ignored.", name);
            return SINGLE_SUCCESS;
        })));
    }

    private String name(CommandContext<CommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (NAME.matcher(name).matches()) return name;

        warning("%s is not a valid player name.", name);
        return null;
    }

    private Messenger active() {
        Messenger messenger = Messenger.get();
        if (messenger != null && messenger.isActive()) return messenger;

        warning("The messenger module is off.");
        return null;
    }
}
