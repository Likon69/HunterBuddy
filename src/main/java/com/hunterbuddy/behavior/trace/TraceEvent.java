package com.hunterbuddy.behavior.trace;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.hooks.Hook;

public record TraceEvent(long tick, long nowMs, Hook hook, String owner,
                         String nodePath, Kind kind, FailureCode code,
                         String reason, String detail) {
}
