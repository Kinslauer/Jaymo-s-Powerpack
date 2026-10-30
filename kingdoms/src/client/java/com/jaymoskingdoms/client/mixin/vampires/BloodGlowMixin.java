package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.client.BloodVision;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blood vision: warm-blooded creatures near a vampire player glow through walls, for that player only. (0.7.361) And Valor's Senses. */
@Mixin(Minecraft.class)
abstract class BloodGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$bloodGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && (BloodVision.sensed(entity) || com.jaymoskingdoms.client.ValorClient.sensed(entity))) {
			cir.setReturnValue(true);
		}
	}
}
