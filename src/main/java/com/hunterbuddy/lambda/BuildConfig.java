/*
 * Port of com.lambda.config.blocks.BuildConfig (lambda 1.21.11).
 */
package com.hunterbuddy.lambda;

public class BuildConfig {
    public enum SwingType {
        Vanilla("Vanilla", "Play the hand swing locally and also notify the server (default, looks and works as expected)."),
        Server("Server", "Only notify the server to swing; local animation may not play unless the server echoes it."),
        Client("Client", "Only play the local swing animation; does not notify the server (purely visual).");

        private final String displayName;
        private final String description;
        SwingType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum PointSelection {
        ByRotation("By Rotation", "Choose the point that needs the least rotation from your current view (minimal camera turn)."),
        Optimum("Optimum", "Choose the point closest to the average of all candidates (balanced and stable aim).");

        private final String displayName;
        private final String description;
        PointSelection(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    private boolean breakBlocks = true;
    private boolean placeBlocks = true;
    private boolean interactBlocks = true;
    private boolean pathing = false;
    private boolean collectDrops = false;
    private boolean spleefEntities = false;
    private boolean cautionDoubleBlocks = false;
    private int maxPendingActions = 50;
    private int actionTimeout = 0;
    private int maxBuildDependencies = 5;
    private int limitTimeframe = 0;
    private int actionLimit = 0;
    private int interactionLimit = 0;
    private int inventoryLimit = 0;
    private double blockReach = 4.5;
    private double entityReach = 3.0;
    private double scanReach = 4.5;
    private boolean checkSideVisibility = false;
    private boolean strictRayCast = false;
    private int resolution = 1;
    private PointSelection pointSelection = PointSelection.ByRotation;

    public boolean isBreakBlocks() { return breakBlocks; }
    public void setBreakBlocks(boolean v) { this.breakBlocks = v; }
    public boolean isPlaceBlocks() { return placeBlocks; }
    public void setPlaceBlocks(boolean v) { this.placeBlocks = v; }
    public boolean isInteractBlocks() { return interactBlocks; }
    public void setInteractBlocks(boolean v) { this.interactBlocks = v; }
    public boolean isPathing() { return pathing; }
    public void setPathing(boolean v) { this.pathing = v; }
    public boolean isCollectDrops() { return collectDrops; }
    public void setCollectDrops(boolean v) { this.collectDrops = v; }
    public boolean isSpleefEntities() { return spleefEntities; }
    public void setSpleefEntities(boolean v) { this.spleefEntities = v; }
    public boolean isCautionDoubleBlocks() { return cautionDoubleBlocks; }
    public void setCautionDoubleBlocks(boolean v) { this.cautionDoubleBlocks = v; }
    public int getMaxPendingActions() { return maxPendingActions; }
    public void setMaxPendingActions(int v) { this.maxPendingActions = v; }
    public int getActionTimeout() { return actionTimeout; }
    public void setActionTimeout(int v) { this.actionTimeout = v; }
    public int getMaxBuildDependencies() { return maxBuildDependencies; }
    public void setMaxBuildDependencies(int v) { this.maxBuildDependencies = v; }
    public int getLimitTimeframe() { return limitTimeframe; }
    public void setLimitTimeframe(int v) { this.limitTimeframe = v; }
    public int getActionLimit() { return actionLimit; }
    public void setActionLimit(int v) { this.actionLimit = v; }
    public int getInteractionLimit() { return interactionLimit; }
    public void setInteractionLimit(int v) { this.interactionLimit = v; }
    public int getInventoryLimit() { return inventoryLimit; }
    public void setInventoryLimit(int v) { this.inventoryLimit = v; }
    public double getBlockReach() { return blockReach; }
    public void setBlockReach(double v) { this.blockReach = v; }
    public double getEntityReach() { return entityReach; }
    public void setEntityReach(double v) { this.entityReach = v; }
    public double getScanReach() { return scanReach; }
    public void setScanReach(double v) { this.scanReach = v; }
    public boolean isCheckSideVisibility() { return checkSideVisibility; }
    public void setCheckSideVisibility(boolean v) { this.checkSideVisibility = v; }
    public boolean isStrictRayCast() { return strictRayCast; }
    public void setStrictRayCast(boolean v) { this.strictRayCast = v; }
    public int getResolution() { return resolution; }
    public void setResolution(int v) { this.resolution = v; }
    public PointSelection getPointSelection() { return pointSelection; }
    public void setPointSelection(PointSelection v) { this.pointSelection = v; }
}
