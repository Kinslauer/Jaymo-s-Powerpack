package com.jaymoskitchen.mixin;

import com.jaymoskitchen.tanning.HideTraits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide traits worn ({@link HideTraits}): mooshroom leather shortens poison; zebra and deer leather is noticed from less far. */
@Mixin(LivingEntity.class)
abstract class HideTraitsLivingMixin {
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
		argsOnly = true)
	private MobEffectInstance jaymoskitchen$sporeProof(MobEffectInstance effect) {
		MobEffectInstance shorter = HideTraits.shorterPoison((LivingEntity) (Object) this, effect);
		return shorter == null ? effect : shorter;
	}

	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$hushed(ServerLevel level, Entity looker, CallbackInfoReturnable<Double> cir) {
		double hushed = HideTraits.hushed((LivingEntity) (Object) this);
		if (hushed < 1.0) {
			cir.setReturnValue(cir.getReturnValueD() * hushed);
		}
	}
}
