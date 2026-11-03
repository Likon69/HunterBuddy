package com.hunterbuddy.modules.waypointfollow;

import com.hunterbuddy.modules.WaypointFollower;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaeroplus.feature.waypoint.WaypointAPI;
import xaeroplus.settings.Settings;

public final class FollowerWaypointWorld {
    private static final double NO_DIVISION = 1.0;

    private FollowerWaypointWorld() {
    }

    public static MinimapWorld resolve() {
        return resolve(null);
    }

    public static MinimapWorld resolveInEnd() {
        return resolve(World.END);
    }

    private static MinimapWorld resolve(RegistryKey<World> preferred) {
        try {
            MinimapSession session = currentSession();
            if (session == null) return worldOfPlayerDimension();
            if (preferOverworld()) return WaypointFollower.resolveOverworldWaypointWorld();
            return resolveInSession(session, preferred);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean preferOverworld() {
        try {
            return Settings.REGISTRY.owAutoWaypointDimension.get();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean enablePreferOverworld() {
        try {
            if (Settings.REGISTRY.owAutoWaypointDimension.get()) return false;
            Settings.REGISTRY.owAutoWaypointDimension.setValue(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static double dimensionDivision() {
        try {
            MinimapSession session = currentSession();
            MinimapWorld world = session == null ? null : session.getWorldManager().getCurrentWorld();
            return world == null ? NO_DIVISION : session.getDimensionHelper().getDimensionDivision(world);
        } catch (Exception e) {
            return NO_DIVISION;
        }
    }

    public static WaypointSet currentSet(MinimapWorld world) {
        return world == null ? null : world.getCurrentWaypointSet();
    }

    public static void saveQuietly(MinimapWorld world) {
        try {
            save(world);
        } catch (Exception ignored) {
        }
    }

    public static void save(MinimapWorld world) throws Exception {
        MinimapSession session = currentSession();
        if (session != null) session.getWorldManagerIO().saveWorld(world);
    }

    public static void requestRefresh() {
        try {
            SupportMods.xaeroMinimap.requestWaypointsRefresh();
        } catch (Exception ignored) {
        }
    }

    public static void removeAndSave(MinimapWorld world, WaypointSet set, Waypoint waypoint) {
        try {
            set.remove(waypoint);
        } catch (Exception ignored) {
        }
        saveQuietly(world);
        requestRefresh();
    }

    private static MinimapSession currentSession() {
        return (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
    }

    private static MinimapWorld resolveInSession(MinimapSession session, RegistryKey<World> preferred) {
        MinimapWorld first = preferred == null ? null : WaypointAPI.getMinimapWorld(preferred);
        if (first != null) return first;
        MinimapWorld current = session.getWorldManager().getCurrentWorld();
        return current != null ? current : worldOfPlayerDimension();
    }

    private static MinimapWorld worldOfPlayerDimension() {
        MinecraftClient mc = MinecraftClient.getInstance();
        RegistryKey<World> dimension = mc.world == null ? World.OVERWORLD : mc.world.getRegistryKey();
        return WaypointAPI.getMinimapWorld(dimension);
    }
}
