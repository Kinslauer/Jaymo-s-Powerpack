package com.jaymospowers.client.mixin;

import com.jaymospowers.client.StanceScroll;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.18.57) A held sneak or low crouch keeps the Sneak bit down without the key; standing by the wheel lets it up. */
@Mixin(KeyboardInput.class)
abstract class StanceInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$heldStance(CallbackInfo ci) {
		Boolean shift = StanceScroll.shift();
		Input k = this.keyPresses;
		if (shift != null && shift != k.shift()) {
			this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), shift, k.sprint());
		}
	}
}
