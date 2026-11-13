package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.LimboGhost;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws a player out of their body ({@link LimboGhost}) as the ghost at a grave is drawn in Kingdoms: a person's shape in
 * pale see-through blue with its own light, hanging off the ground and bobbing. Never the owner's own (they look out of
 * it). Its state is a plain humanoid's, not a player's, so nothing that redraws players takes it for one.
 */
public class LimboGhostRenderer extends MobRenderer<LimboGhost, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/limbo_ghost.png");
	/** As players are drawn; how far off the ground it hangs, how far it bobs, and how fast (blocks, radians a tick). */
	static final float SCALE = 0.9375F, HANG = 0.15F, BOB = 0.06F, BOB_SPEED = 0.08F;

	public LimboGhostRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.0F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public void extractRenderState(LimboGhost entity, HumanoidRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
	}

	@Override
	public boolean shouldRender(LimboGhost entity, Frustum frustum, double x, double y, double z, float partialTicks) {
		var player = Minecraft.getInstance().player;
		return (player == null || entity.owner() != player.getId()) && super.shouldRender(entity, frustum, x, y, z, partialTicks);
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	protected RenderType getRenderType(HumanoidRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
		return RenderTypes.entityTranslucentEmissive(TEXTURE);
	}

	@Override
	protected void scale(HumanoidRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
		// (the model hangs upside down here: up is -y)
		poseStack.translate(0.0F, -(HANG + BOB * Mth.sin(state.ageInTicks * BOB_SPEED)), 0.0F);
	}
}
