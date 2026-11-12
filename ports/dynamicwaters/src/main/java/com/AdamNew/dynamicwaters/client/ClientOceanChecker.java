package com.AdamNew.dynamicwaters.client;

import com.AdamNew.dynamicwaters.HydrologyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class ClientOceanChecker {
    public static boolean isOceanAtClient(BlockPos pos) {
        if (Minecraft.getInstance().level != null) {
            for (int dx = -2; dx <= 2; dx += 2) {
                for (int dz = -2; dz <= 2; dz += 2) {
                    BlockPos neighborPos = pos.offset(dx, 0, dz);
                    if (Minecraft.getInstance().level.getFluidState(neighborPos).is(net.minecraft.tags.FluidTags.WATER) &&
                        HydrologyManager.isOceanAt(Minecraft.getInstance().level, neighborPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
