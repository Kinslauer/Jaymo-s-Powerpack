package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.gear.AlchemyGear;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.17.0) Ichorcloth turns aside part of every magic blow ({@link AlchemyGear#magicTaken}), after vanilla's own magic protection. */
@Mixin(LivingEntity.class)
abstract class IchorclothMagicMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$ichorcloth(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(AlchemyGear.magicTaken((LivingEntity)(Object)this, source, cir.getReturnValueF()));
	}
}
