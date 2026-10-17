package com.hunterbuddy.modules.messenger.ui;

import meteordevelopment.meteorclient.MeteorClient;

final class Clipboard {
    private Clipboard() {
    }

    static void copy(String text) {
        MeteorClient.mc.keyboard.setClipboard(text);
    }

    static String paste() {
        return MeteorClient.mc.keyboard.getClipboard();
    }
}
