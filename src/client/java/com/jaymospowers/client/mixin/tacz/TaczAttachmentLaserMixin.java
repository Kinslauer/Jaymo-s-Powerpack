package com.jaymospowers.client.mixin.tacz;

import com.jaymospowers.client.armory.GunLaserClient;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (ad_laser) TaCZ draws a fitted laser's beam from the attachment's own model, handed the attachment, not the gun; the gun
 * it sits on (whose data says whether the laser is off) is noted here while that model draws ({@link GunLaserClient#drawing}),
 * for {@code TaczLaserBeamMixin}.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.model.BedrockAttachmentModel", remap = false)
abstract class TaczAttachmentLaserMixin {
	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void jaymospowers$gun(ItemStack attachment, ItemStack gun, com.mojang.blaze3d.vertex.PoseStack poseStack,
		net.minecraft.world.item.ItemDisplayContext context, net.minecraft.resources.Identifier texture,
		net.minecraft.client.renderer.rendertype.RenderType type, int light, int overlay, CallbackInfo ci) {
		GunLaserClient.drawing = gun;
	}

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void jaymospowers$done(ItemStack attachment, ItemStack gun, com.mojang.blaze3d.vertex.PoseStack poseStack,
		net.minecraft.world.item.ItemDisplayContext context, net.minecraft.resources.Identifier texture,
		net.minecraft.client.renderer.rendertype.RenderType type, int light, int overlay, CallbackInfo ci) {
		GunLaserClient.drawing = ItemStack.EMPTY;
	}
}
