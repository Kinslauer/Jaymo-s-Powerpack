package com.jaymospowers.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A living renderer's own scaling of its model (MCA's by a villager's genes), for McaPlayerHeads to undo. */
@Mixin(LivingEntityRenderer.class)
public interface LivingRendererScaleInvoker {
	@Invoker("scale")
	void jaymospowers$scale(LivingEntityRenderState state, PoseStack poseStack);
}
