package com.jaymospowers.client.mixin;

import com.jaymospowers.client.GiantCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.15.143) A giant seen from behind: what the crosshair is over is picked along the camera's line, so his left click
 * hits what it points at ({@link GiantCamera#pick}). After vanilla's pick (and any other mod's), so it has the last word.
 */
@Mixin(Minecraft.class)
abstract class GiantPickMixin {
	@Shadow
	public HitResult hitResult;
	@Shadow
	public Entity crosshairPickEntity;

	@Inject(method = "pick", at = @At("TAIL"))
	private void jaymospowers$giantPick(float partialTicks, CallbackInfo ci) {
		HitResult hit = GiantCamera.pick((Minecraft)(Object)this, partialTicks);
		if (hit != null) {
			this.hitResult = hit;
			this.crosshairPickEntity = hit instanceof EntityHitResult entity ? entity.getEntity() : null;
		}
	}
}
