package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.BreedGifts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.305) A woolly mammoth under a Rider it trusts walks on powder snow ({@link BreedGifts#snowStrider}). */
@Mixin(PowderSnowBlock.class)
abstract class PowderSnowMixin {
	@Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$snowStrider(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (BreedGifts.snowStrider(entity)) {
			cir.setReturnValue(true);
		}
	}
}
