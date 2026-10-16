package com.jaymospowers.client.rig.mixin;

import com.jaymospowers.client.rig.RigScanClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.11.87) The Rig's scan finds and its locked target glow through walls, on the wearer's screen only. */
@Mixin(Minecraft.class)
abstract class RigGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$rigScan(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && RigScanClient.color(entity) != 0) {
			cir.setReturnValue(true);
		}
	}
}
