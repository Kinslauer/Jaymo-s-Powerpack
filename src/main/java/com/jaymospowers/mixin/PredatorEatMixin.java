package com.jaymospowers.mixin;

import com.jaymospowers.core.LimbEaters;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A hungry predator can't be set on a downed player ({@link LimbEaters#hungryFor}), whoever sets it on (its own search, a
 * blow it took, its anger): it eats them instead, and finishes them only once it's full.
 */
@Mixin(Mob.class)
abstract class PredatorEatMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$eatTheDowned(@Nullable LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target != null && !self.level().isClientSide() && LimbEaters.hungryFor(self, target)) {
			ci.cancel();
		}
	}
}
