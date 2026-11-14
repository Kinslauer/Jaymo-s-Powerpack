package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.ArmourTraits;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tool Station armour's traits turn aside their share of a blow, after vanilla's armour and enchantments ({@link ArmourTraits}). */
@Mixin(LivingEntity.class)
abstract class TinkerArmourTraitsMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$armourTraits(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(ArmourTraits.taken((LivingEntity) (Object) this, source, cir.getReturnValueF()));
	}
}
