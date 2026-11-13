package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.Saves;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Poisons and holds going on a body are shortened by its Endurance and Grit ({@link Saves#onEffect}). */
@Mixin(LivingEntity.class)
abstract class EffectSaveMixin {
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
		argsOnly = true)
	private MobEffectInstance jaymoskingdoms$save(MobEffectInstance effect) {
		return Saves.onEffect((LivingEntity) (Object) this, effect);
	}
}
