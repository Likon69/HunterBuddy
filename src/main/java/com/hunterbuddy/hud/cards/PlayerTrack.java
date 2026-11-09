package com.hunterbuddy.hud.cards;

import java.util.ArrayDeque;
import java.util.Arrays;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;

public final class PlayerTrack {
    public static final EquipmentSlot[] GEAR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private static final long VECTOR_WINDOW_MS = 3_000L;
    private static final long PRESENT_MS = 1_000L;
    private static final double MOVING_SPEED = 1.0;
    private static final double CLOSING_SPEED = 3.0;

    private final ArrayDeque<double[]> samples = new ArrayDeque<>();
    private final ItemStack[] gear = new ItemStack[GEAR_SLOTS.length];
    private String name = "";
    private SkinTextures skin;
    private long lastSeenAt;
    private double distance;
    private double approachSpeed;

    public PlayerTrack() {
        Arrays.fill(gear, ItemStack.EMPTY);
    }

    public static PlayerTrack preview(String name, SkinTextures skin, long now, double distance,
                                      double approachSpeed, ItemStack... gear) {
        PlayerTrack track = new PlayerTrack();
        track.name = name;
        track.skin = skin;
        track.lastSeenAt = now;
        track.distance = distance;
        track.approachSpeed = approachSpeed;
        System.arraycopy(gear, 0, track.gear, 0, gear.length);
        return track;
    }

    public void observe(PlayerEntity player, SkinTextures skin, double distance, long now) {
        name = player.getGameProfile().name();
        this.skin = skin;
        lastSeenAt = now;
        this.distance = distance;
        for (int i = 0; i < GEAR_SLOTS.length; i++) gear[i] = player.getEquippedStack(GEAR_SLOTS[i]).copy();

        samples.addLast(new double[]{now, distance});
        while (!samples.isEmpty() && now - (long) samples.peekFirst()[0] > VECTOR_WINDOW_MS) samples.removeFirst();
        if (samples.size() >= 2) {
            double[] first = samples.peekFirst();
            double[] last = samples.peekLast();
            double seconds = (last[0] - first[0]) / 1000.0;
            approachSpeed = seconds > 0.2 ? (first[1] - last[1]) / seconds : 0.0;
        }
    }

    public boolean present(long now) {
        return now - lastSeenAt <= PRESENT_MS;
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    public PlayerMovement movement(long now) {
        if (!present(now)) return PlayerMovement.GONE;
        if (approachSpeed > MOVING_SPEED) return PlayerMovement.APPROACH;
        if (approachSpeed < -MOVING_SPEED) return PlayerMovement.RECEDE;
        return PlayerMovement.HOLD;
    }

    public boolean closingWithin(double range, long now) {
        return present(now) && approachSpeed > CLOSING_SPEED && distance < range;
    }

    public String name() {
        return name;
    }

    public SkinTextures skin() {
        return skin;
    }

    public double distance() {
        return distance;
    }

    public ItemStack gear(int index) {
        return gear[index];
    }
}
