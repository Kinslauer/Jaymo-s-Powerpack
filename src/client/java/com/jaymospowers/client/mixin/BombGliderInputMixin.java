package com.jaymospowers.client.mixin;

import com.jaymospowers.armory.BombGlider;
import com.jaymospowers.client.armory.BombGliderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.13.7) On a bomb glider the sprint bit means "dive": in the air Sneak is sent as it (so it dives rather than
 * dismounts; Sneak with Jump still bails out), and the sprint key itself is dropped (toggled sprint would dive forever).
 */
@Mixin(KeyboardInput.class)
abstract class BombGliderInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$gliderDive(CallbackInfo ci) {
		BombGlider glider = BombGliderClient.riding(Minecraft.getInstance().player);
		if (glider == null) {
			return;
		}
		Input k = this.keyPresses;
		boolean dive = k.shift() && !k.jump() && !glider.landed();
		this.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), k.shift() && !dive, dive);
	}
}
