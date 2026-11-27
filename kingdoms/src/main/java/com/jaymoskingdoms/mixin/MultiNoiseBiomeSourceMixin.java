package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.lands.Lands;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where the game's multi-noise biome source picks a biome, our lands may take its place ({@link Lands#pick}): both of its
 * resolvers (the one for chunk generation and the one for everything else) are wrapped. A source that has none of our biomes
 * (the Nether's, a data pack's) is left as it is.
 */
@Mixin(MultiNoiseBiomeSource.class)
abstract class MultiNoiseBiomeSourceMixin {
	@Unique
	private Map<ResourceKey<Biome>, Holder<Biome>> jaymoskingdoms$ours;

	@Unique
	private Map<ResourceKey<Biome>, Holder<Biome>> jaymoskingdoms$ours() {
		if (this.jaymoskingdoms$ours == null) {
			this.jaymoskingdoms$ours = Lands.holders(((BiomeSource)(Object)this).possibleBiomes());
		}
		return this.jaymoskingdoms$ours;
	}

	@Inject(method = "createResolver", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$lands(Climate.Sampler sampler, CallbackInfoReturnable<BiomeResolver> cir) {
		Map<ResourceKey<Biome>, Holder<Biome>> ours = this.jaymoskingdoms$ours();
		if (!ours.isEmpty()) {
			BiomeResolver base = cir.getReturnValue();
			cir.setReturnValue((x, y, z) -> Lands.pick(base.getNoiseBiome(x, y, z), x, y, z, sampler, ours));
		}
	}

	@Inject(method = "createResolverForChunk", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$landsForChunk(Climate.Sampler sampler, int x, int y, int z, int sizeX, int sizeY, int sizeZ,
			CallbackInfoReturnable<BiomeResolver> cir) {
		Map<ResourceKey<Biome>, Holder<Biome>> ours = this.jaymoskingdoms$ours();
		if (!ours.isEmpty()) {
			BiomeResolver base = cir.getReturnValue();
			cir.setReturnValue((qx, qy, qz) -> Lands.pick(base.getNoiseBiome(qx, qy, qz), qx, qy, qz, sampler, ours));
		}
	}
}
