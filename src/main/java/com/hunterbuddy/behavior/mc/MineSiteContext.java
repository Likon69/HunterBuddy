package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.farmer.MineSiteFacts;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.BlockPos;

public final class MineSiteContext {
    public final MaterialSite site;
    public final FarmerProfile profile;
    public final WorldReader world;
    public final BaritoneBridge baritone;
    public final Outputs outputs;
    public final MissionJournal journal;
    public final MineSiteMemory memory;
    public final Supplier<FarmerZone> zoneSource;
    public final Key<BlockPos> targetKey = new Key<>("mine-site-block", BlockPos.class, Scope.OWNER, false);
    public final Key<BlockPos> swimKey = new Key<>("mine-site-swim", BlockPos.class, Scope.OWNER, false);
    public final Set<BlockPos> patch = new LinkedHashSet<>();
    public final Set<BlockPos> rejected = new HashSet<>();
    public final ArrayDeque<BlockPos> steps = new ArrayDeque<>();
    public final ArrayDeque<BlockPos> returnSteps = new ArrayDeque<>();
    public MineSiteFacts facts;
    public FarmerZone zone;
    public FarmerZone recordedZone;
    public WorldKnowledge knowledge;
    public BlockPos target;
    public BlockPos stand;
    public BlockPos nextFeet;
    public MineDropReceipt receipt;
    public String failure = "";
    public BooleanSupplier retreatRequested = () -> false;
    private boolean breathing;
    public BlockPos breathingPoint;

    public MineSiteContext(MaterialSite site, FarmerProfile profile, Supplier<FarmerZone> zoneSource, WorldReader world,
                           BaritoneBridge baritone, Outputs outputs, MissionJournal journal) {
        this.site = site;
        this.profile = profile;
        this.zoneSource = zoneSource;
        this.world = world;
        this.baritone = baritone;
        this.outputs = outputs;
        this.journal = journal;
        memory = new MineSiteMemory(journal);
    }

    public boolean inside(BlockPos point) {
        return zone != null && world.inBounds(point)
            && zone.containsWithMargin(point.getX() + 0.5, point.getZ() + 0.5, FarmerZoneLookup.TARGET_MARGIN);
    }

    public boolean material(BlockPos point) {
        return inside(point) && world.loaded(point) && profile.blocks().contains(world.state(point).getBlock());
    }

    public boolean finished() {
        if (facts.pending == null && retreatRequested.getAsBoolean() && facts.outcome.isEmpty()) {
            facts.outcome = "repli de mission demandé avant une nouvelle casse";
        }
        return facts.mined >= MaterialSite.SHULKER_BLOCKS || !facts.outcome.isEmpty();
    }

    public void protectSupport(BlockPos floor) {
        BlockPos point = floor;
        facts.supports.add(point.asLong());
        while (world.loaded(point) && world.state(point).getBlock() instanceof FallingBlock) {
            point = point.down();
            if (!world.inBounds(point)) break;
            facts.supports.add(point.asLong());
        }
    }

    public void save(TickContext ctx, String milestone) {
        facts.blocks = new ArrayList<>(patch.stream().map(BlockPos::asLong).toList());
        memory.save(ctx, facts, milestone);
    }

    public boolean needsBreath(Sensors sensors) {
        if (sensors.submerged() && sensors.air() < 200) breathing = true;
        if (sensors.air() >= 280) breathing = false;
        return breathing;
    }
}
