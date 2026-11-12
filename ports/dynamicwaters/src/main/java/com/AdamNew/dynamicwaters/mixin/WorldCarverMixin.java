package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Function;

/**
 * Keeps carvers out of river water and river walls. In 26.3 carvers only mark a mask and the generator carves every
 * marked block in one place (applyCarvingMask), so the protection is a filter laid over that pass.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class WorldCarverMixin {
    @WrapMethod(method = "applyCarvingMask")
    private void dynamicwaters$protectRiversFromCarving(ChunkAccess chunk, CarvingMask mask, RandomState randomState,
            MaterialRule materialRule, WorldGenerationContext context, NoiseChunk noiseChunk,
            Function<BlockPos, Holder<Biome>> biomeGetter, CarvingMask.Filter filter, Operation<Void> original) {
        if (!com.AdamNew.dynamicwaters.compat.OneDimension.carves((net.minecraft.world.level.chunk.ChunkGenerator) (Object) this)) {
            original.call(chunk, mask, randomState, materialRule, context, noiseChunk, biomeGetter, filter);
            return;
        }
        final int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        final CarvingMask.Filter rivers = (x, y, z) -> (filter == null || filter.test(x, y, z))
                && !HydrologyManager.isRiverOrWallBlock(x0 + x, y, z0 + z);
        original.call(chunk, mask, randomState, materialRule, context, noiseChunk, biomeGetter, rivers);
    }
}
