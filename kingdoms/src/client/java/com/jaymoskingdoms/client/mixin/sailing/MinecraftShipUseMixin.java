package com.jaymoskingdoms.client.mixin.sailing;

import com.jaymoskingdoms.client.ShipClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.4) Right-clicking a ship's helm or chest (nearer than any world block) uses it. */
@Mixin(Minecraft.class)
abstract class MinecraftShipUseMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$useShip(CallbackInfo ci) {
		if (ShipClient.use((Minecraft)(Object)this)) {
			ci.cancel();
		}
	}
}
