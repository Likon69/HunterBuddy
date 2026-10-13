package com.hunterbuddy.behavior.farmer;

public final class BaseVisitFacts {
    public String world;
    public int step;
    public String profile;
    public int target;
    public int[] departure;
    public int[] delivery;
    public int[] returns;
    public int[] empties;
    public int[] supply;
    public String cargo = "";
    public String cargoRole = "";
    public int cargoBaseline;
    public String miscEmptied = "";
    public String reserveReturned = "";
    public String reserveSupplied = "";
    public int rocketBudget;
    public int wearBudget;
    public int emptyTarget;
    public int[] recoveredAt;
    public BaseTransferFacts transfer = new BaseTransferFacts();
}
