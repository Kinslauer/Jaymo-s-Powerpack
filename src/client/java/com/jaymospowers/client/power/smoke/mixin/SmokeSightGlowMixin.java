package com.jaymospowers.client.power.smoke.mixin;

import com.jaymospowers.client.power.smoke.SmokeSightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Smoke Sight: creatures in smoke glow through walls, on the user's screen only. */
@Mixin(Minecraft.class)
abstract class SmokeSightGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$smokeSight(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && SmokeSightClient.color(entity) != 0) {
			cir.setReturnValue(true);
		}
	}
}
