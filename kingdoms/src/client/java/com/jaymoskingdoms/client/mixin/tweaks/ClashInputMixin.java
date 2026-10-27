package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.spirits.ClashCinematic;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Two spirits are locked arm to arm ({@link ClashCinematic}): no movement key answers while it lasts. */
@Mixin(KeyboardInput.class)
abstract class ClashInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$clashing(CallbackInfo ci) {
		if (ClashCinematic.locked()) {
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	}
}
