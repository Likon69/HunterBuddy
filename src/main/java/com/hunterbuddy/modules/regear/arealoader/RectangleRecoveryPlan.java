package com.hunterbuddy.modules.regear.arealoader;

final class RectangleRecoveryPlan {
    private final RecoveryPoint origin;
    private final RecoveryPoint end;
    private final RecoveryPoint resume;
    private final int blockGap;

    RectangleRecoveryPlan(RecoveryPoint origin, RecoveryPoint end, RecoveryPoint resume, int blockGap) {
        this.origin = origin;
        this.end = end;
        this.resume = resume;
        this.blockGap = blockGap;
    }

    int minX() {
        return Math.min(origin.x(), end.x());
    }

    int maxX() {
        return Math.max(origin.x(), end.x());
    }

    int minZ() {
        return Math.min(origin.z(), end.z());
    }

    int maxZ() {
        return Math.max(origin.z(), end.z());
    }

    boolean isWithinExpandedBounds() {
        return resume.x() >= minX() - blockGap && resume.x() <= maxX() + blockGap
            && resume.z() >= minZ() - blockGap && resume.z() <= maxZ() + blockGap;
    }

    boolean goingPositiveZ() {
        return origin.z() < end.z();
    }

    int zProgress() {
        return goingPositiveZ() ? resume.z() - origin.z() : origin.z() - resume.z();
    }

    int floorRowIndex() {
        return zProgress() / blockGap;
    }

    int nearestRowIndex() {
        return Math.round((float) zProgress() / blockGap);
    }

    int rowZ(int rowIndex) {
        int offset = rowIndex * blockGap;
        return goingPositiveZ() ? origin.z() + offset : origin.z() - offset;
    }

    enum RowDirection {
        EAST,
        WEST
    }

    RowDirection rowDirection(int rowIndex) {
        return (origin.x() < end.x()) == (rowIndex % 2 == 0) ? RowDirection.EAST : RowDirection.WEST;
    }

    int rowEndX(RowDirection direction) {
        return direction == RowDirection.EAST ? maxX() : minX();
    }

    int resumeXWithinBounds() {
        return Math.max(minX(), Math.min(maxX(), resume.x()));
    }

    boolean isAtRowEnd(RowDirection direction) {
        return Math.abs(resume.x() - rowEndX(direction)) < blockGap / 2.0;
    }
}
