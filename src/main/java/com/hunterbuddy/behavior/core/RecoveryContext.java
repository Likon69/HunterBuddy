package com.hunterbuddy.behavior.core;

public final class RecoveryContext {
    private String taskOwner = "core";

    public String taskOwner() {
        return taskOwner;
    }

    public void taskOwner(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("empty recovery owner");
        taskOwner = owner;
    }
}
