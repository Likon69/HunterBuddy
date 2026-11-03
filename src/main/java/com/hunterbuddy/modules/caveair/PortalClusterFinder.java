package com.hunterbuddy.modules.caveair;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

final class PortalClusterFinder {
    private static final int MIN_WIDTH = 4;
    private static final int MIN_HEIGHT = 5;
    private static final int CORNER_COUNT = 4;

    private final CaveAirOptions options;

    PortalClusterFinder(CaveAirOptions options) {
        this.options = options;
    }

    List<CellBox> find(Set<BlockPos> cells) {
        Set<BlockPos> unvisited = new HashSet<>(cells);
        List<CellBox> portals = new ArrayList<>();
        while (!unvisited.isEmpty()) {
            qualify(floodFill(unvisited)).ifPresent(portals::add);
        }
        return portals;
    }

    private Set<BlockPos> floodFill(Set<BlockPos> unvisited) {
        BlockPos start = unvisited.iterator().next();
        unvisited.remove(start);
        Set<BlockPos> cluster = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(start);
        while (!frontier.isEmpty()) {
            BlockPos cell = frontier.poll();
            cluster.add(cell);
            enqueueNeighbours(cell, unvisited, frontier);
        }
        return cluster;
    }

    private void enqueueNeighbours(BlockPos cell, Set<BlockPos> unvisited, Deque<BlockPos> frontier) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = cell.offset(direction);
            if (unvisited.remove(neighbour)) {
                frontier.add(neighbour);
            }
        }
    }

    private Optional<CellBox> qualify(Set<BlockPos> cluster) {
        ClusterBounds bounds = ClusterBounds.of(cluster);
        if (!bounds.isFlatWall() || !fitsPortalSize(bounds)) return Optional.empty();
        return hasPortalCells(cluster, bounds) ? Optional.of(bounds.toBox()) : Optional.empty();
    }

    private boolean fitsPortalSize(ClusterBounds bounds) {
        int width = bounds.wallWidth();
        int height = bounds.extentY();
        return width >= MIN_WIDTH && height >= MIN_HEIGHT
            && width <= options.maxPortalWidth.get() && height <= options.maxPortalHeight.get();
    }

    private boolean hasPortalCells(Set<BlockPos> cluster, ClusterBounds bounds) {
        int area = bounds.wallWidth() * bounds.extentY();
        if (cluster.size() == area) return true;
        return !options.strictPortalShape.get() && cluster.size() == area - CORNER_COUNT && lacksCorners(cluster, bounds);
    }

    private boolean lacksCorners(Set<BlockPos> cluster, ClusterBounds bounds) {
        return bounds.corners().stream().noneMatch(cluster::contains);
    }
}
