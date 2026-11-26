package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Stranded;
import com.jaymoskingdoms.VillagerFixes;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager plans no drop that would hurt it ({@link VillagerFixes#maxFall}); a mob stranded on a roof or a ledge
 * ({@link Stranded}) may plan a deeper drop, to climb down.
 */
@Mixin(Mob.class)
abstract class MobFallDistanceMixin {
	@Inject(method = "getMaxFallDistance", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$stranded(CallbackInfoReturnable<Integer> cir) {
		int was = cir.getReturnValueI();
		Mob mob = (Mob) (Object) this;
		int fall = Stranded.maxFall(mob, VillagerFixes.maxFall(mob, was));
		if (fall != was) {
			cir.setReturnValue(fall);
		}
	}
}
