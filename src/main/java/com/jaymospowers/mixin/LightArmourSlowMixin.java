package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourEase;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Mail shortens a Slowness added to its wearer (see {@link LightArmour#slownessKept}). */
@Mixin(LivingEntity.class)
abstract class LightArmourSlowMixin {
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance jaymospowers$mailShortensSlowness(MobEffectInstance effect) {
		if (!effect.getEffect().is(MobEffects.SLOWNESS) || effect.isInfiniteDuration()) {
			return effect;
		}
		float kept = ArmourEase.slownessKept((LivingEntity) (Object) this);
		return kept >= 1.0F ? effect : effect.withScaledDuration(kept);
	}
}
