package com.jaymoskingdoms.client.mixin.sailing;

import com.jaymoskingdoms.client.ShipClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.18) At a ship's helm, a left click fires a broadside instead of swinging. */
@Mixin(Minecraft.class)
abstract class MinecraftBroadsideMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$broadside(CallbackInfoReturnable<Boolean> cir) {
		if (ShipClient.broadside((Minecraft)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
