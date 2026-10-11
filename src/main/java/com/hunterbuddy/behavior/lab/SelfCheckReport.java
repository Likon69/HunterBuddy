package com.hunterbuddy.behavior.lab;

import java.util.List;

public record SelfCheckReport(int ticks, List<String> checks) {
    public SelfCheckReport {
        checks = List.copyOf(checks);
    }
}
