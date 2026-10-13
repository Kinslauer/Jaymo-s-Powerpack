package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebSymbioteClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A symbiote takeover has your body: no movement key answers (each press is a struggle against its hold instead); nor while it coils to leap you clear. */
@Mixin(KeyboardInput.class)
abstract class WebTakeoverInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$takeover(CallbackInfo ci) {
		if (WebSymbioteClient.inputLocked()) {
			WebSymbioteClient.struggleKeys(this.keyPresses);
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		} else if (WebSymbioteClient.escaping()) {
			// coiling to leap you clear after a meal: it has the legs (no struggle against this)
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	}
}
