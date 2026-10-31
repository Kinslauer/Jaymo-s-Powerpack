package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.XenoformClient;
import com.jaymospowers.client.power.xenoform.XenoformSlamView;
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
 * <li>the slam of the band (see XenoformSlap, and XenoformSlamView for the placements): the left forearm swings up across
 * the view with the band's face turned up at you, the right hand hovers above it and comes down on the dial. While it
 * plays, the usual hands are replaced. (0.18.90) An alien has no band: only its right hand comes up, tapping the
 * insignia on its chest. (0.18.88) With First-person Model drawing your body these hands are drawn too,
 * and the body's own arms are hidden meanwhile (XenoformLivingEntityRendererMixin): posed on the body, the raised
 * forearm came up through the camera and the band never showed (Andy).</li>
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
		if (pose == null) {
			return;
		}
		// the impact is felt at the landing whether or not the hands are drawn
		XenoformSlap.landed(pose, self);
		if (hands.isScoping || self.getAttached(Carry.CARRYING) != null) {
			return;
		}
		float time = self.tickCount + partialTicks;
		poseStack.pushPose();
		// the same lag behind the view as the usual hands
		poseStack.rotateDegrees(Axis.XP, (hands.viewXRot - hands.xBob) * 0.1F);
		poseStack.rotateDegrees(Axis.YP, (hands.viewYRot - hands.yBob) * 0.1F);
		int light = avatar.lightCoords;
		XenoformArmInvoker arms = (XenoformArmInvoker)this;
		if (pose.chest()) {
			// (0.18.90) an alien wears no band: only the right hand comes up, to tap the insignia on its chest
			poseStack.pushPose();
			XenoformSlamView.chest(poseStack, pose, time);
			arms.jaymospowers$renderPlayerHand(poseStack, collector, light, HumanoidArm.RIGHT, player);
			poseStack.popPose();
		} else {
			// the band's wrist: up from below into a bar across the lower view, the band's face turned up at you
			// (the band goes on with the arm: XenoformAvatarRendererMixin, after renderHand, with the dial's cap)
			poseStack.pushPose();
			XenoformSlamView.dial(pose, time);
			XenoformSlamView.left(poseStack, pose, time);
			arms.jaymospowers$renderPlayerHand(poseStack, collector, light, HumanoidArm.LEFT, player);
			XenoformSlamView.dialDone();
			poseStack.popPose();
			// the striking hand: poised above and to the right, then down flat on the dial
			poseStack.pushPose();
			XenoformSlamView.right(poseStack, pose, time);
			arms.jaymospowers$renderPlayerHand(poseStack, collector, light, HumanoidArm.RIGHT, player);
			poseStack.popPose();
		}
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
