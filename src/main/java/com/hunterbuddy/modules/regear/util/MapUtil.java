package com.hunterbuddy.modules.regear.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xaero.map.mods.SupportMods;
import xaeroplus.feature.waypoint.WaypointAPI;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaeroplus.settings.Settings;

/** Reference parity: mlep.util.MapUtil. 100% faithful — direct XaeroPlus imports. */
public class MapUtil {

    public static void addWaypoint(BlockPos pos, String name, String initials, Purpose purpose, WpColor color, boolean temp) {
        if (Utils.XAERO_AVAILABLE) {
            XaeroIntegration.addWaypoint(pos, name, initials, purpose, color, temp, null);
        }
    }

    public static void addWaypoint(BlockPos pos, String name, String initials, Purpose purpose, WpColor color, boolean temp, @Nullable String dimension) {
        if (Utils.XAERO_AVAILABLE) {
            XaeroIntegration.addWaypoint(pos, name, initials, purpose, color, temp, dimension);
        }
    }

    public static void removeWaypoints(String name, Predicate<BlockPos> posPredicate, Optional<Integer> yOverride) {
        if (Utils.XAERO_AVAILABLE) {
            XaeroIntegration.removeWaypoints(name, posPredicate, yOverride);
        }
    }

    public enum Purpose {
        Normal,
        Destination
    }

    public enum WpColor {
        Black, Dark_Blue, Dark_Green, Dark_Aqua, Dark_Red, Dark_Purple,
        Gold, Gray, Dark_Gray, Blue, Green, Aqua, Red, Purple, Yellow, White, Random
    }

    private static class XaeroIntegration {

        static void addWaypoint(BlockPos pos, String name, String initials, Purpose purpose,
                                WpColor color, boolean temp, @Nullable String dimension) {
            try {
                int waypointX = pos.getX();
                int waypointY = pos.getY();
                int waypointZ = pos.getZ();

                MinimapWorld targetWorld = getWaypointWorld();

                if (usesNetherOverworldScaling(dimension)) {
                    waypointX = pos.getX() * 8;
                    waypointZ = pos.getZ() * 8;
                    MinimapWorld overworld = findOverworldMinimapWorld();
                    if (overworld != null) {
                        targetWorld = overworld;
                    }
                }

                if (targetWorld == null) {
                    LogUtil.warn("Cancelling waypoint \"" + name + "\" because target world is null", "MapUtil");
                    return;
                }

                WaypointSet set = targetWorld.getCurrentWaypointSet();
                if (set == null) {
                    LogUtil.warn("Cancelling waypoint \"" + name + "\" because waypoint set is null", "MapUtil");
                    return;
                }

                for (Waypoint wp : set.getWaypoints()) {
                    if (wp.getX() == waypointX && wp.getZ() == waypointZ) {
                        LogUtil.warn("Skipping duplicate waypoint: " + name, "MapUtil");
                        return;
                    }
                }

                Waypoint waypoint = new Waypoint(
                    waypointX, waypointY, waypointZ,
                    name, initials,
                    getColor(color),
                    getPurpose(purpose),
                    temp
                );

                set.add(waypoint);
                saveWaypoints(targetWorld);
                requestWaypointsRefresh();

            } catch (Exception e) {
                LogUtil.error("Error adding waypoint to Xaero: " + e.getMessage(), "MapUtil");
            }
        }

        static void removeWaypoints(String name, Predicate<BlockPos> posPredicate, Optional<Integer> yOverride) {
            try {
                boolean removed;
                if (usesNetherOverworldScalingForCurrentWorld()) {
                    removed = removeWaypointsFromWorld(findOverworldMinimapWorld(), name, posPredicate, yOverride, true);
                    removed |= removeWaypointsFromWorld(getWaypointWorld(), name, posPredicate, yOverride, false);
                } else {
                    removed = removeWaypointsFromWorld(getWaypointWorld(), name, posPredicate, yOverride, false);
                }

                if (removed) {
                    requestWaypointsRefresh();
                }
            } catch (Exception e) {
                LogUtil.error("Error removing waypoints from Xaero: " + e.getMessage(), "MapUtil");
            }
        }

        static boolean removeWaypointsFromWorld(@Nullable MinimapWorld targetWorld, String name,
                                                Predicate<BlockPos> posPredicate, Optional<Integer> yOverride,
                                                boolean unscaleStoredCoords) {
            if (targetWorld == null) {
                return false;
            }

            WaypointSet set = targetWorld.getCurrentWaypointSet();
            if (set == null) {
                return false;
            }

            BlockPos.Mutable mutablePos = new BlockPos.Mutable();
            List<Waypoint> toRemove = new ObjectArrayList<>();

            for (Waypoint wp : set.getWaypoints()) {
                if (wp.getName().trim().startsWith(name.trim())) {
                    int checkX = unscaleStoredCoords ? wp.getX() / 8 : wp.getX();
                    int checkZ = unscaleStoredCoords ? wp.getZ() / 8 : wp.getZ();
                    int y = yOverride.orElseGet(wp::getY);
                    mutablePos.set(checkX, y, checkZ);

                    if (posPredicate.test(mutablePos)) {
                        toRemove.add(wp);
                    }
                }
            }

            if (toRemove.isEmpty()) {
                return false;
            }

            for (Waypoint wp : toRemove) {
                set.remove(wp);
            }

            saveWaypoints(targetWorld);
            return true;
        }

        static void requestWaypointsRefresh() {
            try {
                SupportMods.xaeroMinimap.requestWaypointsRefresh();
            } catch (Exception ignored) {}
        }

        static boolean usesNetherOverworldScaling(@Nullable String dimension) {
            if (dimension == null || !dimension.equals("the_nether")) {
                return false;
            }
            try {
                return Settings.REGISTRY.owAutoWaypointDimension.get();
            } catch (Exception ignored) {
                return false;
            }
        }

        static boolean usesNetherOverworldScalingForCurrentWorld() {
            if (MeteorClient.mc.world == null) {
                return false;
            }
            return usesNetherOverworldScaling(MeteorClient.mc.world.getRegistryKey().getValue().getPath());
        }

        @Nullable
        static MinimapWorld findOverworldMinimapWorld() {
            try {
                MinimapWorld overworld = WaypointAPI.getMinimapWorld(World.OVERWORLD);
                if (overworld != null) {
                    return overworld;
                }
            } catch (Exception ignored) {}

            try {
                MinimapSession session = getMinimapSession();
                if (session == null) {
                    return null;
                }
                MinimapWorldRootContainer rootContainer = session.getWorldManager().getCurrentRootContainer();
                if (rootContainer == null) {
                    return null;
                }
                for (MinimapWorld world : rootContainer.getWorlds()) {
                    try {
                        String dimPath = world.getDimId().getValue().getPath();
                        if ("overworld".equals(dimPath)) {
                            return world;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            return null;
        }

        @Nullable
        static MinimapSession getMinimapSession() {
            return (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
        }

        @Nullable
        static MinimapWorld getWaypointWorld() {
            MinimapSession session = getMinimapSession();
            return session == null ? null : session.getWorldManager().getCurrentWorld();
        }

        @Nullable
        static WaypointSet getWaypointSet() {
            MinimapWorld world = getWaypointWorld();
            return world == null ? null : world.getCurrentWaypointSet();
        }

        static void saveWaypoints() {
            saveWaypoints(getWaypointWorld());
        }

        static void saveWaypoints(@Nullable MinimapWorld world) {
            try {
                MinimapSession session = getMinimapSession();
                if (world != null && session != null) {
                    session.getWorldManagerIO().saveWorld(world);
                }
            } catch (Exception e) {
                LogUtil.error("Failed to save Xaero waypoints: " + e.getMessage(), "MapUtil");
            }
        }

        static WaypointPurpose getPurpose(Purpose purpose) {
            return switch (purpose) {
                case Normal -> WaypointPurpose.NORMAL;
                case Destination -> WaypointPurpose.DESTINATION;
            };
        }

        static WaypointColor getColor(WpColor color) {
            return switch (color) {
                case Black -> WaypointColor.BLACK;
                case Dark_Blue -> WaypointColor.DARK_BLUE;
                case Dark_Green -> WaypointColor.DARK_GREEN;
                case Dark_Aqua -> WaypointColor.DARK_AQUA;
                case Dark_Red -> WaypointColor.DARK_RED;
                case Dark_Purple -> WaypointColor.DARK_PURPLE;
                case Gold -> WaypointColor.GOLD;
                case Gray -> WaypointColor.GRAY;
                case Dark_Gray -> WaypointColor.DARK_GRAY;
                case Blue -> WaypointColor.BLUE;
                case Green -> WaypointColor.GREEN;
                case Aqua -> WaypointColor.AQUA;
                case Red -> WaypointColor.RED;
                case Purple -> WaypointColor.PURPLE;
                case Yellow -> WaypointColor.YELLOW;
                case White -> WaypointColor.WHITE;
                case Random -> WaypointColor.getRandom();
            };
        }
    }
}