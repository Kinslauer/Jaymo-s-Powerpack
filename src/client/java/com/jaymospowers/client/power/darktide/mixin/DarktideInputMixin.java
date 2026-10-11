package com.jaymospowers.client.power.darktide.mixin;

import com.jaymospowers.client.power.darktide.DarktidePowerClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stunned by Darktide, or holding Gedan's stance: no movement keys answer until it's over. */
@Mixin(KeyboardInput.class)
abstract class DarktideInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$darktideRooted(CallbackInfo ci) {
		if (DarktidePowerClient.inputLocked()) {
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	}
}
