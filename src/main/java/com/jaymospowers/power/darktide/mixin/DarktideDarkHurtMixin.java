package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktideDark;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Darkness feeds a Darktide or Smoke user's harm, and the wet a Darktide user's ({@link DarktideDark#incoming}). */
@Mixin(LivingEntity.class)
abstract class DarktideDarkHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$darkFeeds(float damage, @Local(argsOnly = true) DamageSource source) {
		return DarktideDark.incoming((LivingEntity) (Object) this, source, damage);
	}
}
