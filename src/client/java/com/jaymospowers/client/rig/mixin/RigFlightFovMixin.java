package com.jaymospowers.client.rig.mixin;

import com.jaymospowers.client.rig.RigFlightFeel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Rig's full flight widens the view with speed (scaled by the FOV-effects slider, like sprinting; see RigFlightFeel). */
@Mixin(AbstractClientPlayer.class)
abstract class RigFlightFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$rigSpeedFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		if ((Object)this != Minecraft.getInstance().player) {
			return;
		}
		float boost = RigFlightFeel.fovMultiplier();
		if (boost > 1.0001F && cir.getReturnValue() > 0.2F) {
			cir.setReturnValue(cir.getReturnValue() * Mth.lerp(effectScale, 1.0F, boost));
		}
	}
}
