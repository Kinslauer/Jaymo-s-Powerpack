package com.jaymoskingdoms.client.mixin.thirst;

import com.jaymoskingdoms.client.thirst.ThirstClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The thirsty bat's sight: the living nearby glow through walls, for that player only. */
@Mixin(Minecraft.class)
abstract class ThirstGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$thirstGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && ThirstClient.sensed(entity)) {
			cir.setReturnValue(true);
		}
	}
}
