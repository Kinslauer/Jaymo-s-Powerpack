package com.jaymospowers.mixin;

import com.jaymospowers.core.TeamTime;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (Awakening pick) A power's slow on an enemy player catches the caster's teammates near them too ({@link TeamTime#effect}). */
@Mixin(LivingEntity.class)
abstract class TeamTimeMixin {
	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"))
	private void jaymospowers$teamTime(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
		TeamTime.effect((LivingEntity)(Object)this, effect);
	}
}
