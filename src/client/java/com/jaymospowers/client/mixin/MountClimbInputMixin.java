package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MountClimbPose;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Partway up onto a mount or down off it, you don't walk away (MountClimbPose); sneaking still calls a climb on off. */
@Mixin(KeyboardInput.class)
abstract class MountClimbInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$holdStill(CallbackInfo ci) {
		if (MountClimbPose.climbingLocal()) {
			this.keyPresses = new Input(false, false, false, false, false, this.keyPresses.shift(), false);
			this.moveVector = Vec2.ZERO;
		}
	}
}
