package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.XenoBand;
import com.jaymospowers.client.power.xenoform.XenoformClient;
import com.jaymospowers.client.power.xenoform.XenoformSlap;
import com.jaymospowers.core.Carry;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xenoform in first person. Two things:
 * <ul>
 * <li>as an alien, your arm still shows: the renderer skips the bare arm of an invisible body, and the body was made
 * invisible so the alien could stand in for it, so the flag is lifted for the hands and put back after;</li>
 * <li>the slam of the band (see XenoformSlap): the left forearm comes up across the view with the band on its wrist, the
 * right fist rises and drops onto it. While it plays, the usual hands are replaced.</li>
 * </ul>
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class XenoformFirstPersonMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Unique
	private boolean jaymospowers$liftedInvisible;

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$xenoformHands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		LocalPlayer self = this.minecraft.player;
		AvatarRenderState avatar = player.avatarRenderState;
		if (self == null || avatar == null || !XenoformPower.has(self)) {
			return;
		}
		if (avatar.getData(XenoformClient.FORM) != null && avatar.isInvisible && !self.isInvisible()) {
			avatar.isInvisible = false;
			this.jaymospowers$liftedInvisible = true;
		}
		XenoformSlap.Pose pose = avatar.getData(XenoformSlap.KEY);
		if (pose == null || hands.isScoping || self.getAttached(Carry.CARRYING) != null) {
			return;
		}
		float raise = pose.raise(), strike = pose.strike();
		poseStack.pushPose();
		// the same lag behind the view as the usual hands
		poseStack.rotateDegrees(Axis.XP, (hands.viewXRot - hands.xBob) * 0.1F);
		poseStack.rotateDegrees(Axis.YP, (hands.viewYRot - hands.yBob) * 0.1F);
		int light = avatar.lightCoords;
		XenoformArmInvoker arms = (XenoformArmInvoker)this;
		// the band's wrist: up and in across the lower view, the forearm turned nearly level
		poseStack.pushPose();
		poseStack.translate(0.1F * raise, 0.3F * raise, 0.05F * raise);
		poseStack.rotateDegrees(Axis.ZP, -25.0F * raise);
		arms.jaymospowers$renderPlayerArm(poseStack, collector, light, 0.0F, 0.0F, HumanoidArm.LEFT, player);
		if (avatar.getData(XenoformClient.FORM) == null) {
			// the pose stack now carries the arm's own placement: the band sits on that wrist
			XenoBand.submit(XenoBand.firstPerson(), poseStack, collector, light);
		}
		poseStack.popPose();
		// the striking hand: up and back, then down and across onto the wrist
		poseStack.pushPose();
		poseStack.translate(-0.5F * strike, 0.35F * raise - 0.3F * strike, -0.1F * raise + 0.1F * strike);
		poseStack.rotateDegrees(Axis.XP, 35.0F * raise - 25.0F * strike);
		arms.jaymospowers$renderPlayerArm(poseStack, collector, light, 0.0F, 0.0F, HumanoidArm.RIGHT, player);
		poseStack.popPose();
		poseStack.popPose();
		this.jaymospowers$restoreInvisible(player);
		ci.cancel();
	}

	@Unique
	private void jaymospowers$restoreInvisible(PlayerRenderState player) {
		if (this.jaymospowers$liftedInvisible) {
			this.jaymospowers$liftedInvisible = false;
			if (player.avatarRenderState != null) {
				player.avatarRenderState.isInvisible = true;
			}
		}
	}

	@Inject(method = "submitHandsWithItems", at = @At("RETURN"))
	private void jaymospowers$xenoformHandsDone(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		this.jaymospowers$restoreInvisible(player);
	}
}
