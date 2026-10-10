package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoFlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stunned by a Time Flow blow: no movement keys answer until it wears off. */
@Mixin(KeyboardInput.class)
abstract class ChronoStunInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$chronoStunned(CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && ChronoFlow.stunned(player)) {
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	}
}
