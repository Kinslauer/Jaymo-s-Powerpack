package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Toughness;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poise ({@link Toughness#poise}): a blow a player stood through doesn't throw them back. Every knockback (a blow's own, a
 * mob's extra shove after it) comes through here, so the one that goes with that blow is dropped.
 */
@Mixin(LivingEntity.class)
abstract class PoiseKnockbackMixin {
	@Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$poise(double strength, double x, double z, DamageSource source, float amount, boolean flag, CallbackInfo ci) {
		if ((Object) this instanceof Player player && Toughness.steady(player, source)) {
			ci.cancel();
		}
	}
}
