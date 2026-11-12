package com.AdamNew.dynamicwaters.network;

import com.AdamNew.dynamicwaters.HydrologyManager;

public class ModNetwork {
    public static void receive(SyncSeedPacket payload, boolean ownServer) {
        if (ownServer) return;
        HydrologyManager.clearCaches();
        HydrologyManager.setSeed(payload.seed());
        HydrologyManager.setSeaLevel(payload.seaLevel());
    }

    public static void receive(SyncWatershedsPacket payload, boolean ownServer) {
        if (ownServer) return;
        HydrologyManager.putRegion(new HydrologyManager.WatershedRegionData(
                payload.paths(), payload.lakes(), payload.regionX(), payload.regionZ()));
    }

    public static void receive(SyncMainRiversPacket payload, boolean ownServer) {
        if (ownServer) return;
        com.AdamNew.dynamicwaters.MainRivers.cacheRegion(payload.regionX(), payload.regionZ(), payload.edges());
    }

}
