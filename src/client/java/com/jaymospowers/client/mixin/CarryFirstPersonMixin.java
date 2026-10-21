package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CarryPose;
import com.jaymospowers.client.FirstPersonArms;
import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.core.Carry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carrying, in first person: your hands are full. The bare arms take the hold instead of whatever's in them. (0.15.42)
 * They rise in under the load as what the hands held sinks away (PowerArmsFirstPersonMixin), bob with your steps, and
 * sink out again as it comes back when you let go (FirstPersonArms).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class CarryFirstPersonMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$carryingArms(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		LocalPlayer self = this.minecraft.player;
		if (self == null || player.avatarRenderState == null || hands.isScoping) {
			FirstPersonArms.noCarry();
			return;
		}
		Carry.Carrying carrying = self.getAttached(Carry.CARRYING);
		PoseBlend.Track track = FirstPersonArms.carry(self, carrying);
		if (track == null) {
			return;
		}
		if (com.jaymospowers.client.power.web.FirstPersonCompat.bodyShown()) {
			// First-person Model draws your body, its arms already holding on: no second pair
			FirstPersonArms.noCarry();
			if (carrying != null) {
				ci.cancel();
			}
			return;
		}
		FirstPersonArms.CarryHold hold = (FirstPersonArms.CarryHold)track.memo;
		float weight = track.amount();
		poseStack.pushPose();
		// the same lag behind the view as the usual hands
		poseStack.rotateDegrees(Axis.XP, (hands.viewXRot - hands.xBob) * 0.1F);
		poseStack.rotateDegrees(Axis.YP, (hands.viewYRot - hands.yBob) * 0.1F);
		int light = player.avatarRenderState.lightCoords;
		for (HumanoidArm arm : HumanoidArm.values()) {
			poseStack.pushPose();
			FirstPersonArms.carryArm(poseStack, player.avatarRenderState, weight, arm == HumanoidArm.RIGHT);
			if (CarryPose.firstPersonArm(poseStack, hold.hold(), hold.animal(), arm == HumanoidArm.RIGHT)) {
				((CarryArmInvoker)this).jaymospowers$renderPlayerArm(poseStack, collector, light, CarryPose.FIRST_PERSON_EQUIP, 0.0F, arm, player);
			}
			poseStack.popPose();
		}
		poseStack.popPose();
		if (carrying != null && weight >= FirstPersonArms.CARRY_HANDOFF) {
			// all the way in: the held items are sunk out of sight, so they're not drawn
			ci.cancel();
		}
	}
}
