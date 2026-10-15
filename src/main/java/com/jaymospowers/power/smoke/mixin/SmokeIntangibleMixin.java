package com.jaymospowers.power.smoke.mixin;

import com.jaymospowers.power.smoke.SmokeVapour;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wholly smoke: shots fly straight through. */
@Mixin(Entity.class)
abstract class SmokeIntangibleMixin {
	@Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$smokeShots(CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof Player player && SmokeVapour.wholly(player)) {
			cir.setReturnValue(false);
		}
	}
}
