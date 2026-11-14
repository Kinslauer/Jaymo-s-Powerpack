package com.jaymoskitchen.mixin;

import com.jaymoskitchen.delight.Backstabbing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A knife's Backstabbing: a blow from behind is raised before anything else weighs it ({@link Backstabbing}). */
@Mixin(LivingEntity.class)
abstract class BackstabMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskitchen$backstab(float amount, ServerLevel level, DamageSource source) {
		return Backstabbing.damage((LivingEntity) (Object) this, level, source, amount);
	}
}
