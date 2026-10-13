package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.BaseVisitFacts;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerMissionFacts;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.LogisticsMemory;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.farmer.MineSiteFacts;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.farmer.ZoneKnowledge;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class FarmerMissionContext {
    public final FarmerSettings settings;
    public final FarmerCatalog catalog;
    public final WorldReader world;
    public final BaritoneBridge baritone;
    public final Outputs outputs;
    public final FarmerZoneLookup lookup;
    public final MissionJournal journal;
    public final FarmerMissionMemory memory;
    public TickContext tick;
    public FarmerStepConfig step;
    public FarmerProfile profile;
    public FarmerZone zone;
    public WorldKnowledge knowledge;
    public ZoneKnowledge sites;
    public FarmerMissionFacts facts;
    public MaterialSite selected;
    private String miningData;
    private MineSiteFacts savedMining;

    public FarmerMissionContext(FarmerSettings settings, FarmerCatalog catalog, WorldReader world,
                                BaritoneBridge baritone, Outputs outputs, FarmerZoneLookup lookup, MissionJournal journal) {
        this.settings = settings;
        this.catalog = catalog;
        this.world = world;
        this.baritone = baritone;
        this.outputs = outputs;
        this.lookup = lookup;
        this.journal = journal;
        memory = new FarmerMissionMemory(journal);
    }

    public void resolve(TickContext ctx, FarmerStepConfig current, FarmerProfile material) {
        tick = ctx;
        step = current;
        profile = material;
        FarmerZoneLookup.Resolved resolved = lookup.resolve(step.name());
        if (!resolved.ok()) throw new IllegalStateException(resolved.refusal());
        zone = resolved.zone();
        knowledge = WorldKnowledge.load(ctx);
        sites = knowledge.zone(zone, profile);
        facts = memory.load(ctx);
        if (facts.occupied.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis())) {
            save(ctx, "observations de joueurs expirées");
        }
        if (!facts.world.isEmpty() && !facts.world.equals(world.storageKey())) {
            throw new IllegalStateException("les faits de mission appartiennent à un autre monde");
        }
        if (facts.step != step.index() || !facts.profile.equals(profile.key())) {
            if (unfinishedMine() || fieldActive() || baseActive()) {
                throw new IllegalStateException("opération inachevée d'une autre étape : reprise requise");
            }
            facts = new FarmerMissionFacts();
            facts.world = world.storageKey();
            facts.step = step.index();
            facts.profile = profile.key();
            save(ctx, "étape de mission sélectionnée");
        }
        selected = facts.site == null ? null : sites.sites.get(Long.toString(facts.site));
        MineSiteFacts mine = mineFacts();
        if (mine != null && !mine.exited && selected == null) {
            throw new IllegalStateException("site du minage interrompu absent de la connaissance persistée");
        }
        ProtectionRegistry.restore(ctx, journal, outputs.mining());
    }

    public MineSiteFacts mineFacts() {
        String data = journal.equipment(tick, "mine-site");
        if (!data.equals(miningData)) {
            savedMining = new MineSiteMemory(journal).load(tick);
            miningData = data;
        }
        return savedMining;
    }

    public boolean unfinishedMine() {
        MineSiteFacts mine = mineFacts();
        return mine != null && !mine.exited;
    }

    public boolean fieldActive() {
        return LogisticsMemory.active(tick, journal);
    }

    public boolean baseActive() {
        BaseVisitFacts visit = new BaseVisitMemory(journal).load(tick);
        return visit != null && visit.recoveredAt == null;
    }

    public boolean players() {
        return tick.sensors().playersVisible() > 0 && zone.contains(tick.sensors().x(), tick.sensors().z());
    }

    public boolean siteOccupied() {
        return players() && (selected == null || Math.hypot(selected.x - world.feet().getX(),
            selected.z - world.feet().getZ()) <= 96);
    }

    public boolean eligible(MaterialSite site) {
        long now = System.currentTimeMillis();
        return facts.occupied.entrySet().stream().noneMatch(entry -> entry.getValue() > now
            && Math.hypot(BlockPos.fromLong(entry.getKey()).getX() - site.x,
                BlockPos.fromLong(entry.getKey()).getZ() - site.z) <= 96);
    }

    public MaterialSite best() {
        return SiteSelection.best(sites, zone, world.feet(), System.currentTimeMillis(), this::eligible);
    }

    public boolean occupiedOnly() {
        return best() == null && SiteSelection.best(sites, zone, world.feet(), System.currentTimeMillis()) != null;
    }

    public void avoidVisiblePlayers(TickContext ctx) {
        if (!players() || facts.changeRequested) return;
        boolean known = facts.occupied.entrySet().stream().anyMatch(entry -> entry.getValue() > System.currentTimeMillis()
            && Math.hypot(BlockPos.fromLong(entry.getKey()).getX() - world.feet().getX(),
                BlockPos.fromLong(entry.getKey()).getZ() - world.feet().getZ()) <= 32);
        if (known && !siteOccupied()) return;
        long until = System.currentTimeMillis() + settings.playerWaitTicks() * 50L;
        facts.occupied.put(world.feet().asLong(), until);
        if (siteOccupied()) {
            if (selected != null) facts.occupied.put(selected.seed().asLong(), until);
            facts.changeRequested = true;
        }
        save(ctx, "site occupé : évitement temporaire, changement demandé");
    }

    public List<ItemStack> stock() {
        String data = journal.equipment(tick, "mission-ender-contents");
        return data.isEmpty() ? List.of() : LogisticsInventory.decode(data, 27, world);
    }

    public int capacity() {
        int result = 0;
        List<ItemStack> contents = stock();
        String diversSlot = journal.equipment(tick, "logistics-divers-slot");
        String diversSignature = journal.equipment(tick, "logistics-divers");
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack stack = contents.get(slot);
            if (Integer.toString(slot).equals(diversSlot) && !diversSignature.isEmpty()
                && ShulkerSignature.decode(diversSignature, world).matches(stack)) continue;
            if (!ShulkerSignature.isShulker(stack)) continue;
            ShulkerSignature signature = ShulkerSignature.of(stack, world);
            if (signature.empty()) result += MaterialSite.SHULKER_BLOCKS;
            else if (LogisticsInventory.pure(signature, profile)) {
                result += Math.max(0, MaterialSite.SHULKER_BLOCKS - signature.contents().stream().mapToInt(ItemStack::getCount).sum());
            }
        }
        return result;
    }

    public boolean fuelDue() {
        return FarmerFlightBudget.returnDue(tick, step.deliveryChest(), world, outputs);
    }

    public boolean retreatDue() {
        return siteOccupied() || facts.changeRequested || fuelDue() || freeSlots() <= 2
            || world.countMatching(LogisticsInventory.accepted(profile)) >= MaterialSite.SHULKER_BLOCKS
            || LogisticsInventory.needsRegear(world, profile);
    }

    public int freeSlots() {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (world.client().player.getInventory().getStack(slot).isEmpty()) count++;
        }
        return count;
    }

    public boolean storageUnavailable() {
        LogisticsFacts previous = new LogisticsMemory(journal, tick.owner()).load(tick);
        return previous != null && previous.departure != null && previous.profile.equals(profile.key())
            && (!previous.material.unavailable.isEmpty() || !previous.divers.unavailable.isEmpty());
    }

    public void select(TickContext ctx, MaterialSite site) {
        selected = site;
        facts.site = site == null ? null : site.seed().asLong();
        facts.changeRequested = false;
        facts.changeReason = "";
        save(ctx, site == null ? "sélection sans site, prospection requise" : "site retenu " + site.seed().toShortString());
    }

    public void save(TickContext ctx, String milestone) {
        memory.save(ctx, facts, milestone);
    }
}
