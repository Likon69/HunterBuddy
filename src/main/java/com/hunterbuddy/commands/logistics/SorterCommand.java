package com.hunterbuddy.commands.logistics;

import com.hunterbuddy.modules.regear.util.ShulkerDataParser;
import com.hunterbuddy.modules.sorter.ShulkerSorter;
import com.hunterbuddy.modules.sorter.SorterSetup;
import com.hunterbuddy.modules.sorter.SorterStore;
import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Destination;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.command.CommandSource;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;


public class SorterCommand extends Command {
    public SorterCommand() {
        super("sorter", "Set up the shulker sorter (look at a chest first).", new String[0]);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(c -> status());

        builder.then(literal("station").executes(c -> assign(SorterSetup.Role.STATION, null)));
        builder.then(literal("input").executes(c -> assign(SorterSetup.Role.INPUT, null)));
        builder.then(literal("reserve").executes(c -> assign(SorterSetup.Role.RESERVE, null)));
        builder.then(literal("dest").then(argument("category", StringArgumentType.word())
            .suggests((ctx, sb) -> CommandSource.suggestMatching(SorterStore.get().rules.names(), sb))
            .executes(c -> assign(SorterSetup.Role.DESTINATION,
                StringArgumentType.getString(c, "category").toLowerCase(java.util.Locale.ROOT)))));
        builder.then(literal("remove").executes(c -> {
            SorterSetup setup = setup();
            if (setup != null) {
                setup.remove(setup.lookedAt());
            }
            return 1;
        }));

        builder.then(literal("list").executes(c -> {
            SorterProfile p = SorterStore.get();
            info("Inputs (%d): %s", p.inputs.size(), p.inputs);
            info("Reserves (%d): %s", p.reserves.size(), p.reserves);
            info("Station: %s", p.unpackStation == null ? "none" : p.unpackStation);
            for (Destination d : p.destinations) {
                info("  %s: %d chest(s), %d full", d.category, d.chests.size(), d.full.size());
            }
            List<String> missing = new ArrayList<>();
            for (String name : p.rules.names()) {
                if (p.destinationsFor(name).isEmpty()) missing.add(name);
            }
            if (!missing.isEmpty()) info("No destination yet for: %s", String.join(", ", missing));
            return 1;
        }));

        builder.then(literal("categories").executes(c -> {
            info("Categories: %s", String.join(", ", SorterStore.get().rules.names()));
            info("Edit them in meteor-client/hunterbuddy/sorter/*.json, then .sorter reload");
            return 1;
        }));

        builder.then(literal("check").executes(c -> {
            ItemStack stack = mc.player.getMainHandStack();
            SorterProfile p = SorterStore.get();
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
                List<ShulkerClassifier.Slot> slots = new ArrayList<>();
                for (ItemStack inner : ShulkerDataParser.parseShulkerContentsAsList(stack)) {
                    slots.add(new ShulkerClassifier.Slot(Registries.ITEM.getId(inner.getItem()).toString(), inner.getCount()));
                }
                ShulkerSorter module = Modules.get().get(ShulkerSorter.class);
                double purity = module != null ? module.purity.get() : 0.8;
                info("Shulker: %s", ShulkerClassifier.classify(slots, p.rules, purity).describe());
            } else if (!stack.isEmpty()) {
                info("%s -> %s", Registries.ITEM.getId(stack.getItem()), p.rules.categoryOf(Registries.ITEM.getId(stack.getItem()).toString()));
            } else {
                warning("Hold a shulker (or any item) in your main hand.");
            }
            return 1;
        }));

        builder.then(literal("resetfull").executes(c -> {
            SorterSetup setup = setup();
            if (setup != null) {
                setup.resetFull();
            }
            return 1;
        }));

        builder.then(literal("reload").executes(c -> {
            SorterSetup setup = setup();
            if (setup != null) {
                setup.reload();
            }
            return 1;
        }));
    }

    private SorterSetup setup() {
        ShulkerSorter module = Modules.get().get(ShulkerSorter.class);
        if (module == null) {
            error("Shulker sorter is unavailable.");
            return null;
        }
        return module.setup();
    }

    private int assign(SorterSetup.Role role, String category) {
        SorterSetup setup = setup();
        if (setup != null) {
            setup.assign(setup.lookedAt(), new SorterSetup.RoleChoice(role, category), false);
        }
        return 1;
    }

    private int status() {
        SorterProfile p = SorterStore.get();
        ShulkerSorter module = Modules.get().get(ShulkerSorter.class);
        info("%d input(s), %d destination(s), %d reserve(s). Module: %s", p.inputs.size(), p.destinations.size(), p.reserves.size(),
            module != null && module.isActive() ? module.statusLine() : "off");
        info("Subcommands: input, dest <category>, reserve, station, remove, list, categories, check, resetfull, reload");
        return 1;
    }
}
