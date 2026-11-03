package com.hunterbuddy.modules.regear.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaeroplus.feature.waypoint.WaypointAPI;
import xaeroplus.settings.Settings;

public class MapUtil {
    private static final String TAG = "MapUtil";
    private static final String NETHER = "the_nether";
    private static final String OVERWORLD = "overworld";
    private static final int NETHER_SCALE = 8;

    public static void addWaypoint(BlockPos pos, String name, String initials, Purpose purpose, WpColor color, boolean temp) {
        addWaypoint(pos, name, initials, purpose, color, temp, null);
    }

    public static void addWaypoint(BlockPos pos, String name, String initials, Purpose purpose, WpColor color, boolean temp, String dimension) {
        if (!Utils.XAERO_AVAILABLE) return;
        XaeroBackend.add(pos, new Label(name, initials), new Style(purpose, color, temp), dimension);
    }

    public static void removeWaypoints(String name, Predicate<BlockPos> posPredicate, Optional<Integer> yOverride) {
        if (!Utils.XAERO_AVAILABLE) return;
        XaeroBackend.remove(name, posPredicate, yOverride);
    }

    public enum Purpose {
        Normal,
        Destination
    }

    public enum WpColor {
        Black,
        Dark_Blue,
        Dark_Green,
        Dark_Aqua,
        Dark_Red,
        Dark_Purple,
        Gold,
        Gray,
        Dark_Gray,
        Blue,
        Green,
        Aqua,
        Red,
        Purple,
        Yellow,
        White,
        Random
    }

    private record Label(String name, String initials) {
    }

    private record Style(Purpose purpose, WpColor color, boolean temp) {
    }

    private static final class XaeroBackend {
        private record Removal(String name, Predicate<BlockPos> positionTest, Optional<Integer> yOverride) {
            boolean matches(Waypoint waypoint, int divisor) {
                if (!waypoint.getName().trim().startsWith(name.trim())) return false;
                int y = yOverride.orElse(waypoint.getY());
                return positionTest.test(new BlockPos(waypoint.getX() / divisor, y, waypoint.getZ() / divisor));
            }
        }

        private static MinimapSession session() {
            return BuiltInHudModules.MINIMAP.getCurrentSession();
        }

        private static MinimapWorld currentWorld() {
            MinimapSession session = session();
            return session == null ? null : session.getWorldManager().getCurrentWorld();
        }

        private static boolean scalesNetherToOverworld() {
            try {
                return Settings.REGISTRY.owAutoWaypointDimension.get();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static MinimapWorld overworldWorld(MinimapSession session, MinimapWorld fallback) {
            try {
                MinimapWorld world = WaypointAPI.getMinimapWorld(World.OVERWORLD);
                if (world != null) return world;
            } catch (Throwable ignored) {
            }
            try {
                for (MinimapWorld world : session.getWorldManager().getCurrentRootContainer().getAllWorldsIterable()) {
                    if (OVERWORLD.equals(world.getDimId().getValue().getPath())) return world;
                }
            } catch (Throwable ignored) {
            }
            return fallback;
        }

        private static void add(BlockPos pos, Label label, Style style, String dimension) {
            try {
                place(pos, label, style, dimension);
            } catch (Throwable t) {
                LogUtil.error("Could not add waypoint '" + label.name() + "': " + t, TAG);
            }
        }

        private static void place(BlockPos pos, Label label, Style style, String dimension) throws Exception {
            boolean scaled = NETHER.equals(dimension) && scalesNetherToOverworld();
            MinimapWorld world = scaled ? overworldWorld(session(), currentWorld()) : currentWorld();
            int factor = scaled ? NETHER_SCALE : 1;
            addTo(world, new Waypoint(pos.getX() * factor, pos.getY(), pos.getZ() * factor, label.name(), label.initials(),
                toColor(style.color()), toPurpose(style.purpose()), style.temp()));
        }

        private static void addTo(MinimapWorld world, Waypoint waypoint) throws Exception {
            WaypointSet set = world == null ? null : world.getCurrentWaypointSet();
            if (set == null) {
                LogUtil.warn("No waypoint set available for '" + waypoint.getName() + "'", TAG);
            } else if (hasWaypointAt(set, waypoint.getX(), waypoint.getZ())) {
                LogUtil.warn("Waypoint '" + waypoint.getName() + "' skipped, one already exists at " + waypoint.getX() + ", "
                    + waypoint.getZ(), TAG);
            } else {
                set.add(waypoint);
                session().getWorldManagerIO().saveWorld(world);
                refresh();
            }
        }

        private static boolean hasWaypointAt(WaypointSet set, int x, int z) {
            for (Waypoint existing : set.getWaypoints()) {
                if (existing.getX() == x && existing.getZ() == z) return true;
            }
            return false;
        }

        private static void remove(String name, Predicate<BlockPos> positionTest, Optional<Integer> yOverride) {
            Removal removal = new Removal(name, positionTest, yOverride);
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null) return;
                if (removeFromWorlds(mc, removal)) refresh();
            } catch (Throwable t) {
                LogUtil.error("Could not remove waypoints '" + removal.name() + "': " + t, TAG);
            }
        }

        private static boolean removeFromWorlds(MinecraftClient mc, Removal removal) throws Exception {
            boolean inScaledNether = NETHER.equals(mc.world.getRegistryKey().getValue().getPath()) && scalesNetherToOverworld();
            boolean removed = inScaledNether && purge(overworldWorld(session(), null), removal, NETHER_SCALE);
            return purge(currentWorld(), removal, 1) || removed;
        }

        private static boolean purge(MinimapWorld world, Removal removal, int divisor) throws Exception {
            WaypointSet set = world == null ? null : world.getCurrentWaypointSet();
            if (set == null) return false;
            List<Waypoint> doomed = new ArrayList<>();
            for (Waypoint waypoint : set.getWaypoints()) {
                if (removal.matches(waypoint, divisor)) {
                    doomed.add(waypoint);
                }
            }
            doomed.forEach(set::remove);
            if (!doomed.isEmpty()) {
                session().getWorldManagerIO().saveWorld(world);
            }
            return !doomed.isEmpty();
        }

        private static void refresh() {
            try {
                SupportMods.xaeroMinimap.requestWaypointsRefresh();
            } catch (Throwable ignored) {
            }
        }

        private static WaypointPurpose toPurpose(Purpose purpose) {
            return purpose == Purpose.Destination ? WaypointPurpose.DESTINATION : WaypointPurpose.NORMAL;
        }

        private static WaypointColor toColor(WpColor color) {
            if (color == WpColor.Random) return WaypointColor.getRandom();
            return WaypointColor.valueOf(color.name().toUpperCase());
        }
    }
}
