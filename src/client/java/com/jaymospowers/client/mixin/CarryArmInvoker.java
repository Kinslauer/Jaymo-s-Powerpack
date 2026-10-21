package com.jaymospowers.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Draws one bare first-person arm (for the carrying hold, see CarryFirstPersonMixin, and the moves' arms, see
 * PowerArmsFirstPersonMixin); and (0.15.42) one hand again at another equip height (PowerArmsFirstPersonMixin).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
interface CarryArmInvoker {
	@Invoker("renderPlayerArm")
	void jaymospowers$renderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, float inverseArmHeight,
		float attackValue, HumanoidArm arm, PlayerRenderState player);

	@Invoker("submitArmWithItem")
	void jaymospowers$submitArmWithItem(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector,
		int lightCoords);
}
