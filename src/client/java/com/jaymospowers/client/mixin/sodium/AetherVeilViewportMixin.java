package com.jaymospowers.client.mixin.sodium;

import com.jaymospowers.client.aether.SodiumVeil;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.20.52) Every viewport Sodium culls chunks with (the camera's, and Iris' shadow pass) takes the Aether veil's ceiling as one
 * more side of its frustum, so the Aether's chunks aren't drawn from under the clouds ({@code AetherVeil}).
 */
@Mixin(value = Viewport.class, remap = false)
abstract class AetherVeilViewportMixin {
	@Shadow
	@Final
	@Mutable
	private Frustum frustum;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void jaymospowers$aetherVeil(Frustum frustum, Vector3d position, CallbackInfo ci) {
		this.frustum = SodiumVeil.wrap(this.frustum, position.y);
	}
}
