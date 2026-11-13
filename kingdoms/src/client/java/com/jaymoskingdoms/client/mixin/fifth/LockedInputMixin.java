package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.client.FifthClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Held fast, asleep or startled: no key moves the body (walking, jumping, sneaking); the eyes stay free ({@link FifthClient#locked}). */
@Mixin(KeyboardInput.class)
abstract class LockedInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$locked(CallbackInfo ci) {
		if (FifthClient.locked()) {
			this.keyPresses = new Input(false, false, false, false, false, false, false);
			this.moveVector = Vec2.ZERO;
		}
	}
}
