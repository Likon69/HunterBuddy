package com.hunterbuddy.modules.nuker;

public final class NukerRules {
    public enum Shape {
        All, Flatten, Tunnel
    }

    public enum Order {
        Closest, Farthest, Fastest, Slowest
    }

    public enum Filter {
        All, Whitelist, Blacklist
    }

    private NukerRules() {
    }

    public static boolean shapeAllows(Shape shape, int blockY, int feetY) {
        return switch (shape) {
            case All -> true;
            case Flatten -> blockY >= feetY;
            case Tunnel -> blockY == feetY || blockY == feetY + 1;
        };
    }

    public static boolean filterAllows(Filter filter, boolean listed) {
        return switch (filter) {
            case All -> true;
            case Whitelist -> listed;
            case Blacklist -> !listed;
        };
    }

    public static double distanceSquared(double eyeX, double eyeY, double eyeZ, int x, int y, int z) {
        double dx = Math.max(x - eyeX, Math.max(0, eyeX - x - 1));
        double dy = Math.max(y - eyeY, Math.max(0, eyeY - y - 1));
        double dz = Math.max(z - eyeZ, Math.max(0, eyeZ - z - 1));
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean supportsPlayer(int x, int y, int z, int supportY,
                                         double minX, double maxX, double minZ, double maxZ) {
        return y == supportY && x < maxX && x + 1 > minX && z < maxZ && z + 1 > minZ;
    }

    public static boolean toolSafe(int maximum, int damage, int percent, int reservedDamage) {
        if (maximum <= 0) return true;
        int reserve = Math.max(1, (int) Math.ceil(maximum * (percent / 100.0)));
        return maximum - damage >= reserve + Math.max(1, reservedDamage);
    }

    public static boolean better(Order order, double distance, float speed, double bestDistance, float bestSpeed) {
        int priority = switch (order) {
            case Closest -> Double.compare(bestDistance, distance);
            case Farthest -> Double.compare(distance, bestDistance);
            case Fastest -> Float.compare(speed, bestSpeed);
            case Slowest -> Float.compare(bestSpeed, speed);
        };
        return priority > 0 || priority == 0 && distance < bestDistance;
    }
}
