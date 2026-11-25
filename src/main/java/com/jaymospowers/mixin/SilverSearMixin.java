package com.jaymospowers.mixin;

import com.jaymospowers.combat.SilverSear;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** {@link SilverSear}: a silver blade's blow on what silver burns lands harder. After {@link EdgeLivingMixin}, so a dull edge doesn't dull the burn. */
@Mixin(value = LivingEntity.class, priority = 960)
abstract class SilverSearMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$silverSear(float damage, @Local(argsOnly = true) DamageSource source) {
		return SilverSear.dealt((LivingEntity) (Object) this, source, damage);
	}
}
