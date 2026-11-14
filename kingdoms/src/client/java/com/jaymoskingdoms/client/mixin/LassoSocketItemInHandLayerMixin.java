package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.forms.LassoSockets;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's held lasso in third person: where the top of its coil is drawn (LassoSockets). */
@Mixin(ItemInHandLayer.class)
abstract class LassoSocketItemInHandLayerMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void jaymoskingdoms$lassoSocket(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar && arm == avatar.mainArm) {
			LassoSockets.thirdPerson(avatar.id, stack, state.x, state.y, state.z);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void jaymoskingdoms$lassoSocketDone(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		LassoSockets.done();
	}
}
