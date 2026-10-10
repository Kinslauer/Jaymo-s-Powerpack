package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CarryPose;
import com.jaymospowers.core.Carry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carrying, in first person: your hands are full. The bare arms take the hold instead of whatever's in them. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class CarryFirstPersonMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$carryingArms(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		Player self = this.minecraft.player;
		Carry.Carrying carrying = self == null ? null : self.getAttached(Carry.CARRYING);
		if (carrying == null || player.avatarRenderState == null || hands.isScoping) {
			return;
		}
		boolean animal = !(self.level().getEntity(carrying.target()) instanceof Player);
		poseStack.pushPose();
		// the same lag behind the view as the usual hands
		poseStack.rotateDegrees(Axis.XP, (hands.viewXRot - hands.xBob) * 0.1F);
		poseStack.rotateDegrees(Axis.YP, (hands.viewYRot - hands.yBob) * 0.1F);
		int light = player.avatarRenderState.lightCoords;
		for (HumanoidArm arm : HumanoidArm.values()) {
			poseStack.pushPose();
			if (CarryPose.firstPersonArm(poseStack, carrying.hold(), animal, arm == HumanoidArm.RIGHT)) {
				((CarryArmInvoker)this).jaymospowers$renderPlayerArm(poseStack, collector, light, CarryPose.FIRST_PERSON_EQUIP, 0.0F, arm, player);
			}
			poseStack.popPose();
		}
		poseStack.popPose();
		ci.cancel();
	}
}
