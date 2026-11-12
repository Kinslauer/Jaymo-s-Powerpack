package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.MountainRiverBiome;
import com.AdamNew.dynamicwaters.RiverLogs;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Unique
    private volatile Holder<Biome> dynamicwaters$river;
    @Unique
    private volatile Holder<Biome> dynamicwaters$frozenRiver;
    @Unique
    private volatile boolean dynamicwaters$riversFound;

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void dynamicwaters$containAcrossChunks(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
            CallbackInfo ci) {
        if (!com.AdamNew.dynamicwaters.HydrologyManager.riversIn(level.getLevel())
                || !com.AdamNew.dynamicwaters.compat.OneDimension.carves((ChunkGenerator) (Object) this)) return;
        com.AdamNew.dynamicwaters.HydrologyManager.containAcrossChunks(level, chunk, (ChunkGenerator) (Object) this);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void dynamicwaters$riverLogs(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
            CallbackInfo ci) {
        if (RiverLogs.wanted(level) && com.AdamNew.dynamicwaters.compat.OneDimension.carves((ChunkGenerator) (Object) this)) RiverLogs.lay(level, chunk);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void dynamicwaters$mountainRiverBiome(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
            CallbackInfo ci) {
        if (!MountainRiverBiome.wanted(level) || !com.AdamNew.dynamicwaters.compat.OneDimension.carves((ChunkGenerator) (Object) this)) return;
        if (!dynamicwaters$riversFound) {
            for (Holder<Biome> holder : ((ChunkGenerator) (Object) this).getBiomeSource().possibleBiomes()) {
                if (holder.unwrapKey().isEmpty()) continue;
                final String path = holder.unwrapKey().get().identifier().getPath();
                if (dynamicwaters$river == null && path.equals("river")) {
                    dynamicwaters$river = holder;
                } else if (dynamicwaters$frozenRiver == null && path.equals("frozen_river")) {
                    dynamicwaters$frozenRiver = holder;
                }
            }
            dynamicwaters$riversFound = true;
        }
        if (dynamicwaters$river != null) {
            MountainRiverBiome.paint(level, chunk, dynamicwaters$river, dynamicwaters$frozenRiver);
        }
    }
}
