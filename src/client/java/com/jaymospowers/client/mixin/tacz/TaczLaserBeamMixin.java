package com.jaymospowers.client.mixin.tacz;

import com.jaymospowers.armory.GunLasers;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (ad_laser) A laser switched off ({@link GunLasers#OFF}) draws no beam. TaCZ (GPL-3.0, optional, nothing of it copied)
 * draws a fitted laser's beam always; both its ways of drawing it are skipped for a gun whose laser is off. The
 * accelerated one answers "done" so nothing falls back to drawing it the other way.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.model.functional.BeamRenderer", remap = false)
abstract class TaczLaserBeamMixin {
	@Inject(method = "renderLaserBeam", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$off(ItemStack gun, com.mojang.blaze3d.vertex.PoseStack poseStack,
		net.minecraft.world.item.ItemDisplayContext context, java.util.List<?> parts, CallbackInfo ci) {
		if (GunLasers.off(gun) || GunLasers.off(com.jaymospowers.client.armory.GunLaserClient.drawing)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderLaserBeamAccelerated", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$offAccelerated(ItemStack gun, com.mojang.blaze3d.vertex.PoseStack poseStack,
		net.minecraft.world.item.ItemDisplayContext context, java.util.List<?> parts, CallbackInfoReturnable<Boolean> cir) {
		if (GunLasers.off(gun) || GunLasers.off(com.jaymospowers.client.armory.GunLaserClient.drawing)) {
			cir.setReturnValue(true);
		}
	}
}
