package com.jaymospowers.client.rig.mixin;

import com.jaymospowers.client.rig.RigFlightFeel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Rig cruising flat, seen from behind: the camera settles lower and further back (see RigFlightFeel). */
@Mixin(Camera.class)
abstract class RigFlightCameraMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$rigFlightView(float partialTicks, CallbackInfo ci) {
		Vec3 view = RigFlightFeel.camera(this.minecraft, this.position(), partialTicks);
		if (view != null) {
			this.setPosition(view);
		}
	}
}
