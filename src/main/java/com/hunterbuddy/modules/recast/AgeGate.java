package com.hunterbuddy.modules.recast;

public final class AgeGate {
    private int lastAge = -1;

    public boolean alreadySeen(int age) {
        return age == lastAge;
    }

    public void record(int age) {
        lastAge = age;
    }
}
