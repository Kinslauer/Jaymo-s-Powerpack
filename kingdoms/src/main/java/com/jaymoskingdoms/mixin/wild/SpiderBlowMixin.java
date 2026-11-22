package com.jaymoskingdoms.mixin.wild;

import com.jaymoskingdoms.wild.SpiderSquash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Any one blow someone lands on a small or middling spider kills it ({@link SpiderSquash#dealt}). */
@Mixin(LivingEntity.class)
abstract class SpiderBlowMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$oneBlow(float amount, ServerLevel level, DamageSource source) {
		return SpiderSquash.dealt((LivingEntity) (Object) this, source, amount);
	}
}
