/*
 * Port of com.lambda.config.blocks.BreakConfig (lambda 1.21.11).
 * Configuration for block breaking (extends ActionConfig for sorter).
 *
 * Every field lambda has is here, no inventions. All enums ported
 * with their values. Default values match lambda where set.
 */
package com.hunterbuddy.lambda;

import net.minecraft.block.Block;
import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BreakConfig implements ActionConfig {
    public enum BreakMode {
        Vanilla("Vanilla", "Uses vanilla breaking"),
        Grim("Grim", "Uses a grim bypass"),
        OldGrim("Old Grim", "Uses an old grim bypass");

        private final String displayName;
        private final String description;
        BreakMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum SwapMode {
        None("None", "Never auto-swap tools. Keeps whatever you're holding"),
        Start("Start", "Auto-swap to the best tool right when the break starts. No further swaps during the same break"),
        End("End", "Stay on your current tool at first, then auto-swap to the best tool right before the block finishes breaking to speed up the final stretch"),
        StartAndEnd("Start and End", "Auto-swap to the best tool at the start, and again right before the block finishes breaking if it would be faster"),
        Constant("Constant", "Always keep the best tool selected for the entire break. Swaps as needed to maintain optimal speed");

        private final String displayName;
        private final String description;
        SwapMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean isEnabled() { return this != None; }
    }

    public enum SwingMode {
        Constant("Constant", "Swings the hand every tick"),
        StartAndEnd("Start and End", "Swings the hand at the start and end of breaking"),
        Start("Start", "Swings the hand at the start of breaking"),
        End("End", "Swings the hand at the end of breaking"),
        None("None", "Does not swing the hand at all");

        private final String displayName;
        private final String description;
        SwingMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean isEnabled() { return this != None; }
    }

    public enum BreakConfirmationMode {
        None("No confirmation", "Breaks immediately without waiting for the server. Lowest latency, but can briefly show break effects even if the server later disagrees."),
        BreakThenAwait("Break now, confirm later", "Shows the break effects right away (particles/sounds) and then waits for the server to confirm. Feels instant while keeping results consistent."),
        AwaitThenBreak("Confirm first, then break", "Waits for the server response before showing break effects. Most accurate and safest, but adds a short delay.");

        private final String displayName;
        private final String description;
        BreakConfirmationMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum AnimationMode {
        None("None", "Does not render any breaking animation"),
        Out("Out", "Renders a growing animation"),
        In("In", "Renders a shrinking animation"),
        OutIn("Out In", "Renders a growing and shrinking animation"),
        InOut("In Out", "Renders a shrinking and growing animation");

        private final String displayName;
        private final String description;
        AnimationMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum WhitelistMode {
        Whitelist("Whitelist", "Only break blocks in the whitelist"),
        Blacklist("Blacklist", "Only break blocks not in the blacklist"),
        None("None", "Breaks all blocks");

        private final String displayName;
        private final String description;
        WhitelistMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // Configuration fields (mutable so settings can be edited)
    private BreakMode breakMode = BreakMode.Grim;
    private boolean rebreak = true;
    private boolean doubleBreak = true;
    private boolean unsafeCancels = false;
    private float breakThreshold = 1.0f;
    private int fudgeFactor = 0;
    private int serverSwapTicks = 0;
    private int breakDelay = 0;
    private SwapMode swapMode = SwapMode.None;
    private SwingMode swing = SwingMode.Start;
    private BuildConfig.SwingType swingType = BuildConfig.SwingType.Vanilla;
    private boolean rotate = false;
    private BreakConfirmationMode breakConfirmation = BreakConfirmationMode.None;
    private int breaksPerTick = 1;
    private boolean avoidFluids = false;
    private boolean fillFluids = false;
    private boolean avoidSupporting = false;
    private WhitelistMode whitelistMode = WhitelistMode.None;
    private Set<Block> whitelist = new HashSet<>();
    private Set<Block> blacklist = new HashSet<>();
    private boolean efficientOnly = false;
    private boolean suitableToolsOnly = false;
    private boolean forceSilkTouch = false;
    private boolean forceFortunePickaxe = false;
    private int minFortuneLevel = 0;
    private boolean sounds = true;
    private boolean particles = true;
    private boolean breakingTexture = true;
    private boolean renders = true;
    private boolean fill = true;
    private AnimationMode animation = AnimationMode.None;
    private boolean dynamicFillColor = false;
    private Color staticFillColor = new Color(255, 0, 0, 60);
    private Color startFillColor = new Color(255, 255, 0, 60);
    private Color endFillColor = new Color(255, 0, 0, 60);
    private boolean outline = true;
    private LineConfig outlineConfig = new LineConfig();
    private boolean dynamicOutlineColor = false;
    private Color staticOutlineColor = new Color(255, 0, 0, 255);
    private Color startOutlineColor = new Color(255, 255, 0, 255);
    private Color endOutlineColor = new Color(255, 0, 0, 255);

    // Getters/setters for all fields
    public BreakMode getBreakMode() { return breakMode; }
    public void setBreakMode(BreakMode v) { this.breakMode = v; }
    public boolean isRebreak() { return rebreak; }
    public void setRebreak(boolean v) { this.rebreak = v; }
    public boolean isDoubleBreak() { return doubleBreak; }
    public void setDoubleBreak(boolean v) { this.doubleBreak = v; }
    public boolean isUnsafeCancels() { return unsafeCancels; }
    public void setUnsafeCancels(boolean v) { this.unsafeCancels = v; }
    public float getBreakThreshold() { return breakThreshold; }
    public void setBreakThreshold(float v) { this.breakThreshold = v; }
    public int getFudgeFactor() { return fudgeFactor; }
    public void setFudgeFactor(int v) { this.fudgeFactor = v; }
    public int getServerSwapTicks() { return serverSwapTicks; }
    public void setServerSwapTicks(int v) { this.serverSwapTicks = v; }
    public int getBreakDelay() { return breakDelay; }
    public void setBreakDelay(int v) { this.breakDelay = v; }
    public SwapMode getSwapMode() { return swapMode; }
    public void setSwapMode(SwapMode v) { this.swapMode = v; }
    public SwingMode getSwing() { return swing; }
    public void setSwing(SwingMode v) { this.swing = v; }
    public BuildConfig.SwingType getSwingType() { return swingType; }
    public void setSwingType(BuildConfig.SwingType v) { this.swingType = v; }
    public boolean isRotate() { return rotate; }
    public void setRotate(boolean v) { this.rotate = v; }
    public BreakConfirmationMode getBreakConfirmation() { return breakConfirmation; }
    public void setBreakConfirmation(BreakConfirmationMode v) { this.breakConfirmation = v; }
    public int getBreaksPerTick() { return breaksPerTick; }
    public void setBreaksPerTick(int v) { this.breaksPerTick = v; }
    public boolean isAvoidFluids() { return avoidFluids; }
    public void setAvoidFluids(boolean v) { this.avoidFluids = v; }
    public boolean isFillFluids() { return fillFluids; }
    public void setFillFluids(boolean v) { this.fillFluids = v; }
    public boolean isAvoidSupporting() { return avoidSupporting; }
    public void setAvoidSupporting(boolean v) { this.avoidSupporting = v; }
    public WhitelistMode getWhitelistMode() { return whitelistMode; }
    public void setWhitelistMode(WhitelistMode v) { this.whitelistMode = v; }
    public Set<Block> getWhitelist() { return whitelist; }
    public void setWhitelist(Set<Block> v) { this.whitelist = v; }
    public Set<Block> getBlacklist() { return blacklist; }
    public void setBlacklist(Set<Block> v) { this.blacklist = v; }
    public boolean isEfficientOnly() { return efficientOnly; }
    public void setEfficientOnly(boolean v) { this.efficientOnly = v; }
    public boolean isSuitableToolsOnly() { return suitableToolsOnly; }
    public void setSuitableToolsOnly(boolean v) { this.suitableToolsOnly = v; }
    public boolean isForceSilkTouch() { return forceSilkTouch; }
    public void setForceSilkTouch(boolean v) { this.forceSilkTouch = v; }
    public boolean isForceFortunePickaxe() { return forceFortunePickaxe; }
    public void setForceFortunePickaxe(boolean v) { this.forceFortunePickaxe = v; }
    public int getMinFortuneLevel() { return minFortuneLevel; }
    public void setMinFortuneLevel(int v) { this.minFortuneLevel = v; }
    public boolean isSounds() { return sounds; }
    public void setSounds(boolean v) { this.sounds = v; }
    public boolean isParticles() { return particles; }
    public void setParticles(boolean v) { this.particles = v; }
    public boolean isBreakingTexture() { return breakingTexture; }
    public void setBreakingTexture(boolean v) { this.breakingTexture = v; }
    public boolean isRenders() { return renders; }
    public void setRenders(boolean v) { this.renders = v; }
    public boolean isFill() { return fill; }
    public void setFill(boolean v) { this.fill = v; }
    public AnimationMode getAnimation() { return animation; }
    public void setAnimation(AnimationMode v) { this.animation = v; }
    public boolean isDynamicFillColor() { return dynamicFillColor; }
    public void setDynamicFillColor(boolean v) { this.dynamicFillColor = v; }
    public Color getStaticFillColor() { return staticFillColor; }
    public void setStaticFillColor(Color v) { this.staticFillColor = v; }
    public Color getStartFillColor() { return startFillColor; }
    public void setStartFillColor(Color v) { this.startFillColor = v; }
    public Color getEndFillColor() { return endFillColor; }
    public void setEndFillColor(Color v) { this.endFillColor = v; }
    public boolean isOutline() { return outline; }
    public void setOutline(boolean v) { this.outline = v; }
    public LineConfig getOutlineConfig() { return outlineConfig; }
    public void setOutlineConfig(LineConfig v) { this.outlineConfig = v; }
    public boolean isDynamicOutlineColor() { return dynamicOutlineColor; }
    public void setDynamicOutlineColor(boolean v) { this.dynamicOutlineColor = v; }
    public Color getStaticOutlineColor() { return staticOutlineColor; }
    public void setStaticOutlineColor(Color v) { this.staticOutlineColor = v; }
    public Color getStartOutlineColor() { return startOutlineColor; }
    public void setStartOutlineColor(Color v) { this.startOutlineColor = v; }
    public Color getEndOutlineColor() { return endOutlineColor; }
    public void setEndOutlineColor(Color v) { this.endOutlineColor = v; }

    @Override
    public SortMode getSorter() { return SortMode.Closest; }
    @Override
    public void setSorter(SortMode v) { /* ActionConfig */ }
    @Override
    public java.util.Collection<net.minecraft.world.tick.TickScheduler> getTickStageMask() { return java.util.List.of(); }
    @Override
    public void setTickStageMask(java.util.Collection<net.minecraft.world.tick.TickScheduler> v) { }
}
