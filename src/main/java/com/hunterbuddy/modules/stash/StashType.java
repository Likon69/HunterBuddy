package com.hunterbuddy.modules.stash;

public enum StashType {
    CHEST(text("Chests", "Chest", 0x1F4E6), config("chests", true, "min-chests", 4)),
    TRAPPED_CHEST(text("Trapped Chests", "TrappedChest", 0x1FAA4), config("trapped-chests", true, "min-trapped-chests", 1)),
    BARREL(text("Barrels", "Barrel", 0x1F6E2), config("barrels", false, "min-barrels", 4)),
    SHULKER(text("Shulkers", "Shulker", 0x1F381), config("shulkers", true, "min-shulkers", 1)),
    ENDER_CHEST(text("Ender Chests", "EnderChest", 0x2601), config("ender-chests", true, "min-ender-chests", 1)),
    FURNACE(text("Furnaces", "Furnace", 0x1F525), config("furnaces", false, "min-furnaces", 2)),
    DISPENSER(text("Dispensers/Droppers", "Dispenser", 0x2B07), config("dispensers-droppers", false, "min-dispensers", 2)),
    HOPPER(text("Hoppers", "Hopper", 0x23EC), config("hoppers", true, "min-hoppers", 3)),
    BREWING_STAND(text("Brewing Stands", "BrewingStand", 0x1F9EA), config("brewing-stands", false, "min-brewing-stands", 1)),
    CRAFTER(text("Crafters", "Crafter", 0x1F527), config("crafters", false, "min-crafters", 1)),
    DECORATED_POT(text("Decorated Pots", "DecoratedPot", 0x1F3FA), config("decorated-pots", false, "min-decorated-pots", 3)),
    BANNER(text("Banners", "Banner", 0x1F6A9), config("banners", false, "min-banners", 2)),
    SIGN(text("Signs", "Sign", 0x1FAA7), config("signs", false, "min-signs", 3)),
    HANGING_SIGN(text("Hanging Signs", "HangingSign", 0x1FA9D), config("hanging-signs", false, "min-hanging-signs", 2)),
    MAP_ITEM_FRAME(text("Map Item Frames", "MapItemFrame", 0x1F5FA), config("map-item-frames", true, "min-map-item-frames", 2)),
    ITEM_FRAME(text("Item Frames", "ItemFrame", 0x1F5BC), config("item-frames", false, "min-item-frames", 4)),
    ENDER_PEARL(text("Ender Pearls", "EnderPearl", 0x1F441), config("ender-pearls", true, "min-ender-pearls", 1)),
    STACKED_MINECART(text("Stacked Minecarts", "StackedMinecart", TypeText.NO_SYMBOL), config("stacked-minecarts", true, "min-stacked-minecarts", 3)),
    NAMED_ENTITY(text("Named Entities", "NamedEntity", 0x1F3F7), config("named-entities", true, "min-named-entities", 1));

    private final TypeText text;
    private final TypeConfig config;

    StashType(TypeText text, TypeConfig config) {
        this.text = text;
        this.config = config;
    }

    public String label() {
        return text.label();
    }

    public String singular() {
        return text.singular();
    }

    public boolean hasSymbol() {
        return text.symbolCodePoint() != TypeText.NO_SYMBOL;
    }

    public String symbol() {
        return hasSymbol() ? Character.toString(text.symbolCodePoint()) : "";
    }

    public String detectName() {
        return config.detectName();
    }

    public boolean detectDefault() {
        return config.detectDefault();
    }

    public String minimumName() {
        return config.minimumName();
    }

    public int minimumDefault() {
        return config.minimumDefault();
    }

    public boolean isContainer() {
        return ordinal() <= CRAFTER.ordinal();
    }

    public boolean showsInDetail() {
        return ordinal() <= MAP_ITEM_FRAME.ordinal();
    }

    private static TypeText text(String label, String singular, int symbolCodePoint) {
        return new TypeText(label, singular, symbolCodePoint);
    }

    private static TypeConfig config(String detectName, boolean detectDefault, String minimumName, int minimumDefault) {
        return new TypeConfig(detectName, detectDefault, minimumName, minimumDefault);
    }

    private record TypeText(String label, String singular, int symbolCodePoint) {
        static final int NO_SYMBOL = 0;
    }

    private record TypeConfig(String detectName, boolean detectDefault, String minimumName, int minimumDefault) {
    }
}
