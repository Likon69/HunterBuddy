package com.hunterbuddy.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class ShipwreckModels {
    private static final String[] SHAPES = {
        "with_mast", "rightsideup_full", "rightsideup_fronthalf", "rightsideup_backhalf",
        "sideways_full", "sideways_fronthalf", "sideways_backhalf",
        "upsidedown_full", "upsidedown_fronthalf", "upsidedown_backhalf"
    };
    private static final String[] WOODS = {
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
        "pale_oak", "bamboo", "crimson", "warped"
    };
    private static final String[] SUFFIXES = {
        "_planks", "_log", "_wood", "_stem", "_hyphae", "_block", "_stairs", "_slab",
        "_fence", "_fence_gate", "_trapdoor", "_door"
    };
    private static final int[] FAMILIES = {1, 2, 2, 2, 2, 2, 3, 4, 5, 6, 7, 8};
    private static final BlockPos PIVOT = new BlockPos(4, 0, 15);
    private static final ConcurrentHashMap<String, Integer> EXACT_FAMILIES = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_FAMILY = new AtomicInteger(100);
    private static CompletableFuture<ShipwreckModels> loading;
    final Model[] models;
    private final int[] blockFamilies;

    private ShipwreckModels(Model[] models, int[] blockFamilies) {
        this.models = models;
        this.blockFamilies = blockFamilies;
    }

    static synchronized CompletableFuture<ShipwreckModels> loadOnce() {
        if (loading == null) {
            loading = CompletableFuture.supplyAsync(() -> {
                try {
                    return load();
                } catch (IOException | RuntimeException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            });
        }
        return loading;
    }

    static ShipwreckModels load() throws IOException {
        ShipwreckModels resources = loadResources();
        int[] families = new int[Registries.BLOCK.size()];
        for (Block block : Registries.BLOCK) {
            families[Registries.BLOCK.getRawId(block)] = family(Registries.BLOCK.getId(block).toString());
        }
        return new ShipwreckModels(resources.models, families);
    }

    static ShipwreckModels loadResources() throws IOException {
        Model[] models = new Model[SHAPES.length * 2];
        int index = 0;
        for (String shape : SHAPES) {
            for (String suffix : new String[]{"", "_degraded"}) {
                String name = shape + suffix;
                String resource = "data/minecraft/structure/shipwreck/" + name + ".nbt";
                try (InputStream stream = ShipwreckModels.class.getClassLoader().getResourceAsStream(resource)) {
                    if (stream == null) throw new IOException("missing resource " + resource);
                    models[index++] = read(name, NbtIo.readCompressed(stream, NbtSizeTracker.ofUnlimitedBytes()));
                }
            }
        }
        return new ShipwreckModels(models, new int[0]);
    }

    int family(Block block) {
        int id = Registries.BLOCK.getRawId(block);
        return id >= 0 && id < blockFamilies.length ? blockFamilies[id] : -1;
    }

    static int family(String id) {
        if (id.equals("minecraft:chest")) return 9;
        if (id.startsWith("minecraft:")) {
            String name = id.substring(10);
            if (name.startsWith("stripped_")) name = name.substring(9);
            for (String wood : WOODS) {
                for (int i = 0; i < SUFFIXES.length; i++) {
                    if (name.equals(wood + SUFFIXES[i])) return FAMILIES[i];
                }
            }
        }
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            throw new IllegalArgumentException("unknown block " + id);
        }
        return EXACT_FAMILIES.computeIfAbsent(id, key -> NEXT_FAMILY.getAndIncrement());
    }

    private static Model read(String name, NbtCompound root) throws IOException {
        NbtList palettes = root.getListOrEmpty("palettes");
        if (palettes.size() != 8) throw new IOException(name + ": expected eight palettes");
        NbtList palette = palettes.getListOrEmpty(0);
        for (int p = 1; p < palettes.size(); p++) {
            NbtList other = palettes.getListOrEmpty(p);
            if (other.size() != palette.size()) throw new IOException(name + ": different palette sizes");
            for (int i = 0; i < palette.size(); i++) {
                NbtCompound a = palette.getCompoundOrEmpty(i);
                NbtCompound b = other.getCompoundOrEmpty(i);
                if (family(a.getString("Name", "")) != family(b.getString("Name", ""))
                    || !a.getCompoundOrEmpty("Properties").equals(b.getCompoundOrEmpty("Properties"))) {
                    throw new IOException(name + ": palettes differ beyond wood at " + p + "/" + i);
                }
            }
        }
        List<BlockPos> positions = new ArrayList<>();
        List<Integer> families = new ArrayList<>();
        List<Chest> chests = new ArrayList<>();
        NbtList blocks = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound entry = blocks.getCompoundOrEmpty(i);
            int state = entry.getInt("state", -1);
            if (state < 0 || state >= palette.size()) throw new IOException(name + ": invalid state");
            String id = palette.getCompoundOrEmpty(state).getString("Name", "");
            NbtList pos = entry.getListOrEmpty("pos");
            if (pos.size() != 3) throw new IOException(name + ": invalid position");
            BlockPos local = new BlockPos(pos.getInt(0, 0), pos.getInt(1, 0), pos.getInt(2, 0));
            if (id.equals("minecraft:structure_block")) {
                String type = entry.getCompoundOrEmpty("nbt").getString("metadata", "");
                if (type.equals("supply_chest") || type.equals("map_chest") || type.equals("treasure_chest")) {
                    chests.add(new Chest(type, local.down()));
                }
            } else if (!id.equals("minecraft:air") && !id.equals("minecraft:cave_air") && !id.equals("minecraft:void_air")) {
                positions.add(local);
                families.add(family(id));
            }
        }
        if (positions.isEmpty() || chests.isEmpty()) throw new IOException(name + ": empty model");
        chests.sort(Comparator.comparingInt(c -> chestOrder(c.type)));
        NbtList size = root.getListOrEmpty("size");
        if (size.size() != 3) throw new IOException(name + ": invalid size");
        Variant[] variants = new Variant[4];
        BlockRotation[] rotations = BlockRotation.values();
        for (int r = 0; r < rotations.length; r++) {
            BlockRotation rotation = rotations[r];
            int[] x = new int[positions.size()], y = new int[x.length], z = new int[x.length], f = new int[x.length];
            for (int i = 0; i < x.length; i++) {
                BlockPos transformed = StructureTemplate.transformAround(positions.get(i), BlockMirror.NONE, rotation, PIVOT);
                x[i] = transformed.getX(); y[i] = transformed.getY(); z[i] = transformed.getZ(); f[i] = families.get(i);
            }
            Chest[] rotatedChests = new Chest[chests.size()];
            int[][] probes = new int[chests.size()][];
            for (int c = 0; c < chests.size(); c++) {
                Chest chest = chests.get(c);
                rotatedChests[c] = new Chest(chest.type, StructureTemplate.transformAround(chest.pos, BlockMirror.NONE, rotation, PIVOT));
                List<Integer> nearby = new ArrayList<>();
                for (int i = 0; i < positions.size(); i++) if (f[i] != 9) nearby.add(i);
                BlockPos anchor = chest.pos;
                nearby.sort(Comparator.comparingDouble(i -> positions.get(i).equals(anchor.down()) ? -1 : positions.get(i).getSquaredDistance(anchor)));
                probes[c] = nearby.stream().limit(3).mapToInt(Integer::intValue).toArray();
            }
            BlockPos a = StructureTemplate.transformAround(BlockPos.ORIGIN, BlockMirror.NONE, rotation, PIVOT);
            BlockPos b = StructureTemplate.transformAround(new BlockPos(size.getInt(0, 0) - 1, 0, size.getInt(2, 0) - 1), BlockMirror.NONE, rotation, PIVOT);
            variants[r] = new Variant(rotation, x, y, z, f, rotatedChests, probes,
                Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX()), 0, size.getInt(1, 0) - 1,
                Math.min(a.getZ(), b.getZ()), Math.max(a.getZ(), b.getZ()));
        }
        return new Model(name, variants);
    }

    private static int chestOrder(String type) {
        return type.equals("supply_chest") ? 0 : type.equals("map_chest") ? 1 : 2;
    }

    record Model(String name, Variant[] variants) {}
    record Chest(String type, BlockPos pos) {}
    record Variant(BlockRotation rotation, int[] x, int[] y, int[] z, int[] families,
                   Chest[] chests, int[][] probes, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
}
