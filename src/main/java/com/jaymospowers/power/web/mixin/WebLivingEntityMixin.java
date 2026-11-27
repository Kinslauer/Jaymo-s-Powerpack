package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebStrength;
import com.jaymospowers.power.web.WebBruteArts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Anything pinned by an impact web takes more damage (see WebStrength). */
@Mixin(LivingEntity.class)
abstract class WebLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$pinned(float damage, ServerLevel level, DamageSource source, float original) {
		LivingEntity entity = (LivingEntity)(Object)this;
		float amount = WebStrength.modifyDamage(entity, damage);
		return source.is(DamageTypeTags.IS_FALL) ? amount : amount * WebBruteArts.damageScale(entity);
	}
}
