package com.jaymospowers.power.zfighter.mixin;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A biome source's cached biome set, so {@code ZFighterNetherList} can make it again after changing the Nether's list. */
@Mixin(BiomeSource.class)
public interface ZFighterBiomeSourceAccessor {
	@Mutable
	@Accessor("possibleBiomes")
	void jaymospowers$setPossibleBiomes(Supplier<Set<Holder<Biome>>> possibleBiomes);

	@Invoker("collectPossibleBiomes")
	Stream<Holder<Biome>> jaymospowers$collectPossibleBiomes();
}
