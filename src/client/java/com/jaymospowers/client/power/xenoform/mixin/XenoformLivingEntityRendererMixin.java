package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.client.power.xenoform.XenoformSlap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The band's slam on your own body in first person (First-person Model): its arms are shown while the slam poses them. */
@Mixin(LivingEntityRenderer.class)
abstract class XenoformLivingEntityRendererMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
	private void jaymospowers$slamArms(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar && avatar.getData(XenoformSlap.KEY) != null && XenoformSlap.ownBody(avatar)) {
			FirstPersonCompat.showArms(avatar);
		}
	}
}
