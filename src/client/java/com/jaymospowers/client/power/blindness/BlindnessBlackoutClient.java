package com.jaymospowers.client.power.blindness;

import com.jaymospowers.power.blindness.BlindnessBlackout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client-side state tracking for BlindnessBlackout. */
public final class BlindnessBlackoutClient {
    private static boolean wasActive = false;

    private BlindnessBlackoutClient() {
    }

    public static boolean active(LocalPlayer player) {
        // The server tracks active state in BlindnessBlackout.ACTIVE set
        // We sync via the BlindnessState attachment or a separate packet
        // For now, check if the player has the Blindness power and is corrupted
        if (player == null) return false;
        var power = com.jaymospowers.core.Powers.of(player);
        if (power != com.jaymospowers.power.blindness.BlindnessPower.INSTANCE) return false;
        // Check if Blackout is active via a client-synced flag
        // This would need a packet from server, but for now we'll use a simple heuristic
        return com.jaymospowers.core.Fate.isCorrupted(player);
    }

    /** Called when the server sends a Blackout activation packet. */
    public static void onActivate() {
        wasActive = true;
    }

    /** Called when the server sends a Blackout deactivation packet. */
    public static void onDeactivate() {
        wasActive = false;
    }

    public static boolean justActivated(LocalPlayer player) {
        boolean now = active(player);
        boolean result = now && !wasActive;
        wasActive = now;
        return result;
    }
}