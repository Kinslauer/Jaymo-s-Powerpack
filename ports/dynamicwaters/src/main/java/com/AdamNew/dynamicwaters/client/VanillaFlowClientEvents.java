package com.AdamNew.dynamicwaters.client;

import com.AdamNew.dynamicwaters.DynamicWaters;
import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.VanillaRiverFlow;
import com.AdamNew.dynamicwaters.network.SyncVanillaFlowPacket;
import net.minecraft.client.Minecraft;

public final class VanillaFlowClientEvents {
    private VanillaFlowClientEvents() {
    }

    public static void receive(SyncVanillaFlowPacket packet) {
        VanillaRiverFlow.putClientChunk(packet.chunkX(), packet.chunkZ(), packet.angles());
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.levelRenderer == null) return;
        final int sea = HydrologyManager.getSeaLevel();
        for (int sy = (sea - 8) >> 4; sy <= (sea + 2) >> 4; sy++) {
            mc.levelExtractor.setSectionDirty(packet.chunkX(), sy, packet.chunkZ());
        }
    }

    public static void onLoggingOut() {
        VanillaRiverFlow.clearClient();
    }
}
