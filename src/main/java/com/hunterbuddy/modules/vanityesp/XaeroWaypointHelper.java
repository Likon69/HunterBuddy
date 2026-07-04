package com.hunterbuddy.modules.vanityesp;

import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Reflection-based bridge to XaeroPlus for waypoint add/remove.
 *
 * <p>XaeroPlus is loaded as a separate mod on 2b2t, but HunterBuddy does
 * not declare it as a hard dependency (it must keep loading when XaeroPlus
 * is absent). The helper detects at class-load time whether the
 * XaeroPlus API classes are present and exposes a {@link #isAvailable()}
 * flag the rest of the addon reads before every call.
 *
 * <p>The actual XaeroPlus API surface (constructor + add method) varies
 * between XaeroPlus minor versions, so the helper probes a small set of
 * candidate class/method names; if none match, calls silently no-op.
 *
 * <p>Reference: mlep's {@code mlep.util.XaeroWaypointManager}.
 */
public final class XaeroWaypointHelper {
    private static final Logger LOG = LoggerFactory.getLogger("HunterBuddy-Xaero");
    private static final boolean AVAILABLE;
    private static Constructor<?> waypointCtor;
    private static Method addWaypoint;
    private static Method removeWaypoint;

    static {
        boolean available = false;
        try {
            // XaeroPlus 2.x typically exposes Waypoint in
            // xaero.common.minimap.waypoints.Waypoint with a public
            // constructor accepting (int x, int y, int z, String name,
            // String symbol, int color, boolean temporary, int dimension).
            Class<?> wpClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            waypointCtor = wpClass.getDeclaredConstructor(
                int.class, int.class, int.class,
                String.class, String.class, int.class, boolean.class, int.class
            );
            // xaero.common.XaeroMinimap.onWaypointAdded / onWaypointRemoved
            // is the canonical hook XaeroPlus documents for addon mods.
            Class<?> xaeroMain = Class.forName("xaero.common.XaeroMinimap");
            addWaypoint = findMethodByPrefix(xaeroMain, "onMinimapWaypointAdded", wpClass);
            removeWaypoint = findMethodByPrefix(xaeroMain, "onMinimapWaypointRemoved", wpClass);
            if (addWaypoint != null && removeWaypoint != null) available = true;
        } catch (Throwable ignored) {
            // XaeroPlus not loaded or its API shape is unexpected.
        }
        AVAILABLE = available;
        if (available) LOG.info("XaeroPlus detected; waypoint integration enabled.");
        else LOG.info("XaeroPlus not detected; waypoint settings will be inert.");
    }

    private XaeroWaypointHelper() {}

    private static Method findMethodByPrefix(Class<?> owner, String prefix, Class<?> argType) {
        for (Method m : owner.getMethods()) {
            if (m.getName().startsWith(prefix) && m.getParameterCount() == 2
                && m.getParameterTypes()[1].isAssignableFrom(argType)) {
                return m;
            }
        }
        return null;
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Add a waypoint under {@code modId} at world coordinates {@code pos}.
     * No-op if XaeroPlus is not loaded.
     */
    public static void addWaypoint(String modId, BlockPos pos, String name,
                                   String symbol, String colorName, boolean temporary) {
        if (!AVAILABLE) return;
        try {
            int color = colorNameToInt(colorName);
            int dim = netDimensionId();
            Object wp = waypointCtor.newInstance(
                pos.getX(), pos.getY(), pos.getZ(),
                name, symbol, color, temporary, dim
            );
            addWaypoint.invoke(null, modId, wp);
        } catch (Throwable t) {
            // Silently swallow; logging every failure would spam logs.
        }
    }

    /**
     * Remove a waypoint previously added under {@code modId} at {@code pos}.
     * No-op if XaeroPlus is not loaded.
     */
    public static void removeWaypoint(String modId, BlockPos pos) {
        if (!AVAILABLE) return;
        try {
            int dim = netDimensionId();
            Object wp = waypointCtor.newInstance(
                pos.getX(), pos.getY(), pos.getZ(),
                "", "", 0, false, dim
            );
            removeWaypoint.invoke(null, modId, wp);
        } catch (Throwable t) {
            // ditto
        }
    }

    private static int colorNameToInt(String name) {
        // Best-effort: mlep's MapUtil.WpColor enum maps names to ints.
        // Without that utility in our port, fall back to a known-good color
        // (Xaero's "Gold" = 0xFFD700 → decimal 16761024; pure-orange = 0).
        if (name == null) return 0;
        return switch (name) {
            case "Gold" -> 0xFFD700;
            case "Red" -> 0xFF0000;
            case "Green" -> 0x00FF00;
            case "Blue" -> 0x0000FF;
            case "Purple" -> 0xA020F0;
            case "White" -> 0xFFFFFF;
            default -> 0xFFD700;
        };
    }

    private static int netDimensionId() {
        // Xaero's dimension encoding: 0 = overworld, 1 = nether, 2 = end.
        // mc.world is null if not in a world; caller should only invoke us
        // when mc.world != null.
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.MinecraftClient");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            Object world = mcClass.getMethod("getWorld").invoke(mc);
            if (world == null) return 0;
            Object regKey = world.getClass().getMethod("getRegistryKey").invoke(world);
            String id = regKey.getClass().getMethod("getValue").invoke(regKey).toString();
            if (id.contains("the_nether")) return 1;
            if (id.contains("the_end")) return 2;
            return 0;
        } catch (Throwable t) {
            return 0;
        }
    }
}
