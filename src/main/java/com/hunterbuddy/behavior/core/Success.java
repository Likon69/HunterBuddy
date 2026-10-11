package com.hunterbuddy.behavior.core;

public record Success() implements NodeResult {
    public static final Success INSTANCE = new Success();
}
