package com.jaymospowers.client.mixin;

import com.jaymospowers.client.moving.AutoWalk;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto walk ({@link AutoWalk}): the forward key is held for you once the keys are read. */
@Mixin(KeyboardInput.class)
abstract class AutoWalkInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$autoWalk(CallbackInfo ci) {
		AutoWalk.steer(this.keyPresses, this.moveVector, (keys, move) -> {
			this.keyPresses = keys;
			this.moveVector = move;
		});
	}
}
