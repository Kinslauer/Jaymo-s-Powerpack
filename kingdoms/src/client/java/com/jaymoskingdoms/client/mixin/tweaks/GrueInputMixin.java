package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.spirits.GrueClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.465) The Grue walks its host (or a spirit pulls its keeper out of flux) ({@link GrueClient}): no movement key answers. */
@Mixin(KeyboardInput.class)
abstract class GrueInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$driven(CallbackInfo ci) {
		if (GrueClient.driving() || GrueClient.hostLocked()) {
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	}
}
