package com.jaymospowers.client.mixin;

import com.jaymospowers.client.aether.AetherVeil;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.20.52) Anything whose box sits at or above the Aether veil's ceiling is outside the camera's view while you're under the
 * clouds: the Aether's mobs, aerwhales and whatever else flies there ({@code AetherVeil}; its chunks are Sodium's, hidden in
 * {@code mixin/sodium}).
 */
@Mixin(Frustum.class)
abstract class AetherVeilFrustumMixin {
	@Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$aetherVeil(AABB box, CallbackInfoReturnable<Boolean> cir) {
		if (AetherVeil.hides(box.minY)) {
			cir.setReturnValue(false);
		}
	}
}
