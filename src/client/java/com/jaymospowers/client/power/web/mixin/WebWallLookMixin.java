package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebSymbioteClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hung off a wall by the symbiote's tethers, your view is held facing away from it. */
@Mixin(Entity.class)
abstract class WebWallLookMixin {
	@Inject(method = "turn", at = @At("TAIL"))
	private void jaymospowers$holdLook(double xo, double yo, CallbackInfo ci) {
		WebSymbioteClient.holdLook((Entity)(Object)this);
	}
}
