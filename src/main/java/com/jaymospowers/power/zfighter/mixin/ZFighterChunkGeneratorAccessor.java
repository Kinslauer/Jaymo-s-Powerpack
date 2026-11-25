package com.jaymospowers.power.zfighter.mixin;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A chunk generator's cached feature order. The client's world-open check ({@code ChunkGenerator.validate}) fills it before
 * the server starts, so {@code ZFighterNetherList} makes it again once the Nether's list has our biomes in it.
 */
@Mixin(ChunkGenerator.class)
public interface ZFighterChunkGeneratorAccessor {
	@Mutable
	@Accessor("featuresPerStep")
	void jaymospowers$setFeaturesPerStep(Supplier<List<FeatureSorter.StepFeatureData>> featuresPerStep);

	@Accessor("generationSettingsGetter")
	Function<Holder<Biome>, BiomeGenerationSettings> jaymospowers$generationSettingsGetter();
}
