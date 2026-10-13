package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.LogisticsMemory;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.farmer.ShulkerCycleFacts;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

public final class LogisticsContext {
    public final WorldReader world;
    public final BaritoneBridge baritone;
    public final Outputs outputs;
    public final MissionJournal mission;
    public final Supplier<FarmerZone> zones;
    private final Supplier<FarmerProfile> profiles;
    private final boolean store;
    private final BooleanSupplier regear;
    private final BooleanSupplier exitTop;
    private final IntSupplier step;
    private final Map<String, TemporaryEquipment> equipment = new HashMap<>();
    public LogisticsFacts facts;
    public LogisticsMemory memory;
    public FarmerProfile profile;
    public InventoryLayout layout;
    public int requiredFlightWear;
    private WorldIdentity identity;
    public String equipmentPrefix = "logistics";
    public BooleanSupplier evacuationRequested = () -> false;

    public LogisticsContext(Supplier<FarmerProfile> profiles, Supplier<FarmerZone> zones, boolean store,
                            BooleanSupplier regear, BooleanSupplier exitTop, IntSupplier step, WorldReader world,
                            BaritoneBridge baritone, Outputs outputs, MissionJournal mission) {
        this.profiles = profiles;
        this.zones = zones;
        this.store = store;
        this.regear = regear;
        this.exitTop = exitTop;
        this.step = step;
        this.world = world;
        this.baritone = baritone;
        this.outputs = outputs;
        this.mission = mission;
    }

    public void initialize(TickContext ctx) {
        memory = new LogisticsMemory(mission, ctx.owner());
        facts = memory.load(ctx);
        identity = world.identity();
        equipment.clear();
        if (facts == null || facts.departure != null) {
            profile = profiles.get();
            if (profile == null) throw new IllegalStateException("logistics profile missing");
            facts = new LogisticsFacts();
            facts.profile = profile.key();
            facts.dimension = identity.dimensionKey();
            facts.store = store;
            facts.regear = regear.getAsBoolean();
            facts.exitTop = exitTop.getAsBoolean();
            facts.step = step.getAsInt();
            facts.zone = zones.get().corners().stream().map(LogisticsFacts::coordinates).toList();
            for (String role : List.of("chest", "material", "divers", "reserve")) {
                mission.equipment(ctx, equipmentName(role), "");
            }
            save(ctx, "logistics requested");
        } else {
            if (facts.store != store) throw new IllegalStateException("reprendre l'activité logistique déjà journalisée");
            if (!facts.dimension.equals(identity.dimensionKey())) {
                throw new IllegalStateException("logistics facts belong to another dimension");
            }
            profile = FarmerCatalog.load().profile(facts.profile).orElseThrow();
        }
        layout = new InventoryLayout(world, profile);
        String refusal = outputs.mining().temporaryRefusal();
        if (refusal != null) throw new IllegalStateException(refusal);
        if (world.countMatching(InventoryLayout::silkPickaxe) == 0) {
            throw new IllegalStateException("pioche Toucher de soie absente de l'inventaire");
        }
        if (facts.site == null && world.count(Items.ENDER_CHEST) == 0) {
            throw new IllegalStateException("ender chest absent de l'inventaire");
        }
        restore(ctx);
    }

    public void restore(TickContext ctx) {
        outputs.mining().breakRule(pos -> facts.activeZone().contains(pos.getX() + 0.5, pos.getZ() + 0.5));
        ProtectionRegistry.restore(ctx, mission, outputs.mining());
        if (facts.site == null) return;
        equipment.clear();
        restoreEquipment(ctx, "chest");
        for (String role : List.of("material", "divers", "reserve")) {
            if (!cycle(role).signature.isEmpty()) restoreEquipment(ctx, role);
        }
        identity = world.identity();
    }

    private void restoreEquipment(TickContext ctx, String role) {
        TemporaryEquipment item = equipment(role);
        item.restore(ctx);
        if (!role.equals("chest")) item.signature(ShulkerSignature.decode(cycle(role).signature, world));
        if (!item.tracked()) return;
        outputs.mining().trackTemporaryBlock(item.pos());
        if (item.enderChest()) outputs.mining().trackTemporaryChest(item.pos());
    }

    public boolean worldChanged() {
        return !identity.equals(world.identity());
    }

    public ShulkerCycleFacts cycle(String role) {
        if (role.equals("material")) return facts.material;
        if (role.equals("divers")) return facts.divers;
        if (role.equals("reserve")) return facts.reserve;
        throw new IllegalArgumentException("unknown shulker role " + role);
    }

    public TemporaryEquipment equipment(String role) {
        return equipment.computeIfAbsent(role, key -> {
            boolean chest = key.equals("chest");
            BlockPos pos = chest ? facts.chest() : facts.shulker();
            TemporaryEquipment item = new TemporaryEquipment(equipmentName(key), pos, chest, world,
                mission);
            if (!chest) item.signature(ShulkerSignature.decode(cycle(key).signature, world));
            return item;
        });
    }

    public String equipmentName(String role) {
        return equipmentPrefix + (role.equals("chest") ? "-chest" : "-box-" + role);
    }

    public void forget(String role) {
        equipment.remove(role);
    }

    public void save(TickContext ctx, String milestone) {
        memory.save(ctx, facts, milestone);
    }

    public boolean placed(String role) {
        TemporaryEquipment item = equipment(role);
        if (item.pickedUpConfirmed()) return false;
        return item.tracked() && world.loaded(item.pos()) && (item.enderChest()
            ? world.state(item.pos()).isOf(Blocks.ENDER_CHEST) : item.signature().matchesBlock(world.state(item.pos())));
    }

    public boolean returned(String role) {
        TemporaryEquipment item = equipment(role);
        if (!item.pickedUpConfirmed()) return false;
        if (!world.cleared(item.pos()) || world.countMatching(item.filter()) != item.beforePickup() + 1) {
            throw new IllegalStateException("recovered " + role + " no longer matches position and inventory evidence");
        }
        return true;
    }

    public boolean stored(String role) {
        ShulkerCycleFacts cycle = cycle(role);
        if (cycle.storedContents.isEmpty()) return false;
        if (cycle.storageSlot < 0 || !ShulkerSignature.decode(cycle.signature, world)
            .matches(LogisticsInventory.decode(cycle.storedContents, 27, world).get(cycle.storageSlot))) {
            throw new IllegalStateException("stored " + role + " has no matching container receipt");
        }
        if (world.countMatching(ShulkerSignature.decode(cycle.signature, world)::matches)
            != equipment(role).beforePickup()) {
            throw new IllegalStateException("stored " + role + " disagrees with the actual player inventory");
        }
        if (outputs.inventory().at(facts.chest()) && !ShulkerSignature.decode(cycle.signature, world)
            .matches(outputs.inventory().containerContents().get(cycle.storageSlot))) {
            throw new IllegalStateException("stored " + role + " differs from the actual ender chest");
        }
        return true;
    }

    public boolean protectedSite() {
        if (facts.site == null || !LogisticsSite.inside(facts.origin(), facts.activeZone())) return false;
        for (BlockPos pos : LogisticsSite.shell(facts.origin())) {
            if (facts.exitBreaking && pos.equals(facts.door()) && world.cleared(pos)) continue;
            if (!world.loaded(pos) || !world.state(pos).isFullCube(world.client().world, pos)) return false;
        }
        for (int i = 0; i < facts.walls.size(); i++) {
            BlockPos pos = LogisticsFacts.position(facts.walls.get(i));
            if (facts.exitBreaking && pos.equals(facts.door()) && world.cleared(pos)) continue;
            String actual = Registries.ITEM.getId(world.state(pos).getBlock().asItem()).toString();
            if (!actual.equals(facts.wallItems.get(i))) throw new IllegalStateException("recorded protection replaced at " + pos);
        }
        return facts.walls.size() == 32;
    }
}
