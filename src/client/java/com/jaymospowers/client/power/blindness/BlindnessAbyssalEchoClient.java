package com.jaymospowers.client.power.blindness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client-side state tracking for BlindnessAbyssalEcho. */
public final class BlindnessAbyssalEchoClient {
    private static boolean wasActive = false;

    private BlindnessAbyssalEchoClient() {
    }

    public static boolean active(LocalPlayer player) {
        if (player == null) return false;
        var power = com.jaymospowers.core.Powers.of(player);
        if (power != com.jaymospowers.power.blindness.BlindnessPower.INSTANCE) return false;
        return com.jaymospowers.core.Fate.isAscended(player) && com.jaymospowers.core.Fate.isCorrupted(player);
    }

    /** Called when the server sends an Abyssal Echo activation packet. */
    public static void onActivate() {
        wasActive = true;
    }

    public static boolean justActivated(LocalPlayer player) {
        boolean now = active(player);
        boolean result = now && !wasActive;
        wasActive = now;
        return result;
    }
}