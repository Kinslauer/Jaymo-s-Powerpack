package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.client.SeizureClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.185) The blood fever or the wolf has your body ({@link SeizureClient}): no movement key answers; each press is a heave against it. */
@Mixin(KeyboardInput.class)
abstract class SeizeInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$seized(CallbackInfo ci) {
		if (SeizureClient.locked()) {
			SeizureClient.struggleKeys(this.keyPresses);
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	}
}
