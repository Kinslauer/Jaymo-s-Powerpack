package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.Webbed;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Milk clears every effect but {@link Webbed}: it's web on you, not something in your blood. */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
abstract class WebbedMilkMixin {
	@Redirect(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z"))
	private boolean jaymospowers$keepWeb(LivingEntity user) {
		MobEffectInstance web = Webbed.EFFECT == null ? null : user.getEffect(Webbed.EFFECT);
		boolean cleared = user.removeAllEffects();
		Webbed.keep(user, web);
		return cleared;
	}
}
