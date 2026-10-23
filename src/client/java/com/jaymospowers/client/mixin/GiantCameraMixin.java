package com.jaymospowers.client.mixin;

import com.jaymospowers.client.GiantCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.15.141) A giant seen from behind: the view sits back and up as far as he's grown ({@link GiantCamera}). */
@Mixin(Camera.class)
abstract class GiantCameraMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$giantView(float partialTicks, CallbackInfo ci) {
		Vec3 view = GiantCamera.position(this.minecraft, partialTicks);
		if (view != null) {
			this.setPosition(view);
		}
	}
}
