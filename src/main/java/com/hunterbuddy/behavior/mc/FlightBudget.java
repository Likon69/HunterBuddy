package com.hunterbuddy.behavior.mc;

public final class FlightBudget {
    private final FlightPerformance performance;
    private final int minimumRockets;

    public FlightBudget(FlightPerformance performance, int minimumRockets) {
        this.performance = performance;
        this.minimumRockets = minimumRockets;
    }

    public int rockets(double metres) {
        return 2 + (int) Math.ceil(performance.rocketsPerKm() * metres / 1000);
    }

    public int reserve(double metres) {
        return Math.max(minimumRockets, 8 + (int) Math.ceil(rockets(metres) * 1.5));
    }

    public int tripRockets(double outward, double back, boolean returning) {
        return returning ? outward <= 96 ? 0 : rockets(outward - 96)
            : (int) Math.ceil(rockets(outward) * 1.5) + reserve(back);
    }

    public int tripWear(double outward, double back, boolean returning) {
        return 16 + (int) Math.ceil(performance.durabilityPerKm() * (outward + (returning ? 0 : back)) / 1000 * 1.5);
    }

    public int returnWear(double metres) {
        return tripWear(metres, 0, true);
    }

    public int departureRockets(double distance) {
        return Math.max(tripRockets(distance, distance, false), reserve(32)) + 5;
    }

    public int departureWear(double distance) {
        return Math.max(tripWear(distance, distance, false), returnWear(32)) + 65;
    }

    public boolean returnDue(double distance, int rockets, int wear) {
        return rockets <= reserve(distance) + 4 || wear <= returnWear(distance) + 64;
    }
}
