package com.jaymospowers.power.zfighter.mixin;

import com.jaymospowers.power.zfighter.ZFighterHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** No foe fells a Z Fighter twice: an old victor hits him for less and takes more from him (see ZFighterBattle). */
@Mixin(LivingEntity.class)
abstract class ZFighterLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$neverTwice(float damage, @Local(argsOnly = true) DamageSource source) {
		return ZFighterHooks.modifyDamage((LivingEntity) (Object) this, source, damage);
	}
}
