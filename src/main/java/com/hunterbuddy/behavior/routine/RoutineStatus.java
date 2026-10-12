package com.hunterbuddy.behavior.routine;

import com.hunterbuddy.behavior.hooks.Hook;

public record RoutineStatus(String name, Hook slot, String state, String detail) {
}
