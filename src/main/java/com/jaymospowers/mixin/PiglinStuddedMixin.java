package com.jaymospowers.mixin;

import com.jaymospowers.tinkering.smithing.LightFoes;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A piglin brute in a full studded set doesn't zombify ({@link LightFoes#steady}). */
@Mixin(AbstractPiglin.class)
abstract class PiglinStuddedMixin {
	@Inject(method = "isImmuneToZombification", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$studded(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && LightFoes.steady((AbstractPiglin) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
