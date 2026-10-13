package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.ShulkerSignature;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class ChooseShulker extends Node {
    private final LogisticsContext logistics;
    private final String role;

    public ChooseShulker(LogisticsContext logistics, String role) {
        super("choisir-shulker-" + role);
        this.logistics = logistics;
        this.role = role;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ShulkerCycleFacts cycle = logistics.cycle(role);
        if (!cycle.signature.isEmpty() || !cycle.unavailable.isEmpty()) return Success.INSTANCE;
        List<ItemStack> contents = logistics.outputs.inventory().containerContents();
        int reserve = identifyRole(ctx, "reserve", contents);
        int divers = identifyRole(ctx, "divers", contents);
        if (reserve >= 0) {
            ShulkerSignature signature = ShulkerSignature.of(contents.get(reserve), logistics.world);
            logistics.mission.equipment(ctx, "logistics-reserve", signature.encoded());
            logistics.mission.equipment(ctx, "logistics-reserve-slot", Integer.toString(reserve));
            List<String> missing = LogisticsInventory.missing(logistics.world, logistics.profile, signature.contents());
            logistics.mission.equipment(ctx, "reserve-status", missing.isEmpty() ? "ready" : "incomplete");
        }
        int selected = role.equals("reserve") ? reserve : role.equals("divers") ? divers : -1;
        if (role.equals("material")) {
            for (int i = 0; i < contents.size(); i++) {
                if (i == reserve || i == divers || !ShulkerSignature.isShulker(contents.get(i))) continue;
                ShulkerSignature signature = ShulkerSignature.of(contents.get(i), logistics.world);
                if (!signature.empty() && LogisticsInventory.pure(signature, logistics.profile)
                    && !LogisticsInventory.full(signature, logistics.profile) && acceptsPlayer(signature)) {
                    selected = i;
                    break;
                }
            }
        }
        if (selected < 0 && !role.equals("reserve")) {
            for (int i = 0; i < contents.size(); i++) {
                if (i != reserve && i != divers && ShulkerSignature.isShulker(contents.get(i))
                    && ShulkerSignature.of(contents.get(i), logistics.world).empty()) {
                    selected = i;
                    break;
                }
            }
        }
        if (selected < 0) {
            cycle.unavailable = "shulker " + role + " absente ou pleine";
            logistics.facts.incomplete = cycle.unavailable;
            if (role.equals("reserve")) logistics.mission.equipment(ctx, "reserve-status", cycle.unavailable);
            logistics.save(ctx, cycle.unavailable);
            return Success.INSTANCE;
        }
        ShulkerSignature signature = ShulkerSignature.of(contents.get(selected), logistics.world);
        if (!role.equals("reserve") && signature.contents().stream()
            .allMatch(stack -> !stack.isEmpty() && stack.getCount() == stack.getMaxCount())) {
            cycle.unavailable = "shulker " + role + " pleine";
            logistics.facts.incomplete = cycle.unavailable;
            logistics.save(ctx, cycle.unavailable);
            return Success.INSTANCE;
        }
        if (!role.equals("reserve") && !acceptsPlayer(signature)) {
            cycle.unavailable = "shulker " + role + " sans place pour les objets présents";
            logistics.facts.incomplete = cycle.unavailable;
            logistics.save(ctx, cycle.unavailable);
            return Success.INSTANCE;
        }
        if (logistics.layout.freeSlot() < 0) throw new IllegalStateException("no free slot for the identified shulker");
        cycle.signature = signature.encoded();
        cycle.sourceSlot = selected;
        cycle.takeBaseline = logistics.world.countMatching(signature::matches);
        logistics.mission.equipment(ctx, "logistics-box-" + role, "");
        logistics.forget(role);
        logistics.equipment(role).milestone(ctx, "identified");
        logistics.save(ctx, "shulker identified role=" + role + " source=" + selected);
        return Success.INSTANCE;
    }

    private int identifyRole(TickContext ctx, String name, List<ItemStack> contents) {
        String encoded = logistics.mission.equipment(ctx, "logistics-" + name);
        String slot = logistics.mission.equipment(ctx, "logistics-" + name + "-slot");
        if (!encoded.isEmpty()) {
            ShulkerSignature expected = ShulkerSignature.decode(encoded, logistics.world);
            if (!slot.isEmpty()) {
                int index = Integer.parseInt(slot);
                if (index >= 0 && index < contents.size() && expected.matches(contents.get(index))) return index;
            }
            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < contents.size(); i++) if (expected.matches(contents.get(i))) matches.add(i);
            if (matches.size() != 1) throw new IllegalStateException("known " + name + " shulker identity is ambiguous");
            return matches.getFirst();
        }
        if (!name.equals("reserve")) return -1;
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            if (ShulkerSignature.isShulker(contents.get(i))
                && LogisticsInventory.reserve(ShulkerSignature.of(contents.get(i), logistics.world))) candidates.add(i);
        }
        if (candidates.size() > 1) throw new IllegalStateException("several reserve shulkers match their contents");
        return candidates.isEmpty() ? -1 : candidates.getFirst();
    }

    private boolean acceptsPlayer(ShulkerSignature signature) {
        List<ItemStack> player = new ArrayList<>(LogisticsInventory.player(logistics.world));
        player.add(logistics.world.client().player.getOffHandStack());
        for (ItemStack item : player) {
            boolean accepted = role.equals("material")
                ? LogisticsInventory.accepted(logistics.profile).test(item)
                : LogisticsInventory.miscellaneous(logistics.profile).test(item);
            if (!accepted) continue;
            for (ItemStack destination : signature.contents()) {
                if (destination.isEmpty() || ItemStack.areItemsAndComponentsEqual(item, destination)
                    && destination.getCount() < destination.getMaxCount()) return true;
            }
        }
        return false;
    }
}
