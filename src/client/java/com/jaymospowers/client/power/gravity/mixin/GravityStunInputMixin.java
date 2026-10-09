package com.jaymospowers.client.power.gravity.mixin;

import com.jaymospowers.client.power.gravity.GravityHeldClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stunned in a Gravity user's Lift: no movement keys answer. */
@Mixin(KeyboardInput.class)
abstract class GravityStunInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$stunned(CallbackInfo ci) {
		if (GravityHeldClient.stunned()) {
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	}
}
