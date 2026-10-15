package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The big body, remade far more hulking ({@link WebHulkMesh}, {@code tools/gen_web_hulk.py}): massive traps, shoulders
 * and lats, Popeye forearms, long clawed hands hanging to the knees, thick legs, and a small head sunk low and forward
 * between the shoulders, with big angular eyes, a heavy muzzle and a hinged jaw lined with fangs. The jaw hangs open
 * and breathes, now and then drawing back in a snarl; a long red tongue lolls out over it and lashes on its own
 * ({@link #submitTongue}). Everything keeps the player model's pivots, so every pose drives it; on top of them the
 * arms sit wider, the legs further apart and the head lower and further forward ({@link #shift}), over every model
 * drawn for that body (the wearer's own, the suits, armour), so they all line up.
 */
final class WebHulk {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_hulk.png");
	/** How far the arms sit out, the legs apart, and the head sinks and juts forward on the big body (pixels). */
	static final float ARM_OUT = 3.5F, LEG_OUT = 1.2F, HEAD_DOWN = 2.5F, HEAD_FORWARD = 1.5F;
	/** The jaw: how far it hangs open at rest (radians), how much it breathes, and a snarl's gape. */
	static final float JAW_REST = 0.22F, JAW_BREATH = 0.08F, JAW_SNARL = 0.5F;
	/** The tongue: pieces, each one's length (pixels), its width and thickness at the root (tapering to the tip). */
	private static final int TONGUE_PIECES = 12;
	private static final float TONGUE_PIECE = 1.7F, TONGUE_WIDTH = 1.6F, TONGUE_THICK = 0.55F;
	/** How it lolls: drooping this much more each piece, rippling this hard, and lashing out every so often. */
	private static final float TONGUE_DROOP = 0.1F, TONGUE_RIPPLE = 0.28F, TONGUE_SWAY = 0.22F;
	/** A lash comes round every {@code LASH_EVERY} ticks or so and lasts {@code LASH_TICKS}. */
	private static final float LASH_EVERY = 70.0F, LASH_TICKS = 14.0F;

	/** The big body's model: the player model's parts, each muscle a child part (grown on one at a time). */
	static final class Model extends PlayerModel {
		final ModelPart[] muscles;
		final int[] order;
		final ModelPart jaw;

		Model() {
			super(WebHulkMesh.create().bakeRoot(), false);
			ModelPart root = this.root();
			this.muscles = new ModelPart[WebHulkMesh.PARTS.length];
			this.order = WebHulkMesh.ORDER;
			for (int i = 0; i < this.muscles.length; i++) {
				this.muscles[i] = root.getChild(WebHulkMesh.PARTS[i][0]).getChild(WebHulkMesh.PARTS[i][1]);
			}
			this.jaw = this.head.getChild("jaw");
		}

		/** {@code g}: how far it's been built (0 nothing, 1 all of it); each muscle swells on in its turn. */
		void build(float g) {
			for (int i = 0; i < this.muscles.length; i++) {
				float at = Mth.clamp((g - BUILD_FROM - BUILD_SPAN * this.order[i] / (float)WebHulkMesh.LAST_ORDER) / BUILD_EACH, 0.0F, 1.0F);
				float size = at >= 1.0F ? 1.0F : swell(at);
				ModelPart part = this.muscles[i];
				part.xScale = size;
				part.yScale = size;
				part.zScale = size;
				part.visible = size > 0.02F;
			}
		}

		/** The jaw hangs open, breathing, and draws back in a snarl now and then. */
		void breathe(float age) {
			this.jaw.xRot = jaw(age);
		}
	}

	/** When the build starts (share of the change), how long it takes to reach the last muscle, and how long each one takes. */
	private static final float BUILD_FROM = 0.3F, BUILD_SPAN = 0.52F, BUILD_EACH = 0.16F;

	private WebHulk() {
	}

	/** 0 → 1, overshooting first: a muscle slapped on and settling. */
	private static float swell(float g) {
		float eased = 1.0F - (1.0F - g) * (1.0F - g);
		return eased + Mth.sin(g * Mth.PI) * 0.18F;
	}

	static float jaw(float age) {
		float breath = Mth.sin(age * 0.11F) * JAW_BREATH;
		float snarl = (float)Math.pow(Math.max(0.0F, Mth.sin(age * 0.045F)), 16.0) * JAW_SNARL;
		return JAW_REST + breath + snarl;
	}

	/** What the big body's pieces were last moved to (so moving them twice in one frame moves them once). */
	private static final Map<ModelPart, float[]> MOVED = new WeakHashMap<>();

	/** Spreads a model out to the big body's frame, {@code k} of the way (0 none). Safe to call more than once a frame. */
	static void shift(HumanoidModel<?> h, float k) {
		if (k <= 0.0F) {
			return;
		}
		move(h.rightArm, -ARM_OUT * k, 0.0F, 0.0F);
		move(h.leftArm, ARM_OUT * k, 0.0F, 0.0F);
		move(h.rightLeg, -LEG_OUT * k, 0.0F, 0.0F);
		move(h.leftLeg, LEG_OUT * k, 0.0F, 0.0F);
		move(h.head, 0.0F, HEAD_DOWN * k, -HEAD_FORWARD * k);
	}

	private static void move(ModelPart part, float dx, float dy, float dz) {
		float[] was = MOVED.get(part);
		if (was != null && was[0] == part.x && was[1] == part.y && was[2] == part.z) {
			return;
		}
		part.x += dx;
		part.y += dy;
		part.z += dz;
		MOVED.put(part, new float[] {part.x, part.y, part.z});
	}

	/**
	 * The tongue, lolling out of the open jaw over the fangs and down, rippling and swaying, and every few seconds lashing:
	 * curled back up, then whipped out and down. Drawn off the head ({@code head}, already posed and moved to the big
	 * body's frame), {@code grown} of its length.
	 */
	static void submitTongue(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, PlayerModel parent, float grown) {
		if (grown <= 0.02F) {
			return;
		}
		float age = state.ageInTicks;
		// which lash this is, and how far into it (a little irregular: each player's own rhythm)
		float cycle = (age + state.id * 37.0F) % LASH_EVERY;
		float lash = cycle < LASH_TICKS ? Mth.sin(cycle / LASH_TICKS * Mth.PI) : 0.0F;
		float curl = cycle < LASH_TICKS * 0.4F ? Mth.sin(cycle / (LASH_TICKS * 0.4F) * Mth.PI) : 0.0F;
		float jawAngle = jaw(age);
		poseStack.pushPose();
		parent.root().translateAndRotate(poseStack);
		parent.head.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) -> {
			// out from between the jaws, riding down with the lower one
			Vec3 at = new Vec3(0.0, 0.9 + Mth.sin(jawAngle) * 2.0, -3.0);
			float pitch = 0.15F + jawAngle * 0.6F, yaw = 0.0F;
			int pieces = Math.max(1, Math.round(TONGUE_PIECES * grown));
			for (int i = 0; i < pieces; i++) {
				float f = i / (float)TONGUE_PIECES;
				// droops more toward the tip, a ripple running down it, a lazy sway; a lash curls it up then flings it down
				pitch += TONGUE_DROOP + Mth.sin(age * 0.23F - i * 0.6F) * TONGUE_RIPPLE * f - curl * 0.42F + lash * 0.18F * f;
				yaw = Mth.sin(age * 0.13F - i * 0.45F) * TONGUE_SWAY * (0.3F + f) + lash * Mth.sin(age * 0.9F) * 0.3F * f;
				// model space: -Z ahead, +Y down
				Vec3 dir = new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), -Mth.cos(yaw) * Mth.cos(pitch));
				Vec3 next = at.add(dir.scale(TONGUE_PIECE * (1.0F + lash * 0.25F)));
				float w0 = TONGUE_WIDTH * (1.0F - 0.55F * f), w1 = TONGUE_WIDTH * (1.0F - 0.55F * (i + 1) / TONGUE_PIECES);
				float t0 = TONGUE_THICK * (1.0F - 0.4F * f), t1 = TONGUE_THICK * (1.0F - 0.4F * (i + 1) / TONGUE_PIECES);
				if (i == TONGUE_PIECES - 1) {
					// a pointed tip
					w1 = 0.15F;
					t1 = 0.1F;
				}
				WebTubes.prism(buffer, pose, at.scale(1.0 / 16.0), next.scale(1.0 / 16.0), new Vec3(1.0, 0.0, 0.0), w0 / 16.0, t0 / 16.0, w1 / 16.0,
					t1 / 16.0, WebTubes.RED, state.lightCoords);
				at = next;
			}
		});
		poseStack.popPose();
	}
}
