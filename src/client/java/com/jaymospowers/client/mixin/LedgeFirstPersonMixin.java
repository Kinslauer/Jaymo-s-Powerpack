package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CarryPose;
import com.jaymospowers.client.LedgeClient;
import com.jaymospowers.client.LedgePose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On a ledge, in first person: both bare hands grip the edge at the top of the view, then press down through the climb.
 * And on a wall, climbing it: both hands on it, hand over hand ({@code ClimbFirstPerson}).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class LedgeFirstPersonMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$ledgeHands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!LedgeClient.active() || minecraft.player == null || player.avatarRenderState == null || hands.isScoping) {
			return;
		}
		if (com.jaymospowers.client.power.web.FirstPersonCompat.bodyShown()) {
			// First-person Model draws your body, its arms already holding on: no second pair
			ci.cancel();
			return;
		}
		float climb = LedgeClient.localProgress(partialTicks);
		float time = minecraft.player.tickCount + partialTicks;
		poseStack.pushPose();
		// the same lag behind the view as the usual hands
		poseStack.rotateDegrees(Axis.XP, (hands.viewXRot - hands.xBob) * 0.1F);
		poseStack.rotateDegrees(Axis.YP, (hands.viewYRot - hands.yBob) * 0.1F);
		int light = player.avatarRenderState.lightCoords;
		for (HumanoidArm arm : HumanoidArm.values()) {
			poseStack.pushPose();
			if (LedgePose.firstPersonArm(poseStack, climb, time, arm == HumanoidArm.RIGHT, CarryPose.FIRST_PERSON_EQUIP)) {
				((CarryArmInvoker)this).jaymospowers$renderPlayerArm(poseStack, collector, light, CarryPose.FIRST_PERSON_EQUIP, 0.0F, arm, player);
			}
			poseStack.popPose();
		}
		poseStack.popPose();
		ci.cancel();
	}

	/** Climbing a wall, in first person: both bare hands on it, going hand over hand (see ClimbFirstPerson). */
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$climbHands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!com.jaymospowers.client.freerun.FreerunClient.clinging() || LedgeClient.active() || minecraft.player == null) {
			com.jaymospowers.client.freerun.ClimbFirstPerson.stop();
			return;
		}
		com.jaymospowers.client.freerun.ClimbFirstPerson.track(minecraft.player, partialTicks);
		if (player.avatarRenderState == null || hands.isScoping) {
			return;
		}
		if (com.jaymospowers.client.power.web.FirstPersonCompat.bodyShown()) {
			// First-person Model draws your body, its arms already on the wall: no second pair
			ci.cancel();
			return;
		}
		float time = minecraft.player.tickCount + partialTicks;
		poseStack.pushPose();
		// the same lag behind the view as the usual hands
		poseStack.rotateDegrees(Axis.XP, (hands.viewXRot - hands.xBob) * 0.1F);
		poseStack.rotateDegrees(Axis.YP, (hands.viewYRot - hands.yBob) * 0.1F);
		int light = player.avatarRenderState.lightCoords;
		for (HumanoidArm arm : HumanoidArm.values()) {
			poseStack.pushPose();
			com.jaymospowers.client.freerun.ClimbFirstPerson.firstPersonArm(poseStack, minecraft.player, time, arm == HumanoidArm.RIGHT,
				CarryPose.FIRST_PERSON_EQUIP);
			((CarryArmInvoker)this).jaymospowers$renderPlayerArm(poseStack, collector, light, CarryPose.FIRST_PERSON_EQUIP, 0.0F, arm, player);
			poseStack.popPose();
		}
		poseStack.popPose();
		ci.cancel();
	}
}
