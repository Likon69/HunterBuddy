package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.sorter.ShulkerSorter.State;
import com.hunterbuddy.modules.sorter.ShulkerSorter.Target;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.util.math.BlockPos;

final class SorterSnapshotBuilder {
    record Run(boolean connected, boolean active, boolean autoStart, boolean dryRun, boolean withdrawing,
               boolean completed, long started, long ended) { }
    record Work(State state, State resumeState, Target target, BlockPos targetPosition, String destinationCategory,
                String pauseReason, boolean packing, String packCategory, boolean packWaiting,
                BlockPos stationPosition, String stationPause, String stationState) { }
    record Inputs(Set<Pos> exhausted, Set<Pos> excluded, Set<Pos> emptied) {
        Inputs {
            exhausted = Set.copyOf(exhausted);
            excluded = Set.copyOf(excluded);
            emptied = Set.copyOf(emptied);
        }
    }
    private record ChestGroup(List<Pos> chests, Set<Pos> full) { }
    record Storage(List<Pos> inputs, List<ChestGroup> destinations, int reserves, String station) { }
    record View(Run run, Work work, Inputs inputs, Storage storage, SorterRunSnapshot.Results results,
                Supplier<List<String>> missingCategories, Supplier<SorterRunSnapshot.Withdrawal> withdrawal,
                ToDoubleFunction<BlockPos> distance) { }

    private final SorterAlerts alerts;

    SorterSnapshotBuilder(SorterAlerts alerts) {
        this.alerts = alerts;
    }

    static Storage storage(SorterProfile profile) {
        return new Storage(List.copyOf(profile.inputs), profile.destinations.stream()
            .map(destination -> new ChestGroup(List.copyOf(destination.chests), Set.copyOf(destination.full))).toList(),
            profile.reserves.size(), profile.unpackStation == null ? "" : profile.unpackStation.toString());
    }

    SorterRunSnapshot build(View view, long now) {
        SorterRunSnapshot.Alert alert = alerts.snapshot(now);
        if (!view.run().connected()) {
            SorterRunSnapshot empty = SorterRunSnapshot.empty();
            return new SorterRunSnapshot(empty.mode(), null, view.run().autoStart() ? "Auto-start armed" : "Off", "", null,
                empty.progress(), empty.results(), empty.capacity(), alert, empty.storage(), 0, null);
        }
        boolean active = view.run().active();
        boolean done = !active && view.run().completed() && view.run().ended() != 0 && now - view.run().ended() < 10_000_000_000L;
        String stationPause = view.work().stationPause();
        SorterRunSnapshot.Mode mode = !active ? (done ? SorterRunSnapshot.Mode.DONE : SorterRunSnapshot.Mode.OFF)
            : view.work().state() == State.PAUSED || stationPause != null ? SorterRunSnapshot.Mode.PAUSED
            : view.work().state() == State.PURIFY ? SorterRunSnapshot.Mode.STATION
            : view.work().state() == State.DONE ? SorterRunSnapshot.Mode.DONE : view.run().withdrawing() ? SorterRunSnapshot.Mode.WITHDRAW : SorterRunSnapshot.Mode.SORTING;
        State effective = view.work().state() == State.PAUSED ? view.work().resumeState() : view.work().state();
        SorterRunSnapshot.Stage stage = switch (effective) {
            case PURIFY -> SorterRunSnapshot.Stage.STATION;
            case PICK_INPUT -> SorterRunSnapshot.Stage.INPUTS;
            case PACK -> SorterRunSnapshot.Stage.PACK;
            case PICK_DEST, DEPOSIT, DONE -> SorterRunSnapshot.Stage.STORE;
            case GOTO, OPEN -> view.work().target() == Target.INPUT ? SorterRunSnapshot.Stage.INPUTS : SorterRunSnapshot.Stage.STORE;
            default -> SorterRunSnapshot.Stage.SORT;
        };
        if (active && view.work().packing()) {
            stage = SorterRunSnapshot.Stage.PACK;
        }
        if (!active) {
            stage = done ? SorterRunSnapshot.Stage.STORE : null;
        }
        SorterRunSnapshot.Target hudTarget = null;
        if (active && effective == State.PURIFY && view.work().stationPosition() != null) {
            BlockPos pos = view.work().stationPosition();
            hudTarget = new SorterRunSnapshot.Target("Station", "", pos.toShortString(),
                view.distance().applyAsDouble(pos));
        } else if (active && view.work().targetPosition() != null && effective != State.PICK_INPUT && effective != State.PICK_DEST && effective != State.PACK) {
            hudTarget = new SorterRunSnapshot.Target(view.work().target() == Target.INPUT ? "Input" : view.work().target() == Target.PACK ? "Pack input" : "Destination",
                view.work().target() == Target.PACK ? view.work().packCategory() : view.work().target() == Target.DEST ? view.work().destinationCategory() : "", view.work().targetPosition().toShortString(),
                view.distance().applyAsDouble(view.work().targetPosition()));
        }
        int excluded = 0;
        int remaining = 0;
        int empty = 0;
        for (Pos input : view.storage().inputs()) {
            if (active || done) {
                if (view.inputs().excluded().contains(input)) {
                    excluded++;
                } else if (!view.inputs().exhausted().contains(input)) {
                    remaining++;
                }
                if (view.inputs().emptied().contains(input)) {
                    empty++;
                }
            } else {
                remaining++;
            }
        }
        Set<Pos> full = new HashSet<>();
        int destinations = 0;
        for (ChestGroup destination : view.storage().destinations()) {
            destinations += destination.chests().size();
            for (Pos pos : destination.chests()) {
                if (destination.full().contains(pos)) {
                    full.add(pos);
                }
            }
        }
        List<String> missing = (active || done) && !view.run().withdrawing() ? view.missingCategories().get() : List.of();
        String stateText = !active ? (view.run().autoStart() ? "Auto-start armed" : done ? "Done" : "Off") : switch (view.work().state()) {
            case IDLE -> "Off";
            case PICK_INPUT -> "Choosing input";
            case GOTO -> view.work().target() != Target.DEST ? "Walking to input" : "Walking to chest";
            case OPEN -> view.work().target() != Target.DEST ? "Opening input" : "Opening chest";
            case TAKE -> "Taking items";
            case PICK_DEST -> "Choosing destination";
            case PACK -> !view.work().packWaiting() ? "Choosing pack input" : "Waiting for screen to close";
            case DEPOSIT -> "Depositing";
            case CLOSE -> "Closing chest";
            case PURIFY -> view.work().stationState();
            case PAUSED -> "Paused";
            case DONE -> "Done";
        };
        if (mode == SorterRunSnapshot.Mode.PAUSED) {
            stateText = "Paused";
        }
        if (active && !view.run().withdrawing() && view.run().dryRun()) {
            stateText = "Dry run · " + stateText;
        }
        SorterRunSnapshot.Withdrawal hudWithdrawal = (active || done) && view.run().withdrawing() && view.withdrawal() != null ? view.withdrawal().get() : null;
        if (hudWithdrawal != null) {
            stateText = "Withdraw";
            stage = null;
            if (hudTarget != null) {
                hudTarget = new SorterRunSnapshot.Target(view.work().target() == Target.ENDER ? "Ender chest" : "Source",
                    hudWithdrawal.category(), hudTarget.position(), hudTarget.distance());
            }
        }
        long duration = (active || done) && view.run().started() != 0
            ? Math.max(0, ((view.run().ended() == 0 ? now : view.run().ended()) - view.run().started()) / 1_000_000) : 0;
        return new SorterRunSnapshot(mode, stage, stateText,
            mode == SorterRunSnapshot.Mode.PAUSED ? (stationPause != null ? stationPause : view.work().pauseReason()) : "",
            hudTarget, new SorterRunSnapshot.Progress(view.storage().inputs().size(), remaining, empty, excluded),
            active || done ? view.results() : new SorterRunSnapshot.Results(0, 0, 0, 0, 0, 0, Map.of()),
            new SorterRunSnapshot.Capacity(full.size(), view.storage().reserves(), missing), alert,
            new SorterRunSnapshot.Storage(view.storage().inputs().size(), destinations, view.storage().reserves(),
                view.storage().station()), duration, hudWithdrawal);
    }
}
