package com.jaymospowers.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.forms.Billow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.19.29) Billow is cloud: arrows and other shots fly straight through it, as through wholly smoke. Blows still land. */
@Mixin(Entity.class)
abstract class BillowIntangibleMixin {
	@Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$billowShots(CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof Player player && Billow.cloud(player)) {
			cir.setReturnValue(false);
		}
	}
}
