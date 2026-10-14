package com.hunterbuddy.behavior.farmer;

public final class TransferPurposes {
    public static final String DELIVER = "deliver";
    public static final String TAKE_PREFIX = "take-";
    private static final String STORE_PREFIX = "store-";
    public static final String RETURN_RESERVE = "return-" + LogisticsRoles.RESERVE;
    public static final String STORE_RESERVE = STORE_PREFIX + LogisticsRoles.RESERVE;
    public static final String STORE_MISCELLANEOUS = STORE_PREFIX + LogisticsRoles.MISCELLANEOUS;
    public static final String RETURN_SURPLUS = "return-surplus";
    public static final String RETURN_MISCELLANEOUS = "return-miscellaneous";
    public static final String RETURN_EMPTIES = "return-empties";
    public static final String STORE_EMPTIES = STORE_PREFIX + LogisticsRoles.EMPTIES;
    public static final String TAKE_MISCELLANEOUS_CONTENTS = TAKE_PREFIX + LogisticsRoles.MISCELLANEOUS_CONTENTS;
    public static final String CARRIED_FUEL = "carried-fuel";
    public static final String RESTOCK_ROCKETS = "restock-rockets";
    public static final String SHELTER_STOCK = "shelter-stock";
    public static final String RETURN_LOOSE_MISCELLANEOUS = "return-loose-miscellaneous";
    public static final String TAKE = "take";
    public static final String STORE = "store";
    public static final String WORK = "work";

    private TransferPurposes() {}
}
