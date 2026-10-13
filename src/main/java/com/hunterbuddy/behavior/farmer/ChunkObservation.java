package com.hunterbuddy.behavior.farmer;

public record ChunkObservation(CoverageState state, long observedAt, String reason) {}
