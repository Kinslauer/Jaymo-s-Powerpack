package com.jaymospowers.client.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.Hoverboard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The hoverboard: the bomb glider's model ({@link BombGliderModel#body()}, no wings) in a future skin
 * (textures/entity/hoverboard.png, from tools/gen_hoverboard.py), its hull tinted by its dye and its emitters drawn
 * full bright on a second pass (hoverboard_glow.png) that the dye leaves alone. It tilts into its turns and bobs a little
 * while it hovers.
 */
public class HoverboardRenderer extends EntityRenderer<Hoverboard, HoverboardRenderer.State> {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/hoverboard.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/hoverboard_glow.png");
	/** The hover bob: how far (blocks) and how fast (radians a tick). */
	static final float BOB = 0.03F, BOB_RATE = 0.2F;

	public static class State extends EntityRenderState {
		float yaw;
		float roll;
		int hull;
		boolean hovering;
	}

	public HoverboardRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.6F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Hoverboard entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.roll = Mth.lerp(partialTicks, entity.rollO, entity.roll);
		state.hull = entity.hull();
		state.hovering = entity.hovering();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0F, (float)Hoverboard.DECK + (state.hovering ? Mth.sin(state.ageInTicks * BOB_RATE) * BOB : 0.0F), 0.0F);
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		poseStack.rotateDegrees(Axis.ZP, state.roll);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		int tint = state.hull == Hoverboard.DEFAULT_HULL ? 0xFFFFFFFF : 0xFF000000 | state.hull;
		collector.submitModelPart(BombGliderModel.body(), poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, null, tint);
		collector.submitModelPart(BombGliderModel.body(), poseStack, RenderTypes.eyes(GLOW), state.lightCoords, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
