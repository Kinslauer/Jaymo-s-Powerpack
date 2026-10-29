package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.GoblinBody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.320) What a goblin holds sits in its fist, not stretched by its thin arm's scale (GoblinBody#hand). */
@Mixin(HumanoidModel.class)
abstract class GoblinHandMixin {
	@Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$goblinHand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
		GoblinBody.hand((HumanoidModel<?>) (Object) this, state, arm, poseStack);
	}
}
