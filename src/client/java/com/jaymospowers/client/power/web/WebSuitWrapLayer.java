package com.jaymospowers.client.power.web;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The first half of putting the suit on (see WebSuitGrowth): the symbiote's tendrils leech out of the chest and wrap
 * the body, coiling round the torso, then down the arms, up round the head and down the legs, each from its joint out,
 * writhing as they go. Then, as the suit folds over each part along them, they sink into it, and the suit's leading edge
 * rolls down the part as a thick band of tendril. Building the big body, thicker tendrils lash round the body's new,
 * bigger frame first. Everyone sees it; taking the suit off runs it backward. When the tendrils themselves draw back in
 * (Wall Crawl or Living Armor ending), they go the same way: over the body, then back into the chest.
 */
final class WebSuitWrapLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** Strands round each part, and how many times each coils round it from end to end. */
	private static final int STRANDS = 3;
	private static final float TURNS = 1.6F;
	/** Pieces per strand, and how far out from the part they coil (pixels). */
	private static final int PIECES = 14;
	private static final float OUT = 0.5F;
	/** A strand's thickness (pixels): the suit's, the big body's; the rolling edge's. */
	private static final float THICK = 0.55F, HULK_THICK = 1.1F, EDGE_THICK = 0.8F;

	/**
	 * Each part's box in its own space (pixels, from its pivot; the long axis is y, from {@code y0} at the joint to
	 * {@code y1}): body, right arm, left arm, head, right leg, left leg. Then the big body's.
	 */
	private static final float[][] BOXES = {
		{-4.0F, 0.0F, -2.0F, 4.0F, 12.0F, 2.0F},
		{-3.0F, -2.0F, -2.0F, 1.0F, 10.0F, 2.0F},
		{-1.0F, -2.0F, -2.0F, 3.0F, 10.0F, 2.0F},
		{-4.0F, 0.0F, -4.0F, 4.0F, -8.0F, 4.0F},
		{-2.0F, 0.0F, -2.0F, 2.0F, 12.0F, 2.0F},
		{-2.0F, 0.0F, -2.0F, 2.0F, 12.0F, 2.0F},
	};
	private static final float[][] HULK_BOXES = {
		{-8.0F, -3.0F, -4.5F, 8.0F, 13.0F, 5.0F},
		{-5.5F, -4.0F, -3.5F, 1.5F, 17.0F, 3.5F},
		{-1.5F, -4.0F, -3.5F, 5.5F, 17.0F, 3.5F},
		{-4.5F, 2.5F, -5.5F, 4.5F, -8.0F, 4.5F},
		{-3.0F, -1.0F, -5.0F, 3.0F, 12.0F, 3.0F},
		{-3.0F, -1.0F, -5.0F, 3.0F, 12.0F, 3.0F},
	};

	WebSuitWrapLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		WebSuitGrowth.Growth growth = state.getData(WebSuitGrowth.KEY);
		Float pull = state.getData(WebSuitGrowth.PULL);
		if (growth == null && pull != null) {
			struggle(poseStack, collector, state, pull);
			return;
		}
		Float retract = state.getData(WebSymbioteClient.RETRACT);
		if (growth == null && retract != null) {
			retract(poseStack, collector, state, retract);
			return;
		}
		if (growth == null) {
			return;
		}
		boolean hulk = Math.min(growth.from(), growth.to()) >= 1;
		float built = WebSuitGrowth.built(growth);
		PlayerModel parent = this.getParentModel();
		WebSuitGrowth.shift(parent, state);
		ModelPart[] parts = {parent.body, parent.rightArm, parent.leftArm, parent.head, parent.rightLeg, parent.leftLeg};
		float time = state.ageInTicks;
		boolean ownView = WebSymbioteClient.ownFirstPerson(state);
		for (int i = 0; i < parts.length; i++) {
			if (ownView && i == 3) {
				continue;
			}
			float wrap = WebSuitGrowth.wrapped(built, i);
			// they sink into the suit as it folds over them (or into the muscle laid on, building the big body)
			float sunk = hulk ? Mth.clamp((built - 0.55F - 0.08F * i) / 0.3F, 0.0F, 1.0F) : WebSuitGrowth.layered(built, i, true);
			float edge = hulk ? 0.0F : WebSuitGrowth.layered(built, i, true);
			if (wrap <= 0.0F || sunk >= 1.0F && (edge <= 0.0F || edge >= 1.0F)) {
				continue;
			}
			float[] box = hulk ? HULK_BOXES[i] : BOXES[i];
			float thick = (hulk ? HULK_THICK : THICK) * (1.0F - sunk);
			int part = i;
			poseStack.pushPose();
			parent.root().translateAndRotate(poseStack);
			parts[i].translateAndRotate(poseStack);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) -> {
				if (thick > 0.05F) {
					for (int k = 0; k < STRANDS * (hulk ? 2 : 1); k++) {
						strand(buffer, pose, box, part, k, wrap, thick, time, hulk, state.lightCoords);
					}
				}
				if (edge > 0.0F && edge < 1.0F) {
					ring(buffer, pose, box, edge, time, state.lightCoords);
				}
			});
			poseStack.popPose();
		}
	}

	/**
	 * The tendrils drawing back in, the suit's way: as they shrink into the back they pour over the body, coiling round
	 * each part from its joint out (the body first, the legs last), then unwind back the way they came and sink into the
	 * chest. {@code RETRACT_WRAP} of the way through they're all wrapped.
	 */
	private static final float RETRACT_WRAP = 0.45F, RETRACT_THICK = 0.7F;

	private void retract(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, float t) {
		// how far the wrap has got (as putting the suit on reads it): up to all of it, then back to nothing
		float built = t < RETRACT_WRAP ? t / RETRACT_WRAP * 0.55F : 0.55F * (1.0F - (t - RETRACT_WRAP) / (1.0F - RETRACT_WRAP));
		boolean big = WebSuitGrowth.hulkness(state) > 0.5F;
		PlayerModel parent = this.getParentModel();
		WebSuitGrowth.shift(parent, state);
		ModelPart[] parts = {parent.body, parent.rightArm, parent.leftArm, parent.head, parent.rightLeg, parent.leftLeg};
		float time = state.ageInTicks;
		boolean ownView = WebSymbioteClient.ownFirstPerson(state);
		for (int i = 0; i < parts.length; i++) {
			if (ownView && i == 3) {
				continue;
			}
			float wrap = WebSuitGrowth.wrapped(built, i);
			if (wrap <= 0.0F) {
				continue;
			}
			float[] box = big ? HULK_BOXES[i] : BOXES[i];
			float thick = (big ? HULK_THICK : THICK) * RETRACT_THICK * Math.min(1.0F, wrap * 2.0F);
			int part = i;
			poseStack.pushPose();
			parent.root().translateAndRotate(poseStack);
			parts[i].translateAndRotate(poseStack);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) -> {
				for (int k = 0; k < STRANDS * (big ? 2 : 1); k++) {
					strand(buffer, pose, box, part, k, wrap, thick, time, big, state.lightCoords);
				}
			});
			poseStack.popPose();
		}
	}

	/**
	 * Fighting a takeover's hold: wherever you've torn out of the suit (WebSuitGrowth's peel), its tendrils cling on and
	 * try to haul it back over you, coiling down the bared part from the suit's torn edge (a thick, heaving band), more
	 * of them and thicker the further out you are, writhing faster than they ever do putting it on.
	 */
	private static final float STRUGGLE_THICK = 0.75F, STRUGGLE_WRITHE = 1.8F;

	private void struggle(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, float pull) {
		PlayerModel parent = this.getParentModel();
		WebSuitGrowth.shift(parent, state);
		ModelPart[] parts = {parent.body, parent.rightArm, parent.leftArm, parent.head, parent.rightLeg, parent.leftLeg};
		float time = state.ageInTicks * STRUGGLE_WRITHE;
		boolean ownView = WebSymbioteClient.ownFirstPerson(state);
		for (int i = 0; i < parts.length; i++) {
			float peel = WebSuitGrowth.peel(pull, i);
			if (peel <= 0.03F || ownView && i == 3) {
				continue;
			}
			float[] box = BOXES[i];
			float cover = 1.0F - peel;
			float thick = STRUGGLE_THICK * (0.6F + 0.6F * peel);
			int part = i;
			poseStack.pushPose();
			parent.root().translateAndRotate(poseStack);
			parts[i].translateAndRotate(poseStack);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) -> {
				// the strands reach from the joint past the torn edge, over the bare part (as far as it's bared)
				float wrap = Mth.clamp(cover + peel * 0.8F, 0.0F, 1.0F);
				for (int k = 0; k < STRANDS; k++) {
					strand(buffer, pose, box, part, k, wrap, thick, time, false, state.lightCoords);
				}
				if (cover > 0.02F) {
					ring(buffer, pose, box, cover, time, state.lightCoords);
				}
			});
			poseStack.popPose();
		}
	}

	/** One strand coiling round a part from its joint, out to {@code wrap} of the way along it. */
	private static void strand(VertexConsumer buffer, PoseStack.Pose pose, float[] box, int part, int k, float wrap, float thick, float time,
		boolean hulk, int light) {
		float cx = (box[0] + box[3]) * 0.5F, cz = (box[2] + box[5]) * 0.5F;
		float rx = (box[3] - box[0]) * 0.5F + OUT, rz = (box[5] - box[2]) * 0.5F + OUT;
		float phase = k * Mth.TWO_PI / (STRANDS * (hulk ? 2 : 1)) + part * 0.7F;
		// alternate strands coil the other way: a lattice, not a spring
		float turn = (k % 2 == 0 ? 1.0F : -1.0F) * TURNS * (hulk ? 0.8F : 1.0F);
		Vec3 prev = null;
		int pieces = Math.max(1, (int)Math.ceil(PIECES * wrap));
		for (int n = 0; n <= pieces; n++) {
			float f = Math.min(wrap, n / (float)PIECES);
			float angle = phase + turn * Mth.TWO_PI * f + Mth.sin(time * 0.35F + k + f * 4.0F) * 0.25F;
			// writhing in and out as it goes
			float swellOut = 1.0F + Mth.sin(time * 0.5F + f * 9.0F + k * 2.0F) * 0.08F;
			float y = Mth.lerp(f, box[1], box[4]);
			Vec3 p = new Vec3(cx + Mth.cos(angle) * rx * swellOut, y, cz + Mth.sin(angle) * rz * swellOut).scale(1.0 / 16.0);
			if (prev != null) {
				// thinning to the growing tip
				float t0 = thick * (n >= pieces - 1 ? 0.6F : 1.0F), t1 = thick * (n == pieces ? 0.25F : 1.0F);
				WebTubes.cord(buffer, pose, prev, p, t0 / 16.0, t1 / 16.0, (n + k) % 3 == 0 ? WebTubes.SHEEN : WebTubes.BLACK, light);
			}
			prev = p;
		}
	}

	/** The suit's leading edge: a thick band of tendril round the part where it has folded to. */
	private static void ring(VertexConsumer buffer, PoseStack.Pose pose, float[] box, float edge, float time, int light) {
		float cx = (box[0] + box[3]) * 0.5F, cz = (box[2] + box[5]) * 0.5F;
		float rx = (box[3] - box[0]) * 0.5F + 0.2F, rz = (box[5] - box[2]) * 0.5F + 0.2F;
		float y = Mth.lerp(edge, box[1], box[4]);
		int around = 10;
		Vec3 prev = null;
		for (int n = 0; n <= around; n++) {
			float angle = n * Mth.TWO_PI / around;
			float bulge = 1.0F + Mth.sin(time * 0.8F + n * 1.3F) * 0.1F;
			Vec3 p = new Vec3(cx + Mth.cos(angle) * rx * bulge, y + Mth.sin(time * 0.6F + n) * 0.3F, cz + Mth.sin(angle) * rz * bulge).scale(1.0 / 16.0);
			if (prev != null) {
				WebTubes.cord(buffer, pose, prev, p, EDGE_THICK / 16.0, EDGE_THICK / 16.0, n % 2 == 0 ? WebTubes.SHEEN : WebTubes.BLACK, light);
			}
			prev = p;
		}
	}
}
