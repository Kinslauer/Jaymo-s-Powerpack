package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebStrength;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Anything pinned by an impact web takes more damage (see WebStrength). */
@Mixin(LivingEntity.class)
abstract class WebLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$pinned(float damage) {
		return WebStrength.modifyDamage((LivingEntity) (Object) this, damage);
	}
}
