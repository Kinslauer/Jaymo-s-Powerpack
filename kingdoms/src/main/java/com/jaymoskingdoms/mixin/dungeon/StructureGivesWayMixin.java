package com.jaymoskingdoms.mixin.dungeon;

import com.jaymoskingdoms.dungeon.StormbornTomb;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A structure in {@link StormbornTomb#GIVES_WAY} (trial chambers, ancient cities) doesn't generate where a Stormborn Tomb
 * will ({@link StormbornTomb#near}), so its rooms don't cut through the tomb's halls. Checked before the structure lays
 * out its pieces, so a skipped one costs nothing more.
 */
@Mixin(Structure.class)
abstract class StructureGivesWayMixin {
	@Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$giveWayToTombs(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		Structure self = (Structure)(Object)this;
		if (!(self instanceof StormbornTomb) && context.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(self).is(StormbornTomb.GIVES_WAY)
			&& StormbornTomb.near(context)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
