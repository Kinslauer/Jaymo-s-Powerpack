package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.LashSockets;
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

/** (0.16.8) A player's main-hand flail or whip in third person: where its handle's tip is drawn (LashSockets). */
@Mixin(ItemInHandLayer.class)
abstract class LashSocketItemInHandLayerMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void jaymospowers$lashSocket(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar && arm == avatar.mainArm) {
			LashSockets.thirdPerson(avatar.id, stack, state.x, state.y, state.z);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void jaymospowers$lashSocketDone(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		LashSockets.done();
	}
}
