package com.jaymospowers.client.power.resonance.mixin;

import com.jaymospowers.client.power.resonance.ResonanceSenseClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Echolocation's outline: creatures this client hears glow through walls, here and nowhere else. */
@Mixin(Minecraft.class)
abstract class ResonanceGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$resonanceGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && ResonanceSenseClient.sensed(entity)) {
			cir.setReturnValue(true);
		}
	}
}
