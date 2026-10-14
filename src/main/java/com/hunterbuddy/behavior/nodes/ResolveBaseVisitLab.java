package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.lab.BaseVisitLabContext;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import net.minecraft.util.math.BlockPos;

public final class ResolveBaseVisitLab extends Action {
    private final BaseVisitLabContext lab;

    public ResolveBaseVisitLab(BaseVisitLabContext lab) {
        super("resolve-FarmerBuddy-step-and-base");
        this.lab = lab;
        limit(100);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!lab.worldKey.equals(lab.world.storageKey())) {
            return fail(FailureCode.REFUSED, "base lab world changed");
        }
        if (!lab.mission.isDone()) {
            return new Running("loading FarmerBuddy journal");
        }
        try {
            ctx.bb().loadDurable("bot:farmerbuddy", lab.mission.join());
            MissionJournal journal = new MissionJournal(FarmerSettings.MAX_STEPS, "bot:farmerbuddy");
            int current = journal.currentStep(ctx);
            FarmerStepConfig step = lab.steps.stream().filter(FarmerStepConfig::enabled).filter(s -> s.index() >= current)
                .findFirst().orElseThrow(() -> new IllegalStateException("no current FarmerBuddy step enabled"));
            FarmerProfile profile = lab.catalog.profile(step.profile()).orElseThrow(() -> new IllegalStateException("unknown profile"));
            if (step.deliveryChest().equals(BlockPos.ORIGIN) || lab.settings.returnChest().equals(BlockPos.ORIGIN)
                || lab.settings.emptyShulkersChest().equals(BlockPos.ORIGIN) || lab.settings.resupplyChest().equals(BlockPos.ORIGIN)) {
                throw new IllegalStateException("configure all four base chest roles before visiting");
            }
            FarmerZoneLookup.Resolved resolved = lab.lookup.resolve(step.name());
            if (!resolved.ok()) {
                throw new IllegalStateException(resolved.refusal());
            }
            lab.visit = new BaseVisitContext(step, profile, () -> resolved.zone(), lab.settings,
                lab.world, lab.baritone, lab.outputs, journal);
            return Success.INSTANCE;
        } catch (RuntimeException ex) {
            return fail(FailureCode.REFUSED, ex.getMessage());
        }
    }
}
