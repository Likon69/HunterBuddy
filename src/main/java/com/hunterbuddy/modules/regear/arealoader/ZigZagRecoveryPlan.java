package com.hunterbuddy.modules.regear.arealoader;

final class ZigZagRecoveryPlan {
    record Sizes(int legLength, int rowGap) {
    }

    private final RecoveryPoint origin;
    private final RecoveryPoint resume;
    private final boolean mainAlongZ;
    private final Sizes sizes;

    ZigZagRecoveryPlan(RecoveryPoint origin, RecoveryPoint resume, Heading mainHeading, Sizes sizes) {
        this.origin = origin;
        this.resume = resume;
        this.mainAlongZ = !mainHeading.alongX();
        this.sizes = sizes;
    }

    int mainProgress() {
        return mainAlongZ ? Math.abs(resume.z() - origin.z()) : Math.abs(resume.x() - origin.x());
    }

    int sideProgress() {
        return mainAlongZ ? Math.abs(resume.x() - origin.x()) : Math.abs(resume.z() - origin.z());
    }

    int legsCompleted() {
        return sideProgress() / sizes.rowGap();
    }

    int positionInLeg() {
        return mainProgress() % sizes.legLength();
    }

    boolean isLikelyOnMainLeg() {
        return positionInLeg() > sizes.rowGap() / 2;
    }

    RecoveryPoint snapped() {
        if (mainAlongZ) return new RecoveryPoint(origin.x() + nearestRowOffset(resume.x() - origin.x()), resume.z());
        return new RecoveryPoint(resume.x(), origin.z() + nearestRowOffset(resume.z() - origin.z()));
    }

    private int nearestRowOffset(int sideDelta) {
        return Math.round((float) sideDelta / sizes.rowGap()) * sizes.rowGap();
    }
}
