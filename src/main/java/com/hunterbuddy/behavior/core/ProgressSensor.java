package com.hunterbuddy.behavior.core;

@FunctionalInterface
public interface ProgressSensor {
    double value(Sensors sensors);
}
