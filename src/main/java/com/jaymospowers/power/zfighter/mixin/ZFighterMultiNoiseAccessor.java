package com.jaymospowers.power.zfighter.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A multi-noise source's biome list (a list written out, or a preset), for {@code ZFighterNetherList}. */
@Mixin(MultiNoiseBiomeSource.class)
public interface ZFighterMultiNoiseAccessor {
	@Accessor("parameters")
	Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> jaymospowers$parameters();

	@Mutable
	@Accessor("parameters")
	void jaymospowers$setParameters(Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters);
}
