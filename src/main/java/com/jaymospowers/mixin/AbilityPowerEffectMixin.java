package com.jaymospowers.mixin;

import com.jaymospowers.core.AbilityPower;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** (Awakening pick) Ability Power: what a power's move lays on someone else lasts longer ({@link AbilityPower#lengthen}). */
@Mixin(LivingEntity.class)
abstract class AbilityPowerEffectMixin {
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
		argsOnly = true)
	private MobEffectInstance jaymospowers$abilityPower(MobEffectInstance effect) {
		return AbilityPower.lengthen((LivingEntity)(Object)this, effect);
	}
}
