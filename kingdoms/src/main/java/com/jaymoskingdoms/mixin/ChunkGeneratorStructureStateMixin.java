package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.AtiCompounds;
import com.jaymoskingdoms.SharedPools;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes ATi's single above-ground structure sets out of a world's structure placement when its compounds are in the
 * game ({@link AtiCompounds#filter}), and pools every mod's villages and outposts into one set each
 * ({@link SharedPools#merge}): generation, /locate and maps all read this list.
 */
@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin {
	@Shadow
	@Final
	@Mutable
	private List<Holder<StructureSet>> possibleStructureSets;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void jaymoskingdoms$compounds(CallbackInfo ci) {
		this.possibleStructureSets = SharedPools.merge(AtiCompounds.filter(this.possibleStructureSets));
	}
}
