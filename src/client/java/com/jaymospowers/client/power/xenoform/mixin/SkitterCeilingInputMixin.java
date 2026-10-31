package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.SkitterCeiling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.18.112, Scan E) Skitter on a ceiling, its view rolled over: left and right strafe the way the screen shows. */
@Mixin(KeyboardInput.class)
abstract class SkitterCeilingInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymospowers$skitterStrafe(CallbackInfo ci) {
		if (SkitterCeiling.overturned(Minecraft.getInstance().player)) {
			this.moveVector = new Vec2(-this.moveVector.x, this.moveVector.y);
		}
	}
}
