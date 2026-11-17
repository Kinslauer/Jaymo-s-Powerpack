package com.jaymospowers.client.mixin;

import com.jaymospowers.client.StanceScroll;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.18.57) A held sneak or low crouch keeps the Sneak bit down without the key; standing by the wheel lets it up. (0.18.80)
 * On the knee in the low crouch the walking keys are dropped. (0.21.36) While Sneak is held toward a sprint slide (the 1.5 s
 * hold, {@code FreerunClient.slideWindup}), the Sneak bit is kept off so the run goes on.
 */
@Mixin(KeyboardInput.class)
abstract class StanceInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$heldStance(CallbackInfo ci) {
		Boolean shift = StanceScroll.shift();
		Input k = this.keyPresses;
		if (shift != null && shift != k.shift()) {
			this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), shift, k.sprint());
		}
		// (0.21.36) Sneak held toward a sprint slide: the run goes on, no sneak, till the hold is long enough to slide
		if (com.jaymospowers.client.freerun.FreerunClient.slideWindup()) {
			k = this.keyPresses;
			if (k.shift()) {
				this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), false, k.sprint());
			}
		}
		// (0.18.80) settled onto the knee in the low crouch: you stay put till you change stance
		if (StanceScroll.kneelHeld()) {
			k = this.keyPresses;
			this.keyPresses = new Input(false, false, false, false, k.jump(), k.shift(), false);
			this.moveVector = Vec2.ZERO;
		}
	}
}
