package com.jaymoskingdoms.client.mixin.sleep;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
	@Invoker("setRotation")
	void jaymoskingdoms$setRotation(float yRot, float xRot);
}
