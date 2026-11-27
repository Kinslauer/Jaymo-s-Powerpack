package com.jaymospowers.client.mixin;

import com.jaymospowers.client.alchemy.DarkSightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Grey sight: living things near you in the dark are outlined, on your screen only ({@link DarkSightClient}). */
@Mixin(Minecraft.class)
abstract class DarkSightGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$greySight(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && DarkSightClient.seen(entity)) {
			cir.setReturnValue(true);
		}
	}
}
