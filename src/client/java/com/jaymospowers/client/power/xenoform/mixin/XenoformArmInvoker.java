package com.jaymospowers.client.power.xenoform.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Draws one bare first-person hand and arm where the pose stack stands (for the slam of the band, see XenoformFirstPersonMixin). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
interface XenoformArmInvoker {
	@Invoker("renderPlayerHand")
	void jaymospowers$renderPlayerHand(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidArm arm, PlayerRenderState player);
}
