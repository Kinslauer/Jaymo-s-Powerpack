package com.jaymospowers.power.blindness;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Registers blindness network packets. */
public final class BlindnessNet {
    private BlindnessNet() {
    }

    public static void register() {
        BlindnessEchoPayload.register();
        ClientPlayNetworking.registerGlobalReceiver(BlindnessEchoPayload.TYPE, (payload, context) -> {
            // Handle on client
            ClientPlayNetworking.handle(payload, context);
        });
    }

    private static void handle(BlindnessEchoPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                BlindnessEcholocationClient.onEcho(payload);
            }
        });
    }
}