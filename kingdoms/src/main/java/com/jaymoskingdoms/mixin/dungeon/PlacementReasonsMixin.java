package com.jaymoskingdoms.mixin.dungeon;

import com.jaymoskingdoms.PlacementReasons;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A structure with a placement rule ({@link PlacementReasons}: farms, mines, stables, watchtowers, ruins) doesn't
 * generate where its reason doesn't hold. Checked before the structure lays out its pieces, so a skipped one costs
 * nothing more.
 */
@Mixin(Structure.class)
abstract class PlacementReasonsMixin {
	@Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$placementReasons(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		Structure self = (Structure)(Object)this;
		PlacementReasons.Rule rule = PlacementReasons.rule(context.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(self));
		if (rule != null && !PlacementReasons.holds(rule, context)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
