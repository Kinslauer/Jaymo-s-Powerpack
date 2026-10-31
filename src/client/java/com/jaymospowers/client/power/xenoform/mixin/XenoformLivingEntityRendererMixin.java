package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.client.power.xenoform.XenoformSlap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.jaymospowers.client.power.xenoform.XenoformClient;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two things on a player's render:
 * <ul>
 * <li>the band's slam in first person with First-person Model drawing your body: the body's arms are hidden while it
 * plays, since the slam's own hands (XenoformFirstPersonMixin) are drawn instead;</li>
 * <li>(0.18.84) as an alien, only the game's own layers and Xenoform's run (Andy: none of your weapons should show on
 * the alien). Other layers hang things off the player's own body, now invisible under the alien: holstered weapons (YDM's
 * Weapon Master), tool belts (Traveler's Tool Belt), quivers, backpacks, costumes, heritage looks.</li>
 * </ul>
 */
@Mixin(LivingEntityRenderer.class)
abstract class XenoformLivingEntityRendererMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
	private void jaymospowers$slamArms(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar && avatar.getData(XenoformSlap.KEY) != null && XenoformSlap.ownBody(avatar)) {
			FirstPersonCompat.hideArms(avatar);
		}
	}

	@WrapWithCondition(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
	private boolean jaymospowers$alienLayers(RenderLayer<?, ?> layer, PoseStack poseStack, SubmitNodeCollector collector, int light, EntityRenderState state,
		float yRot, float xRot) {
		if (!(state instanceof AvatarRenderState avatar) || avatar.getData(XenoformClient.FORM) == null) {
			return true;
		}
		String name = layer.getClass().getName();
		return name.startsWith("net.minecraft.") || name.startsWith("com.jaymospowers.client.power.xenoform.");
	}
}
