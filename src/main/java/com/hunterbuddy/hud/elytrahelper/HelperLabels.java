package com.hunterbuddy.hud.elytrahelper;

public record HelperLabels(
    String title,
    String header,
    String flying,
    String session,
    String distance,
    String rockets,
    String average,
    String peak,
    String fromStart,
    String speed
) {
    public static final HelperLabels FULL = new HelperLabels(
        "Elytra Helper", "--- Session ---", "Flying: ", "Session: ", "Distance: ",
        "Rockets: ", "Average: ", "Peak: ", "From Start: ", "Speed: ");

    public static final HelperLabels COMPACT = new HelperLabels(
        "Elytra", "-- Session --", "Fly: ", "Sess: ", "Dist: ",
        "Rkt: ", "Avg: ", "Peak: ", "From start: ", "Spd: ");
}
