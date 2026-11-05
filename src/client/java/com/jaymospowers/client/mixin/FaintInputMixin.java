package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.FaintHud;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.20.111) Swooning from hunger or thirst (Fainting): the keys stop answering. Whatever you were doing as it began goes
 * on, slowing and weaving, till you go down (see {@link FaintHud#steer}).
 */
@Mixin(KeyboardInput.class)
abstract class FaintInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$swoon(CallbackInfo ci) {
		FaintHud.steer(this.keyPresses, this.moveVector, (keys, move) -> {
			this.keyPresses = keys;
			this.moveVector = move;
		});
	}
}
