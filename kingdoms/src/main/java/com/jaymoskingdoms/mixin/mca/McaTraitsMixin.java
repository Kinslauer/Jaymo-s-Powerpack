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
 * (0.5.41) Traits Andy turned off: MCA's Rainbow Hair and Homosexual traits never count, on new villagers or ones already
 * in the world, and drop off their trait lists. Villagers keep their natural hair colour.
 */
@Mixin(Traits.class)
abstract class McaTraitsMixin {
	@Inject(method = "hasTrait(Lnet/conczin/mca/entity/VillagerLike;Lnet/conczin/mca/entity/ai/Traits$Trait;)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$traitOff(VillagerLike<?> entity, Traits.Trait trait, CallbackInfoReturnable<Boolean> cir) {
		if (jaymoskingdoms$off(trait)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getTraits", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$hideOff(CallbackInfoReturnable<Set<Traits.Trait>> cir) {
		if (cir.getReturnValue().stream().anyMatch(McaTraitsMixin::jaymoskingdoms$off)) {
			Set<Traits.Trait> traits = new HashSet<>(cir.getReturnValue());
			traits.removeIf(McaTraitsMixin::jaymoskingdoms$off);
			cir.setReturnValue(traits);
		}
	}

	private static boolean jaymoskingdoms$off(Traits.Trait trait) {
		return trait == Traits.RAINBOW || trait == Traits.HOMOSEXUAL;
	}
}
