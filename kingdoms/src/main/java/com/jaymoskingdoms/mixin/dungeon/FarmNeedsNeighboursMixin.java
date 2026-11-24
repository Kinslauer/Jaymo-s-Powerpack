package com.jaymoskingdoms.mixin.dungeon;

import com.jaymoskingdoms.FarmNeighbours;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A farm in {@link FarmNeighbours#FARMS} doesn't generate unless a village or a home will stand near it
 * ({@link FarmNeighbours#near}). Checked before the structure lays out its pieces, so a skipped one costs nothing more.
 */
@Mixin(Structure.class)
abstract class FarmNeedsNeighboursMixin {
	@Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$farmsByHomes(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		Structure self = (Structure)(Object)this;
		if (context.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(self).is(FarmNeighbours.FARMS) && !FarmNeighbours.near(context)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
