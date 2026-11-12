package com.AdamNew.dynamicwaters.client;

public class ClientSetup {
    public static void clientSetup() {
        com.AdamNew.dynamicwaters.HydrologyManager.setDrawnLevelRivers(() -> {
            final net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null || level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        });
    }
}
