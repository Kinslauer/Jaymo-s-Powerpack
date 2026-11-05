package com.jaymospowers.mixin.lithostitched;

import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * One Dimension x Lithostitched (0.20.34). Lithostitched 2.0.6 injects {@code fillBands} at the head of
 * {@link NoiseBasedChunkGenerator#buildTerrain} and calls {@code carverBiomeRegion.registryAccess()}, though vanilla
 * marks that argument nullable. One Dimension's {@code codx.customchunks.gen.StackedChunkGenerator} builds each layer
 * into a scratch chunk and passes null there on purpose (vanilla then skips the region-based carver biomes), so every
 * stacked chunk threw and a new world hung at "Selecting global world spawn". This skips only Lithostitched's handler
 * when the region is null; vanilla still gets the null it was given. The skipped step only reseeds the clay bands of
 * Lithostitched's bandlands registry, which the next chunk with a region (or none, under One Dimension) would redo.
 * Lithostitched is MIT; nothing of it is copied. Without Lithostitched (the dev game) the target handler is absent and
 * this applies to nothing ({@code defaultRequire: 0}).
 */
@Mixin(value = NoiseBasedChunkGenerator.class, priority = 1500)
public class LithostitchedBandsMixin {
	@TargetHandler(mixin = "dev.worldgen.lithostitched.mixin.common.NoiseBasedChunkGeneratorMixin", name = "fillBands", prefix = "handler")
	@Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$skipBandsWithoutRegion(ChunkAccess chunk, Blender blender, RandomState randomState,
			StructureManager structureManager, BiomeManager biomeManager, WorldGenRegion carverBiomeRegion,
			Set<Holder<Biome>> possibleBiomes, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> originalCir,
			CallbackInfo ci) {
		if (carverBiomeRegion == null) {
			ci.cancel();
		}
	}
}
