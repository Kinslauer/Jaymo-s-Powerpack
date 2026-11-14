package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.PlayerGhost;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Draws a player out of their body ({@link PlayerGhost}) as the ghost at a grave is drawn in Kingdoms: a person's shape in
 * pale see-through blue with its own light, hanging off the ground and bobbing. Its state is a plain humanoid's, not a
 * player's, so nothing that redraws players takes it for one.
 * <p>
 * Andy (2026-11-14): "3rd person as a limbo ghost didnt animate properly". It drifts as a ghost does, not walking on air:
 * the legs hang together and trail behind, the arms float loose at the sides, swaying, and the body leans into the way it
 * drifts, more the faster it goes ({@link Ghost#drift}). Its owner sees it in third person too (never in first: they look
 * out of it), drawn where their own view is this frame (the ghost they steer, {@link OutOfBodyClient#camera}, or in Limbo
 * their own place), so it keeps up with the camera instead of lagging a round trip behind; the floating head vanilla
 * draws for a spectator is hidden in Limbo ({@code LimboSelfHiddenMixin}).
 */
public class PlayerGhostRenderer extends MobRenderer<PlayerGhost, PlayerGhostRenderer.Ghost, HumanoidModel<PlayerGhostRenderer.Ghost>> {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/player_ghost.png");
	/** As players are drawn; how far off the ground it hangs, how far it bobs, and how fast (blocks, radians a tick). */
	static final float SCALE = 0.9375F, HANG = 0.15F, BOB = 0.06F, BOB_SPEED = 0.08F;
	/** The drift speed (blocks a tick) taken as full drift, and how far it leans into it then (degrees). */
	static final float FULL_DRIFT = 0.2F, LEAN_DEGREES = 22.0F;
	/** The legs: how far they trail behind at rest and at full drift, and their slow sway (radians). */
	static final float LEGS_REST = 0.12F, LEGS_TRAIL = 0.55F, LEGS_SWAY = 0.06F;
	/** The arms: held a little forward and out, swaying slowly; swept back at full drift (radians). */
	static final float ARMS_FORWARD = -0.2F, ARMS_OUT = 0.18F, ARMS_SWAY = 0.1F, ARMS_SWEEP = 0.55F, SWAY_SPEED = 0.07F;

	/** A ghost's state: a humanoid's, and how fast it drifts (0 still to 1 full). */
	public static class Ghost extends HumanoidRenderState {
		public float drift;
	}

	public PlayerGhostRenderer(EntityRendererProvider.Context context) {
		super(context, new GhostModel(context.bakeLayer(ModelLayers.PLAYER)), 0.0F);
	}

	@Override
	public Ghost createRenderState() {
		return new Ghost();
	}

	@Override
	public void extractRenderState(PlayerGhost entity, Ghost state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		// never a walk in the air: the drift is drawn instead
		state.walkAnimationPos = 0.0F;
		state.walkAnimationSpeed = 0.0F;
		LocalPlayer me = Minecraft.getInstance().player;
		Entity moving = entity;
		if (me != null && entity.owner() == me.getId()) {
			// (its owner's own: where their view is now, out of their body or waiting in Limbo)
			Entity camera = OutOfBodyClient.camera();
			moving = camera != null ? camera : me;
			state.x = Mth.lerp(partialTicks, moving.xo, moving.getX());
			state.y = Mth.lerp(partialTicks, moving.yo, moving.getY());
			state.z = Mth.lerp(partialTicks, moving.zo, moving.getZ());
			state.bodyRot = me.getViewYRot(partialTicks);
			state.yRot = 0.0F;
			state.xRot = me.getViewXRot(partialTicks);
		}
		float speed = (float)Math.sqrt(Mth.lengthSquared(moving.getX() - moving.xo, moving.getY() - moving.yo, moving.getZ() - moving.zo));
		state.drift = Mth.clamp(speed / FULL_DRIFT, 0.0F, 1.0F);
	}

	@Override
	public boolean shouldRender(PlayerGhost entity, Frustum frustum, double x, double y, double z, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player != null && entity.owner() == player.getId()) {
			// their own: only from outside it (third person); it is drawn where they are, so nearness to the camera is given
			return !minecraft.options.getCameraType().isFirstPerson();
		}
		return super.shouldRender(entity, frustum, x, y, z, partialTicks);
	}

	@Override
	public Identifier getTextureLocation(Ghost state) {
		return TEXTURE;
	}

	@Override
	protected RenderType getRenderType(Ghost state, boolean bodyVisible, boolean translucent, boolean glowing) {
		return RenderTypes.entityTranslucentEmissive(TEXTURE);
	}

	@Override
	protected void setupRotations(Ghost state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.drift > 0.0F) {
			// leaning into the drift, about the middle of the body
			poseStack.translate(0.0F, 0.9F * entityScale, 0.0F);
			poseStack.rotateDegrees(Axis.XP, -LEAN_DEGREES * state.drift);
			poseStack.translate(0.0F, -0.9F * entityScale, 0.0F);
		}
	}

	@Override
	protected void scale(Ghost state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
		// (the model hangs upside down here: up is -y)
		poseStack.translate(0.0F, -(HANG + BOB * Mth.sin(state.ageInTicks * BOB_SPEED)), 0.0F);
	}

	/** The player's shape, posed as a ghost drifting rather than a body walking. */
	static final class GhostModel extends HumanoidModel<Ghost> {
		GhostModel(ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(Ghost state) {
			super.setupAnim(state);
			float t = state.ageInTicks * SWAY_SPEED;
			float drift = state.drift;
			// the legs hang together, trailing back the faster it drifts, swaying a little
			float legs = Mth.lerp(drift, LEGS_REST, LEGS_TRAIL);
			this.rightLeg.xRot = legs + LEGS_SWAY * Mth.sin(t);
			this.leftLeg.xRot = legs + LEGS_SWAY * Mth.sin(t + 0.6F);
			this.rightLeg.yRot = 0.0F;
			this.leftLeg.yRot = 0.0F;
			this.rightLeg.zRot = 0.03F;
			this.leftLeg.zRot = -0.03F;
			// the arms float loose a little forward and out, swept back as it goes
			float arms = Mth.lerp(drift, ARMS_FORWARD, ARMS_SWEEP);
			this.rightArm.xRot = arms + ARMS_SWAY * Mth.sin(t + 1.3F);
			this.leftArm.xRot = arms + ARMS_SWAY * Mth.sin(t + 2.1F);
			this.rightArm.yRot = 0.0F;
			this.leftArm.yRot = 0.0F;
			this.rightArm.zRot = ARMS_OUT + 0.05F * Mth.sin(t * 0.7F);
			this.leftArm.zRot = -ARMS_OUT - 0.05F * Mth.sin(t * 0.7F + 0.9F);
		}
	}
}
