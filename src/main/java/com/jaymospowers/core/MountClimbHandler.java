package com.jaymospowers.core;

import com.jaymospowers.net.MountClimbPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;

/**
 * Server-side handler for mount climbing synchronisation to other players.
 */
public final class MountClimbHandler {

    private MountClimbHandler() {
    }

    /**
     * Called when a player starts climbing onto a mount.
     * Broadcasts to nearby players so they see the animation.
     */
    public static void onClimbOnStart(ServerPlayer player, Entity mount, byte type) {
        // Broadcast to tracking players (excluding the rider)
        for (ServerPlayer tracker : PlayerLookup.tracking(player)) {
            if (tracker != player) {
                ServerPlayNetworking.send(tracker, new MountClimbPackets.ClimbSync(
                        player.getId(), mount.getId(), type, true));
            }
        }
    }

    /**
     * Called when a player starts climbing off a mount.
     * Broadcasts to nearby players so they see the animation.
     */
    public static void onClimbOffStart(ServerPlayer player, Entity mount, byte type) {
        for (ServerPlayer tracker : PlayerLookup.tracking(player)) {
            if (tracker != player) {
                ServerPlayNetworking.send(tracker, new MountClimbPackets.ClimbSync(
                        player.getId(), mount.getId(), type, false));
            }
        }
    }

    /**
     * Called when a climb is cancelled (e.g., player took damage).
     * Broadcasts to nearby players to cancel their view of the animation.
     */
    public static void onClimbCancel(ServerPlayer player) {
        for (ServerPlayer tracker : PlayerLookup.tracking(player)) {
            if (tracker != player) {
                ServerPlayNetworking.send(tracker, new MountClimbPackets.ClimbCancel(player.getId()));
            }
        }
    }

    /** Called from client packet handler when climb-on finishes. */
    public static void finishClimbOn(int mountId) {
        // This is called from the client packet receiver (MountClimbPackets.register)
        // The actual ride start is handled there
    }
}