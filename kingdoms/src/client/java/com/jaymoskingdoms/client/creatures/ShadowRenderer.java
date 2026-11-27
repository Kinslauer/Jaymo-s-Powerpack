package com.jaymoskingdoms.client.creatures;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.creatures.Shadow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Draws a {@link Shadow} as the ghosts are drawn (Powers' PlayerGhostRenderer, the grave ghost): a person's shape in a
 * see-through skin with its own faint light, but the skin is a shadow's cold near-black ({@code tools/gen_shadow_skin.py}).
 * Andy (2026-11-26): "shadows should be transparent, like our ghost texture, they should also be varying sizes and some
 * should have the wispy tail instead of legs. they should also disappear or go invisible between attacks". Its size is its
 * own (its scale, drawn by the renderer as every body's is). A wispy one has no legs: the ghost's tapering tail hangs from
 * its waist instead, curled and wavering, streaming out behind as it goes, and it hangs a little off the ground, bobbing,
 * its arms loose. One with legs walks as a man walks. Between strikes it thins away to nothing and gathers again
 * ({@link Shadow#shown}), so it fades rather than blinks.
 */
public class ShadowRenderer extends MobRenderer<Shadow, ShadowRenderer.State, ShadowRenderer.ShadowModel> {
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/creatures/shadow.png");
	/** As players are drawn; how far a wispy one hangs off the ground, how far it bobs, and how fast (blocks, radians a tick). */
	static final float SCALE = 0.9375F, HANG = 0.15F, BOB = 0.06F, BOB_SPEED = 0.08F;
	/** Below this much showing it is not drawn at all. */
	static final float UNSEEN = 0.02F;
	/** The speed (blocks a tick) taken as full drift for a wispy one, and how far it leans into it then (degrees). */
	static final float FULL_DRIFT = 0.2F, LEAN_DEGREES = 18.0F;
	/** The tail, waist to tip, as the ghosts' (width, length, depth, box UV), each link starting this far up inside the one above. */
	static final int[][] TAIL = {{8, 3, 4, 16, 16}, {7, 3, 4, 16, 16}, {6, 3, 3, 16, 16}, {5, 3, 3, 16, 16}, {4, 3, 2, 0, 20}, {3, 3, 2, 0, 22},
		{2, 3, 1, 0, 26}, {1, 4, 1, 0, 27}};
	static final float TAIL_OVERLAP = 0.75F;
	/** The tail hanging: each link's bend back, its breath and rate; at full drift the top link's bend and each one's after (radians). */
	static final float TAIL_CURL = 0.15F, TAIL_BREATHE = 0.05F, TAIL_BREATHE_RATE = 0.045F, TAIL_TRAIL_TOP = 1.0F, TAIL_TRAIL_LINK = 0.04F;
	/** The tail's waver: side to side at the tip (radians), its rate, and how far it is out of step link to link. */
	static final float TAIL_WAVE = 0.2F, TAIL_WAVE_RATE = 0.16F, TAIL_WAVE_STEP = 0.8F;
	/** A wispy one's arms: held a little forward and out, swaying slowly; swept back at full drift (radians). */
	static final float ARMS_FORWARD = -0.2F, ARMS_OUT = 0.18F, ARMS_SWAY = 0.1F, ARMS_SWEEP = 0.55F, SWAY_SPEED = 0.07F;

	/** A shadow's state: a humanoid's, whether it is wispy, how fast it drifts (0 to 1), and how much of it shows (0 to 1). */
	public static class State extends HumanoidRenderState {
		boolean wispy;
		float drift, shown;
	}

	public ShadowRenderer(EntityRendererProvider.Context context) {
		super(context, new ShadowModel(mesh()), 0.0F);
	}

	/** A player's shape with the tail hanging from the waist too, each link under the one above (shown only on a wispy one). */
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
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Shadow entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.wispy = entity.wispy();
		state.shown = entity.shown(partialTicks);
		if (state.wispy) {
			// it drifts, never walks on air
			state.walkAnimationPos = 0.0F;
			state.walkAnimationSpeed = 0.0F;
			float speed = (float)Math.sqrt(Mth.lengthSquared(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo));
			state.drift = Mth.clamp(speed / FULL_DRIFT, 0.0F, 1.0F);
		} else {
			state.drift = 0.0F;
		}
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	@Override
	protected boolean isBodyVisible(State state) {
		// (gone, it is invisible; how much it shows is drawn by its fade instead)
		return true;
	}

	@Override
	protected @Nullable RenderType getRenderType(State state, boolean bodyVisible, boolean translucent, boolean glowing) {
		return state.shown < UNSEEN ? null : RenderTypes.entityTranslucentEmissive(TEXTURE);
	}

	@Override
	protected int getModelTint(State state) {
		return ARGB.white(state.shown);
	}

	@Override
	protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.drift > 0.0F) {
			// a wispy one leans into its drift, about the middle of the body
			poseStack.translate(0.0F, 0.9F * entityScale, 0.0F);
			poseStack.rotateDegrees(Axis.XP, -LEAN_DEGREES * state.drift);
			poseStack.translate(0.0F, -0.9F * entityScale, 0.0F);
		}
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
		if (state.wispy) {
			// (the model hangs upside down here: up is -y)
			poseStack.translate(0.0F, -(HANG + BOB * Mth.sin(state.ageInTicks * BOB_SPEED)), 0.0F);
		}
	}

	/** The player's shape, with legs, or with the ghost's tail in their place and arms that float loose. */
	static final class ShadowModel extends HumanoidModel<State> {
		private final ModelPart[] tail = new ModelPart[TAIL.length];

		ShadowModel(ModelPart root) {
			super(root);
			ModelPart link = root;
			for (int i = 0; i < TAIL.length; i++) {
				link = link.getChild("tail" + i);
				this.tail[i] = link;
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			boolean wispy = state.wispy;
			this.rightLeg.visible = !wispy;
			this.leftLeg.visible = !wispy;
			for (ModelPart link : this.tail) {
				link.visible = wispy;
			}
			if (!wispy) {
				return;
			}
			float drift = state.drift;
			float curl = TAIL_CURL + TAIL_BREATHE * Mth.sin(state.ageInTicks * TAIL_BREATHE_RATE);
			for (int i = 0; i < this.tail.length; i++) {
				float along = i / (float)(this.tail.length - 1);
				float sway = TAIL_WAVE * (0.3F + 0.7F * along) / (this.tail.length * 0.25F);
				float bend = Mth.lerp(drift, curl, i == 0 ? TAIL_TRAIL_TOP : TAIL_TRAIL_LINK);
				this.tail[i].xRot = bend + Mth.cos(state.ageInTicks * TAIL_WAVE_RATE * 0.7F - i * TAIL_WAVE_STEP) * sway * 0.5F;
				this.tail[i].zRot = Mth.sin(state.ageInTicks * TAIL_WAVE_RATE - i * TAIL_WAVE_STEP) * sway;
			}
			if (state.swingAnimation > 0.0F) {
				// (its reach for a touch is the humanoid swing, left as it is)
				return;
			}
			float t = state.ageInTicks * SWAY_SPEED;
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
