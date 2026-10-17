package com.hunterbuddy.modules.messenger;

public record PrivateMessage(String peer, boolean incoming, String text, long epochMillis) {
}
