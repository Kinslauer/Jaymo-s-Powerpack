package com.jaymoskingdoms.client.mixin.occult;

import com.jaymoskingdoms.client.OccultClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exposure, Unveiled: the hidden cursed near you glow (in violet, {@code EtherealSightMixin}), for you alone. */
@Mixin(Minecraft.class)
abstract class UnveiledGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$unveiled(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && OccultClient.unveiled(entity)) {
			cir.setReturnValue(true);
		}
	}
}
