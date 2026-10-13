package com.jaymospowers.client.power.xenoform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Draws one bare first-person arm (for the slam of the band, see XenoformFirstPersonMixin). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
interface XenoformArmInvoker {
	@Invoker("renderPlayerArm")
	void jaymospowers$renderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, float inverseArmHeight,
		float attackValue, HumanoidArm arm, PlayerRenderState player);
}
