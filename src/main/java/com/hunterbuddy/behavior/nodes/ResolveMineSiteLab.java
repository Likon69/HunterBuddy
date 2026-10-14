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
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.farmer.MineSiteFacts;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.farmer.ZoneKnowledge;
import com.hunterbuddy.behavior.lab.MineSiteLabContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.mc.MineSiteMemory;
import com.hunterbuddy.behavior.mc.SiteSelection;

public final class ResolveMineSiteLab extends Action {
    private final MineSiteLabContext lab;

    public ResolveMineSiteLab(MineSiteLabContext lab) {
        super("resolve-current-step-and-previously-verified-site");
        this.lab = lab;
        limit(100);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!lab.worldKey.equals(lab.world.storageKey())) return fail(FailureCode.REFUSED, "mining lab world changed");
        if (!lab.mission.isDone()) return new Running("loading FarmerBuddy current step");
        try {
            ctx.bb().loadDurable("bot:farmerbuddy", lab.mission.join());
            MissionJournal journal = new MissionJournal(FarmerSettings.MAX_STEPS);
            int current = journal.currentStep(ctx, "bot:farmerbuddy");
            FarmerStepConfig step = lab.steps.stream().filter(FarmerStepConfig::enabled).filter(s -> s.index() >= current)
                .findFirst().orElseThrow(() -> new IllegalStateException("no enabled current FarmerBuddy step"));
            FarmerProfile profile = lab.catalog.profile(step.profile()).orElseThrow(() -> new IllegalStateException("unknown profile"));
            FarmerZoneLookup.Resolved resolved = lab.lookup.resolve(step.hint());
            if (!resolved.ok()) throw new IllegalStateException(resolved.refusal());
            WorldKnowledge knowledge = WorldKnowledge.load(ctx);
            ZoneKnowledge memory = knowledge.zone(resolved.zone(), profile);
            MineSiteFacts previous = new MineSiteMemory(journal).load(ctx);
            MaterialSite site = previous != null && !previous.exited
                ? memory.sites.get(Long.toString(previous.site))
                : SiteSelection.bestVerified(memory, resolved.zone(), lab.world.feet(), System.currentTimeMillis());
            if (site == null) throw new IllegalStateException("no verified site for this step; run .bt farmer survey first");
            lab.mine = new MineSiteContext(site, profile, () -> {
                FarmerZoneLookup.Resolved zone = lab.lookup.resolve(step.hint());
                if (!zone.ok()) throw new IllegalStateException(zone.refusal());
                return zone.zone();
            }, lab.world, lab.baritone, lab.outputs, journal);
            return Success.INSTANCE;
        } catch (RuntimeException ex) {
            return fail(FailureCode.REFUSED, ex.getMessage());
        }
    }
}
