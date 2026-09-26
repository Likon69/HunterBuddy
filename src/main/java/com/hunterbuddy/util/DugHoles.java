package com.hunterbuddy.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

public final class DugHoles {

    public interface BlockSource {
        BlockState get(int x, int y, int z);
    }

    public enum Kind { TUNNEL, SHAFT, STAIRS, POCKET }

    public record Params(int minTunnelLength, int minShaftDepth, int minStairSteps, int maxPocketSize,
                          int minCover, boolean tunnels, boolean shafts, boolean stairs, boolean pockets) {}

    public record Feature(Kind kind, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean confirmed) {}

    private static final byte CLS_AIR = 0;
    private static final byte CLS_CAVE = 1;
    private static final byte CLS_WATER = 2;
    private static final byte CLS_LAVA = 3;
    private static final byte CLS_PASS = 4;
    private static final byte CLS_SOLID = 5;
    private static final int CLASS_MASK = 0x07;
    private static final int GEODE_BIT = 0x40;

    private static final Set<Block> GEODE_BLOCKS = Set.of(
        Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.AMETHYST_CLUSTER,
        Blocks.LARGE_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.SMALL_AMETHYST_BUD,
        Blocks.CALCITE, Blocks.SMOOTH_BASALT
    );

    private static volatile byte[] classCache = new byte[Math.max(1, Block.STATE_IDS.size())];

    private static final ThreadLocal<byte[]> GRID_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<int[]> SURFACE_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<boolean[]> USED_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<boolean[]> VISITED_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<int[]> STACK_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<boolean[]> AVAIL_BUFFER = new ThreadLocal<>();

    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, -1, 1};

    private DugHoles() {}

    public static List<Feature> analyze(BlockSource src, int minX, int minZ, int sizeX, int sizeZ, int pad,
                                         int minY, int maxY, Params p) {
        if (sizeX <= 0 || sizeZ <= 0 || maxY < minY) return List.of();

        int originX = minX - pad;
        int originZ = minZ - pad;
        int width = sizeX + 2 * pad;
        int depth = sizeZ + 2 * pad;
        int height = maxY - minY + 1;
        if (width <= 0 || depth <= 0 || height <= 0) return List.of();

        long cellCount = (long) width * depth * height;
        if (cellCount <= 0 || cellCount > Integer.MAX_VALUE - 16) return List.of();
        int total = (int) cellCount;

        byte[] grid = GRID_BUFFER.get();
        if (grid == null || grid.length < total) {
            grid = new byte[total];
            GRID_BUFFER.set(grid);
        }

        for (int gx = 0; gx < width; gx++) {
            int wx = originX + gx;
            for (int gz = 0; gz < depth; gz++) {
                int wz = originZ + gz;
                int base = (gx * depth + gz) * height;
                for (int gy = 0; gy < height; gy++) {
                    grid[base + gy] = classify(src.get(wx, minY + gy, wz));
                }
            }
        }

        int surfaceSize = width * depth;
        int[] surface = SURFACE_BUFFER.get();
        if (surface == null || surface.length < surfaceSize) {
            surface = new int[surfaceSize];
            SURFACE_BUFFER.set(surface);
        }
        for (int gx = 0; gx < width; gx++) {
            for (int gz = 0; gz < depth; gz++) {
                int base = (gx * depth + gz) * height;
                int top = minY - 1;
                for (int gy = height - 1; gy >= 0; gy--) {
                    if ((grid[base + gy] & CLASS_MASK) == CLS_SOLID) {
                        top = minY + gy;
                        break;
                    }
                }
                surface[gx * depth + gz] = top;
            }
        }

        List<Feature> raw = new ArrayList<>();
        boolean[] used = USED_BUFFER.get();
        if (used == null || used.length < total) {
            used = new boolean[total];
            USED_BUFFER.set(used);
        } else {
            Arrays.fill(used, 0, total, false);
        }

        Ctx ctx = new Ctx(grid, width, depth, height, minY, originX, originZ, surface, p, raw, used);

        ctx.findTunnels();
        ctx.findShafts();
        ctx.findStairs();
        ctx.findPockets();

        if (raw.isEmpty()) return List.of();

        int centerMaxX = minX + sizeX - 1;
        int centerMaxZ = minZ + sizeZ - 1;
        List<Feature> result = new ArrayList<>();

        for (Feature f : raw) {
            if (result.size() >= 256) break;

            int cx0 = Math.max(f.minX(), minX);
            int cx1 = Math.min(f.maxX(), centerMaxX);
            if (cx0 > cx1) continue;

            int cz0 = Math.max(f.minZ(), minZ);
            int cz1 = Math.min(f.maxZ(), centerMaxZ);
            if (cz0 > cz1) continue;

            result.add(new Feature(f.kind(), cx0, f.minY(), cz0, cx1, f.maxY(), cz1, f.confirmed()));
        }

        return result;
    }

    public static int cellClass(BlockState state) {
        return classify(state) & CLASS_MASK;
    }

    private static byte classify(BlockState state) {
        if (state == null || state.isOf(Blocks.VOID_AIR)) return CLS_SOLID;

        int id = Block.getRawIdFromState(state);
        byte[] table = classCache;
        if (id >= table.length) table = growClassCache(id);

        int stored = table[id] & 0xFF;
        if (stored != 0) {
            int cls = (stored & CLASS_MASK) - 1;
            return (byte) (cls | (stored & GEODE_BIT));
        }

        byte cls = computeClass(state);
        boolean geode = GEODE_BLOCKS.contains(state.getBlock());
        int packed = (cls + 1) | (geode ? GEODE_BIT : 0);
        table[id] = (byte) packed;
        return (byte) (cls | (geode ? GEODE_BIT : 0));
    }

    private static synchronized byte[] growClassCache(int minId) {
        byte[] table = classCache;
        if (minId < table.length) return table;
        byte[] grown = new byte[minId + 64];
        System.arraycopy(table, 0, grown, 0, table.length);
        classCache = grown;
        return grown;
    }

    private static byte computeClass(BlockState state) {
        if (state.isOf(Blocks.AIR)) return CLS_AIR;
        if (state.isOf(Blocks.CAVE_AIR)) return CLS_CAVE;

        FluidState fluid = state.getFluidState();
        boolean collisionEmpty = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty();

        if (!fluid.isEmpty()) {
            if (collisionEmpty && fluid.isIn(FluidTags.WATER)) return CLS_WATER;
            if (collisionEmpty && fluid.isIn(FluidTags.LAVA)) return CLS_LAVA;
            return CLS_SOLID;
        }

        return collisionEmpty ? CLS_PASS : CLS_SOLID;
    }

    private static boolean isOpenGeneral(int cls) {
        return cls == CLS_AIR || cls == CLS_CAVE || cls == CLS_PASS;
    }

    private static boolean isOpenShaft(int cls) {
        return cls == CLS_AIR || cls == CLS_CAVE || cls == CLS_PASS || cls == CLS_WATER;
    }

    private record MaterialStats(boolean vetoed, boolean confirmed) {}

    private static final class Ctx {
        final byte[] grid;
        final int width, depth, height, worldMinY, originX, originZ;
        final int[] surface;
        final Params p;
        final List<Feature> raw;
        final boolean[] used;

        Ctx(byte[] grid, int width, int depth, int height, int worldMinY, int originX, int originZ,
            int[] surface, Params p, List<Feature> raw, boolean[] used) {
            this.grid = grid;
            this.width = width;
            this.depth = depth;
            this.height = height;
            this.worldMinY = worldMinY;
            this.originX = originX;
            this.originZ = originZ;
            this.surface = surface;
            this.p = p;
            this.raw = raw;
            this.used = used;
        }

        int index(int gx, int gz, int gy) {
            return (gx * depth + gz) * height + gy;
        }

        int classAt(int gx, int gz, int gy) {
            return grid[index(gx, gz, gy)] & CLASS_MASK;
        }

        int localSurfaceGeneral(int gx, int gz) {
            int local = surface[gx * depth + gz];
            if (gx > 0) local = Math.max(local, surface[(gx - 1) * depth + gz]);
            if (gx < width - 1) local = Math.max(local, surface[(gx + 1) * depth + gz]);
            if (gz > 0) local = Math.max(local, surface[gx * depth + gz - 1]);
            if (gz < depth - 1) local = Math.max(local, surface[gx * depth + gz + 1]);
            return local;
        }

        boolean undergroundGeneral(int gx, int gz, int gy) {
            int local = localSurfaceGeneral(gx, gz);
            return (local - (worldMinY + gy)) >= p.minCover();
        }

        boolean undergroundNeighborsOnly(int gx, int gz, int gy) {
            int local = Integer.MIN_VALUE;
            boolean any = false;
            if (gx > 0) { local = Math.max(local, surface[(gx - 1) * depth + gz]); any = true; }
            if (gx < width - 1) { local = Math.max(local, surface[(gx + 1) * depth + gz]); any = true; }
            if (gz > 0) { local = Math.max(local, surface[gx * depth + gz - 1]); any = true; }
            if (gz < depth - 1) { local = Math.max(local, surface[gx * depth + gz + 1]); any = true; }
            if (!any) return false;
            return (local - (worldMinY + gy)) >= p.minCover();
        }

        boolean neighborTouchesCave(int gx, int gz, int gy) {
            if (gx > 0 && classAt(gx - 1, gz, gy) == CLS_CAVE) return true;
            if (gx < width - 1 && classAt(gx + 1, gz, gy) == CLS_CAVE) return true;
            if (gz > 0 && classAt(gx, gz - 1, gy) == CLS_CAVE) return true;
            if (gz < depth - 1 && classAt(gx, gz + 1, gy) == CLS_CAVE) return true;
            if (gy > 0 && classAt(gx, gz, gy - 1) == CLS_CAVE) return true;
            if (gy < height - 1 && classAt(gx, gz, gy + 1) == CLS_CAVE) return true;
            return false;
        }

        MaterialStats evaluate(int[] gxs, int[] gzs, int[] gys, int count) {
            int cave = 0, air = 0;
            boolean touchesCave = false;
            for (int i = 0; i < count; i++) {
                int cls = classAt(gxs[i], gzs[i], gys[i]);
                if (cls == CLS_CAVE) cave++;
                else if (cls == CLS_AIR) air++;
                if (!touchesCave && neighborTouchesCave(gxs[i], gzs[i], gys[i])) touchesCave = true;
            }
            boolean vetoed = count > 0 && cave * 2 > count;
            boolean confirmed = !vetoed && count > 0 && air * 2 > count && touchesCave;
            return new MaterialStats(vetoed, confirmed);
        }

        void findTunnels() {
            if (!p.tunnels()) return;
            int minRun = p.minTunnelLength();

            for (int y = 1; y <= height - 3; y++) {
                for (int z = 1; z < depth - 1; z++) {
                    int runStart = -1;
                    for (int x = 0; x < width; x++) {
                        if (tunnelCellValid(x, y, z, true)) {
                            if (runStart < 0) runStart = x;
                        } else {
                            if (runStart >= 0) emitTunnelRun(z, y, runStart, x - 1, true, minRun);
                            runStart = -1;
                        }
                    }
                    if (runStart >= 0) emitTunnelRun(z, y, runStart, width - 1, true, minRun);
                }
            }

            for (int y = 1; y <= height - 3; y++) {
                for (int x = 1; x < width - 1; x++) {
                    int runStart = -1;
                    for (int z = 0; z < depth; z++) {
                        if (tunnelCellValid(x, y, z, false)) {
                            if (runStart < 0) runStart = z;
                        } else {
                            if (runStart >= 0) emitTunnelRun(x, y, runStart, z - 1, false, minRun);
                            runStart = -1;
                        }
                    }
                    if (runStart >= 0) emitTunnelRun(x, y, runStart, depth - 1, false, minRun);
                }
            }
        }

        boolean tunnelCellValid(int x, int y, int z, boolean alongX) {
            int c0 = classAt(x, z, y);
            int c1 = classAt(x, z, y + 1);
            if (!isOpenGeneral(c0) || !isOpenGeneral(c1)) return false;
            if (classAt(x, z, y - 1) != CLS_SOLID) return false;
            if (classAt(x, z, y + 2) != CLS_SOLID) return false;

            if (alongX) {
                if (z - 1 < 0 || z + 1 >= depth) return false;
                if (classAt(x, z - 1, y) != CLS_SOLID) return false;
                if (classAt(x, z + 1, y) != CLS_SOLID) return false;
                if (classAt(x, z - 1, y + 1) != CLS_SOLID) return false;
                if (classAt(x, z + 1, y + 1) != CLS_SOLID) return false;
            } else {
                if (x - 1 < 0 || x + 1 >= width) return false;
                if (classAt(x - 1, z, y) != CLS_SOLID) return false;
                if (classAt(x + 1, z, y) != CLS_SOLID) return false;
                if (classAt(x - 1, z, y + 1) != CLS_SOLID) return false;
                if (classAt(x + 1, z, y + 1) != CLS_SOLID) return false;
            }

            return undergroundGeneral(x, z, y + 1);
        }

        void emitTunnelRun(int fixed, int y, int runStart, int runEnd, boolean alongX, int minRun) {
            int len = runEnd - runStart + 1;
            if (len < minRun) return;

            int count = len * 2;
            int[] gxs = new int[count];
            int[] gzs = new int[count];
            int[] gys = new int[count];
            int idx = 0;
            for (int v = runStart; v <= runEnd; v++) {
                int gx = alongX ? v : fixed;
                int gz = alongX ? fixed : v;
                gxs[idx] = gx; gzs[idx] = gz; gys[idx] = y; idx++;
                gxs[idx] = gx; gzs[idx] = gz; gys[idx] = y + 1; idx++;
            }

            MaterialStats stats = evaluate(gxs, gzs, gys, count);
            if (stats.vetoed()) return;

            for (int i = 0; i < count; i++) used[index(gxs[i], gzs[i], gys[i])] = true;

            int minXw, maxXw, minZw, maxZw;
            if (alongX) {
                minXw = originX + runStart; maxXw = originX + runEnd;
                minZw = maxZw = originZ + fixed;
            } else {
                minZw = originZ + runStart; maxZw = originZ + runEnd;
                minXw = maxXw = originX + fixed;
            }

            raw.add(new Feature(Kind.TUNNEL, minXw, worldMinY + y, minZw, maxXw, worldMinY + y + 1, maxZw, stats.confirmed()));
        }

        void findShafts() {
            if (!p.shafts()) return;
            int minRun = p.minShaftDepth();

            for (int x = 1; x < width - 1; x++) {
                for (int z = 1; z < depth - 1; z++) {
                    int runStart = -1;
                    for (int y = 0; y < height; y++) {
                        if (shaftCellValid(x, y, z)) {
                            if (runStart < 0) runStart = y;
                        } else {
                            if (runStart >= 0) emitShaftRun(x, z, runStart, y - 1, minRun);
                            runStart = -1;
                        }
                    }
                    if (runStart >= 0) emitShaftRun(x, z, runStart, height - 1, minRun);
                }
            }
        }

        boolean shaftCellValid(int x, int y, int z) {
            if (!isOpenShaft(classAt(x, z, y))) return false;
            if (classAt(x - 1, z, y) != CLS_SOLID) return false;
            if (classAt(x + 1, z, y) != CLS_SOLID) return false;
            if (classAt(x, z - 1, y) != CLS_SOLID) return false;
            if (classAt(x, z + 1, y) != CLS_SOLID) return false;
            return undergroundNeighborsOnly(x, z, y);
        }

        void emitShaftRun(int x, int z, int yStart, int yEnd, int minRun) {
            int len = yEnd - yStart + 1;
            if (len < minRun) return;

            int[] gxs = new int[len];
            int[] gzs = new int[len];
            int[] gys = new int[len];
            for (int i = 0; i < len; i++) { gxs[i] = x; gzs[i] = z; gys[i] = yStart + i; }

            MaterialStats stats = evaluate(gxs, gzs, gys, len);
            if (stats.vetoed()) return;

            for (int i = 0; i < len; i++) used[index(x, z, yStart + i)] = true;

            raw.add(new Feature(Kind.SHAFT, originX + x, worldMinY + yStart, originZ + z,
                originX + x, worldMinY + yEnd, originZ + z, stats.confirmed()));
        }

        void findStairs() {
            if (!p.stairs()) return;
            for (int z = 1; z < depth - 1; z++) findStairsColumnSet(z, true);
            for (int x = 1; x < width - 1; x++) findStairsColumnSet(x, false);
        }

        @SuppressWarnings("unchecked")
        void findStairsColumnSet(int fixed, boolean alongX) {
            int primaryLen = alongX ? width : depth;
            List<int[]>[] segs = new List[primaryLen];
            for (int pos = 0; pos < primaryLen; pos++) segs[pos] = columnSegments(pos, fixed, alongX);

            boolean[][] consumed = new boolean[primaryLen][];
            for (int pos = 0; pos < primaryLen; pos++) consumed[pos] = new boolean[segs[pos].size()];

            for (int pos = 0; pos < primaryLen; pos++) {
                for (int si = 0; si < segs[pos].size(); si++) {
                    if (consumed[pos][si]) continue;
                    tryStairChain(segs, consumed, primaryLen, pos, si, 1, fixed, alongX);
                    tryStairChain(segs, consumed, primaryLen, pos, si, -1, fixed, alongX);
                }
            }
        }

        List<int[]> columnSegments(int pos, int fixed, boolean alongX) {
            int gx = alongX ? pos : fixed;
            int gz = alongX ? fixed : pos;
            List<int[]> segs = new ArrayList<>();

            int y = 0;
            while (y < height) {
                if (classAt(gx, gz, y) != CLS_SOLID) { y++; continue; }

                int f = y;
                int oy = f + 1;
                while (oy < height && isOpenGeneral(classAt(gx, gz, oy))) oy++;
                if (oy >= height) break;

                if (classAt(gx, gz, oy) == CLS_SOLID) {
                    int c = oy;
                    int openHeight = c - f - 1;
                    if (openHeight >= 2 && openHeight <= 4
                        && sideWallsSolid(gx, gz, f + 1, c - 1, alongX)
                        && undergroundGeneral(gx, gz, c - 1)) {
                        segs.add(new int[]{f, c});
                    }
                    y = c;
                } else {
                    y = oy + 1;
                }
            }

            return segs;
        }

        boolean sideWallsSolid(int gx, int gz, int yFrom, int yTo, boolean alongX) {
            if (alongX) {
                if (gz - 1 < 0 || gz + 1 >= depth) return false;
            } else {
                if (gx - 1 < 0 || gx + 1 >= width) return false;
            }

            for (int y = yFrom; y <= yTo; y++) {
                if (alongX) {
                    if (classAt(gx, gz - 1, y) != CLS_SOLID) return false;
                    if (classAt(gx, gz + 1, y) != CLS_SOLID) return false;
                } else {
                    if (classAt(gx - 1, gz, y) != CLS_SOLID) return false;
                    if (classAt(gx + 1, gz, y) != CLS_SOLID) return false;
                }
            }

            return true;
        }

        void tryStairChain(List<int[]>[] segs, boolean[][] consumed, int primaryLen, int startPos, int startSi,
                            int slope, int fixed, boolean alongX) {
            if (consumed[startPos][startSi]) return;

            List<Integer> chainPos = new ArrayList<>();
            List<Integer> chainSi = new ArrayList<>();

            int pos = startPos, si = startSi;
            int floor = segs[pos].get(si)[0];
            chainPos.add(pos);
            chainSi.add(si);

            while (pos + 1 < primaryLen) {
                int wantFloor = floor + slope;
                List<int[]> cand = segs[pos + 1];
                int nextSi = -1;
                for (int k = 0; k < cand.size(); k++) {
                    if (!consumed[pos + 1][k] && cand.get(k)[0] == wantFloor) { nextSi = k; break; }
                }
                if (nextSi < 0) break;

                pos = pos + 1;
                si = nextSi;
                floor = wantFloor;
                chainPos.add(pos);
                chainSi.add(si);
            }

            int n = chainPos.size();
            if (n < p.minStairSteps()) return;

            List<Integer> cellGx = new ArrayList<>();
            List<Integer> cellGz = new ArrayList<>();
            List<Integer> cellGy = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                int cp = chainPos.get(i);
                int cs = chainSi.get(i);
                int[] seg = segs[cp].get(cs);
                int gx = alongX ? cp : fixed;
                int gz = alongX ? fixed : cp;
                for (int gy = seg[0] + 1; gy <= seg[1] - 1; gy++) {
                    cellGx.add(gx); cellGz.add(gz); cellGy.add(gy);
                }
            }

            int count = cellGx.size();
            int[] axs = new int[count];
            int[] azs = new int[count];
            int[] ays = new int[count];
            for (int i = 0; i < count; i++) { axs[i] = cellGx.get(i); azs[i] = cellGz.get(i); ays[i] = cellGy.get(i); }

            MaterialStats stats = evaluate(axs, azs, ays, count);
            if (stats.vetoed()) return;

            for (int i = 0; i < n; i++) {
                int cp = chainPos.get(i);
                int cs = chainSi.get(i);
                consumed[cp][cs] = true;

                int[] seg = segs[cp].get(cs);
                int gx = alongX ? cp : fixed;
                int gz = alongX ? fixed : cp;
                for (int gy = seg[0] + 1; gy <= seg[1] - 1; gy++) used[index(gx, gz, gy)] = true;

                int wx = originX + gx, wz = originZ + gz;
                raw.add(new Feature(Kind.STAIRS, wx, worldMinY + seg[0] + 1, wz,
                    wx, worldMinY + seg[1] - 1, wz, stats.confirmed()));
            }
        }

        void findPockets() {
            if (!p.pockets()) return;

            int total = width * depth * height;
            boolean[] visited = VISITED_BUFFER.get();
            if (visited == null || visited.length < total) {
                visited = new boolean[total];
                VISITED_BUFFER.set(visited);
            } else {
                Arrays.fill(visited, 0, total, false);
            }
            int maxSize = p.maxPocketSize();
            int[] stack = STACK_BUFFER.get();
            if (stack == null || stack.length < total) {
                stack = new int[total];
                STACK_BUFFER.set(stack);
            }
            List<Integer> componentIdx = new ArrayList<>();
            List<Integer> validIdx = new ArrayList<>();

            for (int gx = 0; gx < width; gx++) {
                for (int gz = 0; gz < depth; gz++) {
                    for (int gy = 0; gy < height; gy++) {
                        int idx = index(gx, gz, gy);
                        if (visited[idx] || used[idx]) continue;

                        int cls = grid[idx] & CLASS_MASK;
                        if (cls != CLS_AIR || !undergroundGeneral(gx, gz, gy)) {
                            visited[idx] = true;
                            continue;
                        }

                        componentIdx.clear();
                        int sp = 0;
                        stack[sp++] = idx;
                        visited[idx] = true;

                        boolean touchesEdge = false;
                        boolean touchesCave = false;
                        boolean badNeighbor = false;

                        while (sp > 0) {
                            int cur = stack[--sp];
                            componentIdx.add(cur);

                            int cgy = cur % height;
                            int rem = cur / height;
                            int cgz = rem % depth;
                            int cgx = rem / depth;

                            if (cgx == 0 || cgx == width - 1 || cgz == 0 || cgz == depth - 1) touchesEdge = true;

                            for (int d = 0; d < 6; d++) {
                                int ngx = cgx + DX[d];
                                int ngz = cgz + DZ[d];
                                int ngy = cgy + DY[d];
                                if (ngx < 0 || ngx >= width || ngz < 0 || ngz >= depth || ngy < 0 || ngy >= height) continue;

                                int nidx = index(ngx, ngz, ngy);
                                int ncls = grid[nidx] & CLASS_MASK;
                                boolean ngeode = (grid[nidx] & GEODE_BIT) != 0;

                                if (ncls == CLS_CAVE) touchesCave = true;
                                if (ncls == CLS_WATER || ncls == CLS_LAVA || ngeode) badNeighbor = true;

                                if (ncls == CLS_AIR && !used[nidx] && !visited[nidx] && undergroundGeneral(ngx, ngz, ngy)) {
                                    visited[nidx] = true;
                                    stack[sp++] = nidx;
                                }
                            }
                        }

                        if (componentIdx.size() > maxSize) continue;
                        if (touchesEdge) continue;
                        if (!touchesCave) continue;
                        if (badNeighbor) continue;

                        validIdx.addAll(componentIdx);
                    }
                }
            }

            if (validIdx.isEmpty()) return;

            boolean[] avail = AVAIL_BUFFER.get();
            if (avail == null || avail.length < total) {
                avail = new boolean[total];
                AVAIL_BUFFER.set(avail);
            } else {
                Arrays.fill(avail, 0, total, false);
            }
            for (int idx : validIdx) avail[idx] = true;

            greedyMeshPockets(avail);
        }

        void greedyMeshPockets(boolean[] avail) {
            for (int gx = 0; gx < width; gx++) {
                for (int gz = 0; gz < depth; gz++) {
                    for (int gy = 0; gy < height; gy++) {
                        int idx = index(gx, gz, gy);
                        if (!avail[idx]) continue;

                        int endX = gx;
                        while (endX + 1 < width && avail[index(endX + 1, gz, gy)]) endX++;

                        int endZ = gz;
                        boolean canExpandZ = true;
                        while (canExpandZ && endZ + 1 < depth) {
                            for (int x = gx; x <= endX; x++) {
                                if (!avail[index(x, endZ + 1, gy)]) { canExpandZ = false; break; }
                            }
                            if (canExpandZ) endZ++;
                        }

                        int endY = gy;
                        boolean canExpandY = true;
                        while (canExpandY && endY + 1 < height) {
                            boolean ok = true;
                            for (int x = gx; x <= endX && ok; x++) {
                                for (int z = gz; z <= endZ; z++) {
                                    if (!avail[index(x, z, endY + 1)]) { ok = false; break; }
                                }
                            }
                            canExpandY = ok;
                            if (canExpandY) endY++;
                        }

                        for (int x = gx; x <= endX; x++)
                            for (int z = gz; z <= endZ; z++)
                                for (int y = gy; y <= endY; y++)
                                    avail[index(x, z, y)] = false;

                        raw.add(new Feature(Kind.POCKET,
                            originX + gx, worldMinY + gy, originZ + gz,
                            originX + endX, worldMinY + endY, originZ + endZ,
                            true));
                    }
                }
            }
        }
    }
}
