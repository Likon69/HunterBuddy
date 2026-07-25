/*
 * Port of com.lambda.context.Automated (lambda 1.21.11).
 * Holds references to all the config classes that drive automation.
 */
package com.hunterbuddy.lambda;

public interface Automated {
    BuildConfig getBuildConfig();
    BreakConfig getBreakConfig();
    Object getInteractConfig();
    Object getRotationConfig();
    Object getInventoryConfig();
    Object getHotbarConfig();
    Object getEatConfig();
}
