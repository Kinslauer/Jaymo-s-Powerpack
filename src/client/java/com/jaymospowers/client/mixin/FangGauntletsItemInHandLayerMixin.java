package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.FangGauntletsClient;
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

/** (0.18.75) Fang Gauntlets in the main hand aren't held in third person: they're worn (FangGauntletsClient.Layer). */
@Mixin(ItemInHandLayer.class)
abstract class FangGauntletsItemInHandLayerMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$gauntletsNotHeld(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar && arm == avatar.mainArm && FangGauntletsClient.worn(avatar) && FangGauntletsClient.isGauntlets(stack)) {
			ci.cancel();
		}
	}
}
