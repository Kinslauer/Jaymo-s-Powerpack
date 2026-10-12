package com.jaymoskingdoms.mixin.mca;

import java.util.HashSet;
import java.util.Set;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Traits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.5.41) No rainbow hair: MCA's Rainbow Hair trait never shows, on new villagers or ones already in the world. They
 * keep their natural hair colour, and the trait drops off their trait list.
 */
@Mixin(Traits.class)
abstract class McaTraitsMixin {
	@Inject(method = "hasTrait(Lnet/conczin/mca/entity/VillagerLike;Lnet/conczin/mca/entity/ai/Traits$Trait;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noRainbow(VillagerLike<?> entity, Traits.Trait trait, CallbackInfoReturnable<Boolean> cir) {
		if (trait == Traits.RAINBOW) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getTraits", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$hideRainbow(CallbackInfoReturnable<Set<Traits.Trait>> cir) {
		if (cir.getReturnValue().contains(Traits.RAINBOW)) {
			Set<Traits.Trait> traits = new HashSet<>(cir.getReturnValue());
			traits.remove(Traits.RAINBOW);
			cir.setReturnValue(traits);
		}
	}
}
