package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktidePower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Darktide user never runs out of air (both sides, so no bubbles flicker on the HUD). */
@Mixin(LivingEntity.class)
abstract class DarktideAirMixin {
	@Inject(method = "decreaseAirSupply(I)I", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$darktideBreathe(int air, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof Player player && DarktidePower.has(player)) {
			cir.setReturnValue(air);
		}
	}
}
