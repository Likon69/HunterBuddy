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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class FarmerMissionContext {
    private static final long OCCUPATION_SAVE_MS = 10_000;
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
    private String fieldData;
    private LogisticsFacts savedField;
    private String baseData;
    private BaseVisitFacts savedBase;
    private long occupationSavedAt;

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
        facts = memory.load(ctx);
        boolean retained = facts.siteActive || unfinishedMine() || fieldActive() || baseActive();
        if (retained && facts.zone != null && !facts.zone.isEmpty()) {
            zone = FarmerZone.restore(facts.zone.stream().map(LogisticsFacts::position).toList());
        } else if (fieldActive()) {
            zone = fieldFacts().activeZone();
        } else if (baseActive()) {
            LogisticsFacts baseLogistics = new LogisticsMemory(journal, ctx.owner(), "base-logistics-facts").load(ctx);
            zone = baseLogistics.activeZone();
        } else if (unfinishedMine() && mineFacts().zone != null && !mineFacts().zone.isEmpty()) {
            zone = FarmerZone.restore(mineFacts().zone.stream().map(LogisticsFacts::position).toList());
        } else {
            FarmerZoneLookup.Resolved resolved = lookup.resolve(step.name());
            if (!resolved.ok()) throw new IllegalStateException(resolved.refusal());
            zone = resolved.zone();
        }
        knowledge = WorldKnowledge.load(ctx);
        knowledge.biomeBounds(step.deliveryChest(), settings.flightRadius());
        sites = knowledge.zone(zone, profile);
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
        if (retained && selected == null && facts.siteSnapshot != null
            && facts.site != null && facts.siteSnapshot.seed().asLong() == facts.site) {
            selected = facts.siteSnapshot;
            sites.sites.put(Long.toString(facts.site), selected);
        }
        MineSiteFacts mine = mineFacts();
        if (mine != null && !mine.exited && selected == null) {
            throw new IllegalStateException("site du minage interrompu absent de la connaissance persistée");
        }
        ProtectionRegistry.restore(ctx, journal, outputs.mining());
        outputs.mining().breakRule(baseActive() ? pos -> false
            : pos -> zone.contains(pos.getX() + 0.5, pos.getZ() + 0.5));
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
        LogisticsFacts field = fieldFacts();
        return field != null && field.departure == null;
    }

    public LogisticsFacts fieldFacts() {
        String data = journal.equipment(tick, "logistics-facts");
        if (!data.equals(fieldData)) {
            savedField = new LogisticsMemory(journal, journal.owner(tick)).load(tick);
            fieldData = data;
        }
        return savedField;
    }

    public boolean baseActive() {
        String data = journal.equipment(tick, "base-visit-facts");
        if (!data.equals(baseData)) {
            savedBase = new BaseVisitMemory(journal).load(tick);
            baseData = data;
        }
        return savedBase != null && savedBase.recoveredAt == null;
    }

    public boolean players() {
        return !visiblePlayers().isEmpty();
    }

    private List<? extends PlayerEntity> visiblePlayers() {
        return world.client().world.getPlayers().stream().filter(player -> player != world.client().player
            && !player.isInvisibleTo(world.client().player)).toList();
    }

    public boolean siteOccupied() {
        return selected != null && !eligible(selected);
    }

    public BlockPos arrivalStand;

    public boolean eligible(MaterialSite site) {
        long now = System.currentTimeMillis();
        return !site.flightAvoided() && visiblePlayers().stream().noneMatch(player -> Math.hypot(player.getX() - site.x,
            player.getZ() - site.z) <= 96) && facts.occupied.entrySet().stream().noneMatch(entry -> entry.getValue() > now
            && Math.hypot(BlockPos.fromLong(entry.getKey()).getX() - site.x,
                BlockPos.fromLong(entry.getKey()).getZ() - site.z) <= 96);
    }

    public MaterialSite best() {
        return SiteSelection.best(sites, zone, world.feet(), System.currentTimeMillis(), this::eligible);
    }

    public boolean occupiedOnly() {
        return best() == null && (SiteSelection.best(sites, zone, world.feet(), System.currentTimeMillis()) != null
            || sites.sites.values().stream().anyMatch(site -> site.usable() && site.flightAvoided()
                && zone.containsWithMargin(site.x + 0.5, site.z + 0.5, FarmerZoneLookup.TARGET_MARGIN)));
    }

    public void avoidVisiblePlayers(TickContext ctx) {
        List<? extends PlayerEntity> players = visiblePlayers();
        if (players.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean recorded = facts.occupied.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (PlayerEntity player : players) {
            long cell = new BlockPos(player.getBlockX() & -16, 0, player.getBlockZ() & -16).asLong();
            Long previous = facts.occupied.get(cell);
            if (previous == null || previous - now < settings.playerWaitMillis() - OCCUPATION_SAVE_MS) {
                facts.occupied.put(cell, now + settings.playerWaitMillis());
                recorded = true;
            }
        }
        boolean requested = siteOccupied() && !facts.changeRequested;
        if (requested) facts.changeRequested = true;
        if (requested || recorded && now - occupationSavedAt >= OCCUPATION_SAVE_MS) {
            occupationSavedAt = now;
            save(ctx, "visible player positions recorded for temporary site avoidance");
        }
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

    public int returnWear() {
        double distance = Math.hypot(world.feet().getX() - step.deliveryChest().getX(),
            world.feet().getZ() - step.deliveryChest().getZ());
        return new FlightBudget(FlightPerformance.load(tick), outputs.flight().minimumRockets()).returnWear(distance);
    }

    public boolean flightAfterField() {
        long remaining = Math.max(1, step.shulkerTarget() - journal.delivered(tick, step.index() - 1));
        return players() || fuelDue() || facts.baseRequested || facts.changeRequested
            || selected == null || !selected.usable() || !eligible(selected)
            || capacity() == 0 || journal.carried(tick, step.index() - 1) >= remaining;
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
        LogisticsFacts previous = fieldFacts();
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
        if (zone != null) facts.zone = zone.corners().stream().map(LogisticsFacts::coordinates).toList();
        facts.siteSnapshot = selected;
        memory.save(ctx, facts, milestone);
    }
}
