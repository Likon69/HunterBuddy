package com.hunterbuddy.modules.mapwaypoint;

import com.hunterbuddy.modules.WaypointFollower;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;

public final class MapWaypointPlacer {
    private static final int NETHER_COORDINATE_SCALE = 8;
    private static final int END_HEIGHT = 70;
    private static final int OVERWORLD_HEIGHT = 320;
    private static final String WORLD_MISSING =
        "Overworld waypoint world not found. Nothing was placed: select Overworld in the map waypoint dimension and try again.";

    private final WaypointFollower follower;

    public MapWaypointPlacer(WaypointFollower follower) {
        this.follower = follower;
    }

    public void place(int clickedX, int clickedZ, WaypointKind kind) {
        try {
            create(clickedX, clickedZ, kind);
        } catch (Exception failure) {
            report("Error creating waypoint: " + failure.getMessage());
        }
    }

    private void create(int clickedX, int clickedZ, WaypointKind kind) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            report("MinimapSession is null");
            return;
        }
        MapDimension viewed = MapDimension.viewed();
        MinimapWorld world = MapWorldLookup.targetFor(session, viewed);
        if (world == null) {
            follower.error(WORLD_MISSING);
            return;
        }
        PlacementRequest request = new PlacementRequest(spotFor(clickedX, clickedZ, viewed), kind.nameFor(follower), kind);
        if (store(session, world, request)) afterStored(request);
    }

    private MapSpot spotFor(int clickedX, int clickedZ, MapDimension viewed) {
        if (viewed == MapDimension.END) return new MapSpot(clickedX, END_HEIGHT, clickedZ);
        int scale = viewed == MapDimension.NETHER ? NETHER_COORDINATE_SCALE : 1;
        return new MapSpot(clickedX * scale, OVERWORLD_HEIGHT, clickedZ * scale);
    }

    private boolean store(MinimapSession session, MinimapWorld world, PlacementRequest request) {
        WaypointSet set = world.getCurrentWaypointSet();
        if (set == null) {
            report("Current waypoint set is null");
            return false;
        }
        MapSpot spot = request.spot();
        WaypointKind kind = request.kind();
        set.add(new Waypoint(spot.x(), spot.y(), spot.z(), request.name(), kind.initials(), kind.color(), WaypointPurpose.NORMAL, false));
        save(session, world);
        SupportMods.xaeroMinimap.requestWaypointsRefresh();
        return true;
    }

    private void save(MinimapSession session, MinimapWorld world) {
        try {
            session.getWorldManagerIO().saveWorld(world);
        } catch (Exception failure) {
            report("Failed to save waypoint: " + failure.getMessage());
        }
    }

    private void afterStored(PlacementRequest request) {
        MapSpot spot = request.spot();
        WaypointKind kind = request.kind();
        if (!kind.isRouteMark() && follower.isActive()) follower.addWaypointToTrack(spot.x(), spot.y(), spot.z());
        if (follower.shouldShowChatMessages()) {
            follower.info("Created waypoint '" + request.name() + "' at " + spot.x() + ", " + spot.z());
        }
    }

    private void report(String message) {
        if (follower.shouldShowChatMessages()) follower.error(message);
    }
}
