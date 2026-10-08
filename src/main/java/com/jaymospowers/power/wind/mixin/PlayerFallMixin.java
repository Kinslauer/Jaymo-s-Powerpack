package com.jaymospowers.power.wind.mixin;

import com.jaymospowers.power.wind.WindPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No fall damage while flying, or when landing after a flight. ({@code WindPower.tick} also resets the fall distance
 * every tick; this catches the landing tick itself, where up to one tick of falling could still count.)
 */
@Mixin(Player.class)
abstract class PlayerFallMixin {
	@Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$windNoFallDamage(double fallDistance, float damageModifier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof ServerPlayer player && WindPower.INSTANCE.protectsFromFall(player)) {
			cir.setReturnValue(false);
		}
	}
}
