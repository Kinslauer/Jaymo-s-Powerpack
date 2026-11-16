package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.PlayerGhost;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
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
 * the arms float loose at the sides, swaying, and the body leans into the way it drifts, more the faster it goes
 * ({@link Ghost#drift}). Andy (2026-11-16): "the ghosts should have wispy tails like wisp": in place of legs, one tapering
 * tail of {@link #TAIL} links hangs from the waist, curled back a little at each link as Wisp's is, wavering to the tip,
 * and streamed out behind as it drifts (the same tail as the ghost at a grave, on the same skin). Its owner sees it in third person too (never in first: they look
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
	/**
	 * The tail, waist to tip: each link's width, length and depth (model pixels) and where its box UV starts on the skin
	 * (the body's flat light for the wide links, then lower and lower down the leg's fading strip); each starts this far up
	 * inside the one above.
	 */
	static final int[][] TAIL = {{8, 3, 4, 16, 16}, {7, 3, 4, 16, 16}, {6, 3, 3, 16, 16}, {5, 3, 3, 16, 16}, {4, 3, 2, 0, 20}, {3, 3, 2, 0, 22},
		{2, 3, 1, 0, 26}, {1, 4, 1, 0, 27}};
	static final float TAIL_OVERLAP = 0.75F;
	/** Hanging: each link's bend back from the one above, its breath and rate (radians). At full drift: the top link's bend and each one's after. */
	static final float TAIL_CURL = 0.15F, TAIL_BREATHE = 0.05F, TAIL_BREATHE_RATE = 0.045F, TAIL_TRAIL_TOP = 1.0F, TAIL_TRAIL_LINK = 0.04F;
	/** The waver: side to side at the tip (radians), its rate, and how far it is out of step link to link. */
	static final float TAIL_WAVE = 0.2F, TAIL_WAVE_RATE = 0.16F, TAIL_WAVE_STEP = 0.8F;
	/** The arms: held a little forward and out, swaying slowly; swept back at full drift (radians). */
	static final float ARMS_FORWARD = -0.2F, ARMS_OUT = 0.18F, ARMS_SWAY = 0.1F, ARMS_SWEEP = 0.55F, SWAY_SPEED = 0.07F;

	/** A ghost's state: a humanoid's, and how fast it drifts (0 still to 1 full). */
	public static class Ghost extends HumanoidRenderState {
		public float drift;
	}

	public PlayerGhostRenderer(EntityRendererProvider.Context context) {
		super(context, new GhostModel(mesh()), 0.0F);
	}

	/** A player's shape with the tail hanging from the waist, each link under the one above. */
	static ModelPart mesh() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		PartDefinition parent = mesh.getRoot();
		float y = 12.0F;
		for (int i = 0; i < TAIL.length; i++) {
			int[] link = TAIL[i];
			parent = parent.addOrReplaceChild("tail" + i, CubeListBuilder.create().texOffs(link[3], link[4])
				.addBox(-link[0] / 2.0F, 0.0F, -link[2] / 2.0F, link[0], link[1], link[2]), PartPose.offset(0.0F, y, 0.0F));
			y = link[1] - TAIL_OVERLAP;
		}
		return LayerDefinition.create(mesh, 64, 64).bakeRoot();
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
		private final ModelPart[] tail = new ModelPart[TAIL.length];

		GhostModel(ModelPart root) {
			super(root);
			ModelPart link = root;
			for (int i = 0; i < TAIL.length; i++) {
				link = link.getChild("tail" + i);
				this.tail[i] = link;
			}
		}

		@Override
		public void setupAnim(Ghost state) {
			super.setupAnim(state);
			float t = state.ageInTicks * SWAY_SPEED;
			float drift = state.drift;
			// no legs: the tail hangs curled, breathing, and streams out behind the faster it drifts, wavering to the tip
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			float curl = TAIL_CURL + TAIL_BREATHE * Mth.sin(state.ageInTicks * TAIL_BREATHE_RATE);
			for (int i = 0; i < this.tail.length; i++) {
				float along = i / (float)(this.tail.length - 1);
				float sway = TAIL_WAVE * (0.3F + 0.7F * along) / (this.tail.length * 0.25F);
				float bend = Mth.lerp(drift, curl, i == 0 ? TAIL_TRAIL_TOP : TAIL_TRAIL_LINK);
				this.tail[i].xRot = bend + Mth.cos(state.ageInTicks * TAIL_WAVE_RATE * 0.7F - i * TAIL_WAVE_STEP) * sway * 0.5F;
				this.tail[i].zRot = Mth.sin(state.ageInTicks * TAIL_WAVE_RATE - i * TAIL_WAVE_STEP) * sway;
			}
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
