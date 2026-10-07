package com.hunterbuddy.commands.logistics;

import com.hunterbuddy.modules.regear.util.ShulkerDataParser;
import com.hunterbuddy.modules.sorter.ShulkerSorter;
import com.hunterbuddy.modules.sorter.Purifier;
import com.hunterbuddy.modules.sorter.SorterStore;
import com.hunterbuddy.modules.sorter.logic.ShulkerClassifier;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Destination;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Pos;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;


public class SorterCommand extends Command {
    public SorterCommand() {
        super("sorter", "Set up the shulker sorter (look at a chest first).", new String[0]);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(c -> status());

        builder.then(literal("station").executes(c -> {
            if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                error("Look at a support block first.");
                return 1;
            }
            BlockPos support = hit.getBlockPos();
            if (!Purifier.validStation(support)) {
                error("Station needs a solid support, a free replaceable block above it, room for the lid and an accessible side.");
                return 1;
            }
            SorterProfile profile = SorterStore.get();
            Pos at = SorterStore.pos(support);
            String used = profile.whatIs(at);
            if (used != null && !at.equals(profile.unpackStation)) {
                error("That block is already the %s.", used);
                return 1;
            }
            profile.unpackStation = at;
            SorterStore.save();
            info("Purification station set at %s.", at);
            return 1;
        }));

        builder.then(literal("input").executes(c -> {
            BlockPos pos = lookedAtContainer(true);
            if (pos == null) return 1;
            SorterProfile p = SorterStore.get();
            Pos at = SorterStore.pos(pos);
            String used = p.whatIs(at);
            if (used != null) {
                warning("That chest is already the %s.", used);
                return 1;
            }
            p.inputs.add(at);
            SorterStore.save();
            info("Input added at %s (%d input chests).", at, p.inputs.size());
            return 1;
        }));

        builder.then(literal("dest").then(argument("category", StringArgumentType.word())
            .suggests((ctx, sb) -> CommandSource.suggestMatching(SorterStore.get().rules.names(), sb))
            .executes(c -> {
                String category = StringArgumentType.getString(c, "category").toLowerCase();
                SorterProfile p = SorterStore.get();
                if (!p.rules.isKnown(category)) {
                    error("Unknown category '%s'. Known: %s", category, String.join(", ", p.rules.names()));
                    return 1;
                }
                BlockPos pos = lookedAtContainer(false);
                if (pos == null) return 1;
                Pos at = SorterStore.pos(pos);
                String used = p.whatIs(at);
                if (used != null) {
                    warning("That chest is already the %s.", used);
                    return 1;
                }
                Destination d = p.getOrCreate(category);
                d.chests.add(at);
                SorterStore.save();
                info("%s: chest %d added at %s.", category, d.chests.size(), at);
                return 1;
            })));

        builder.then(literal("remove").executes(c -> {
            BlockPos pos = mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && SorterStore.pos(hit.getBlockPos()).equals(SorterStore.get().unpackStation)
                ? hit.getBlockPos() : lookedAtContainer();
            if (pos == null) return 1;
            SorterProfile p = SorterStore.get();
            Pos at = SorterStore.pos(pos);
            boolean removed = p.inputs.remove(at);
            for (Destination d : p.destinations) {
                removed |= d.chests.remove(at);
                d.full.remove(at);
            }
            p.destinations.removeIf(d -> d.chests.isEmpty());
            if (at.equals(p.unpackStation)) {
                p.unpackStation = null;
                removed = true;
            }
            SorterStore.save();
            info(removed ? "Removed %s." : "%s was not used by the sorter.", at);
            return 1;
        }));

        builder.then(literal("list").executes(c -> {
            SorterProfile p = SorterStore.get();
            info("Inputs (%d): %s", p.inputs.size(), p.inputs);
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
            SorterStore.get().destinations.forEach(d -> d.full.clear());
            SorterStore.save();
            info("All destination chests marked as not full.");
            return 1;
        }));

        builder.then(literal("reload").executes(c -> {
            SorterStore.reload();
            info("Sorter profile reloaded.");
            return 1;
        }));
    }

    private int status() {
        SorterProfile p = SorterStore.get();
        ShulkerSorter module = Modules.get().get(ShulkerSorter.class);
        info("%d input(s), %d destination(s). Module: %s", p.inputs.size(), p.destinations.size(),
            module != null && module.isActive() ? module.statusLine() : "off");
        info("Subcommands: input, dest <category>, station, remove, list, categories, check, resetfull, reload");
        return 1;
    }


    private BlockPos lookedAtContainer(boolean input) {
        BlockPos pos = lookedAtContainer();
        if (pos == null) return null;
        BlockEntity be = mc.world.getBlockEntity(pos);
        if (input && be instanceof TrappedChestBlockEntity) {
            error("Trapped chests cannot be inputs.");
            return null;
        }
        if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) {
            BlockState state = mc.world.getBlockState(pos);
            error("That is not a chest or barrel (%s).", Registries.BLOCK.getId(state.getBlock()).getPath());
            return null;
        }
        return pos;
    }

    private BlockPos lookedAtContainer() {
        if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            error("Look at a chest first.");
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.world.getBlockEntity(pos);
        BlockState state = mc.world.getBlockState(pos);
        if (!(be instanceof LootableContainerBlockEntity) || state.getBlock() instanceof ShulkerBoxBlock) {
            error("That is not a chest or barrel (%s).", Registries.BLOCK.getId(state.getBlock()).getPath());
            return null;
        }
        return pos;
    }
}
