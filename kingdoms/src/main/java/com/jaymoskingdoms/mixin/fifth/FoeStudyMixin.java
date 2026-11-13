package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.FoeStudy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A foe you know is easier to face (and a fear you've learned can be used on it): {@link FoeStudy#dealt}. */
@Mixin(LivingEntity.class)
abstract class FoeStudyMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$foeLore(float amount, ServerLevel level, DamageSource source) {
		return FoeStudy.dealt((LivingEntity)(Object)this, source, amount);
	}
}
