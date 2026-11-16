package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.EpicTowns;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An Epic town's biomes are all the land of its kind ({@link EpicTowns#lands}), not only its tag: generation, /locate
 * and the structure checks all ask {@code biomes()}.
 */
@Mixin(Structure.class)
abstract class StructureBiomesMixin {
	@Inject(method = "biomes", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$epicLands(CallbackInfoReturnable<HolderSet<Biome>> cir) {
		HolderSet<Biome> lands = EpicTowns.lands((Structure) (Object) this);
		if (lands != null) {
			cir.setReturnValue(lands);
		}
	}
}
