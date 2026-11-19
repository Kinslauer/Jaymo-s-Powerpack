package com.jaymospowers.client;

import com.jaymospowers.net.MountClimbPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side handler for mount climb packets from the server (sync for other players).
 */
public final class MountClimbClientHandler {

    private MountClimbClientHandler() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MountClimbPackets.ClimbSync.TYPE, (payload, context) -> {
            // Another player started a climb animation
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level != null) {
                    Entity playerEntity = mc.level.getEntity(payload.playerId());
                    Entity mountEntity = mc.level.getEntity(payload.mountId());
                    if (playerEntity instanceof Player player && mountEntity != null) {
                        MountClimbPose.ClimbType type = MountClimbPose.ClimbType.values()[payload.climbType()];
                        if (payload.climbingOn()) {
                            MountClimbPose.startClimbOn(player, mountEntity, type);
                        } else {
                            MountClimbPose.startClimbOff(player, mountEntity, type);
                        }
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MountClimbPackets.ClimbCancel.TYPE, (payload, context) -> {
            // Another player's climb was cancelled
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level != null) {
                    Entity playerEntity = mc.level.getEntity(payload.playerId());
                    if (playerEntity instanceof Player player) {
                        // Cancel their climb animation
                        MountClimbPose.cancelClimb(player.getId());
                        com.jaymospowers.client.EmfCompat.hold(player.getId(), false);
                    }
                }
            });
        });
    }

    /** Called from client when climb-on finishes. */
    public static void finishClimbOn(int mountId) {
        ClientPlayNetworking.send(new MountClimbPackets.ClimbOnFinish(mountId));
    }

    /** Called from client when climb-off starts. */
    public static void startClimbOff(int mountId) {
        ClientPlayNetworking.send(new MountClimbPackets.ClimbOffStart(mountId));
    }
}