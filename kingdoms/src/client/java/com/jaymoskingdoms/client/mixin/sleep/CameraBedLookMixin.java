package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.RestClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** (0.7.5) In bed the view turns from straight along the bed by where you've dragged it ({@link RestClient#look}). */
@Mixin(Camera.class)
abstract class CameraBedLookMixin {
	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 3))
	private void jaymoskingdoms$lookFromBed(Camera camera, float yRot, float xRot) {
		((CameraAccessor) camera).jaymoskingdoms$setRotation(yRot + RestClient.lookYaw(), xRot + RestClient.lookPitch());
	}
}
