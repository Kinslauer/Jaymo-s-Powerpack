package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The symbiote's maw, while it eats (Consume): the whole head morphs into it. Since Andy's "fix the old large head
 * eating animation to be more like the new style" it's the big body's head grown huge ({@link WebMawMesh} and its
 * texture, from {@code tools/gen_web_hulk.py}): the same wet black hide with its veins and teal rims, the big white
 * eyes, a heavy snout over a red palate, rows of fangs; a skull and a jaw bigger than the head and closing right over
 * it, hinged at the back of the head. It swells out as the tendril brings the meal up, breathing with its jaws a
 * little open, then gapes as wide as it goes over the meal (quivering), lashes its tongue out at it, and snaps shut on
 * each bite in {@code SNAP_TICKS} (one, or two for something cow-sized), the skull jolting down on it; chews a while,
 * and sinks back (timings in {@link WebSymbiote#EAT_REACH} and on). Everyone sees it. The maw keeps its tongue on the
 * plain suit too (the one tongue the plain suit has: it's eating).
 */
final class WebMouthLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_mouth.png");
	/** How far the skull lifts and the jaw drops at the widest (radians). */
	private static final float SKULL_GAPE = 0.8F, JAW_GAPE = 1.15F;
	/** At the very widest, the maw is this much bigger, and this far forward (pixels). */
	private static final float SWELL = 0.45F, LUNGE = 3.0F;
	/**
	 * Eating: the jaws hang {@code REST_OPEN} open, breathing {@code REST_BREATH}, as the meal comes up; {@code GAPE_TICKS}
	 * before a bite's snap they gape to the widest, quivering {@code GAPE_QUIVER}; then they snap shut in {@code SNAP_TICKS},
	 * landing on the bite, and the skull jolts down {@code SNAP_JOLT} (radians) over {@code JOLT_TICKS}. A chew opens it
	 * {@code CHEW_OPEN}.
	 */
	private static final float REST_OPEN = 0.14F, REST_BREATH = 0.05F, GAPE_TICKS = 10.0F, GAPE_QUIVER = 0.05F, SNAP_TICKS = 1.5F,
		SNAP_JOLT = 0.12F, JOLT_TICKS = 3.0F, CHEW_OPEN = 0.18F;
	/**
	 * Its tongue (the big body's, {@link WebHulk#tongue}): {@code TONGUE_LEAD} ticks before each bite it lashes out at the
	 * meal from the gaping jaws, all the way out by {@code TONGUE_OUT} of that and drawn back in from {@code TONGUE_IN},
	 * gone into the mouth as the jaws snap shut. It also tastes the air as the maw first swells out ({@code TASTE} of its
	 * length).
	 */
	private static final float TONGUE_LEAD = 8.0F, TONGUE_OUT = 0.35F, TONGUE_IN = 0.72F, TASTE = 0.5F;
	/** The maw's tongue: pieces, each one's length (pixels), its width and thickness at the root, drooping each piece. */
	private static final int TONGUE_PIECES = 14;
	private static final float TONGUE_PIECE = 1.8F, TONGUE_WIDTH = 2.2F, TONGUE_THICK = 0.7F, TONGUE_DROOP = 0.04F;
	/**
	 * The maw's fangs stay in its gums until it's open (Andy: the teeth show only when he opens his mouth to eat): they
	 * slide out as it morphs out of the head, all the way by {@code TEETH_AT} of the morph, and back in as it sinks. The
	 * lower fangs root {@code JAW_FANG_ROOT} pixels down in the jaw.
	 */
	private static final float TEETH_AT = 0.6F, JAW_FANG_ROOT = 0.0F;

	private final MouthModel model;

	WebMouthLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		// the big body's head grown huge: the throat closing off the face behind it, the skull (eyes, snout, red palate,
		// fangs down) and the jaw (fangs up), hinged at the back of the head
		this.model = new MouthModel(WebMawMesh.create().bakeRoot());
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.getData(WebSymbioteClient.EATING) == null && state.getData(WebSymbioteClient.STARE) == null && state.getData(WebSymbioteClient.ROAR) == null
			&& state.getData(WebSymbioteClient.COUNTER_MAW) == null || state.isInvisible || WebSymbioteClient.ownFirstPerson(state)) {
			return;
		}
		poseStack.pushPose();
		WebFeral.applyModel(this.getParentModel(), state);
		WebClimbPose.applyModel(this.getParentModel(), state);
		WebSuitGrowth.shift(this.getParentModel(), state);
		this.getParentModel().head.translateAndRotate(poseStack);
		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(WebStrainLook.texture(TEXTURE, state)), lightCoords,
			OverlayTexture.NO_OVERLAY, state.outlineColor);
		Float eating = state.getData(WebSymbioteClient.EATING);
		if (eating != null) {
			tongue(poseStack, collector, state, eating);
		}
		poseStack.popPose();
	}

	/**
	 * The tongue, off the maw's jaw (the pose stack at the head): a flick tasting the air as the maw swells out, then
	 * lashed out at the meal just before the last bite and drawn back into the mouth as the jaws close on it.
	 */
	private void tongue(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, float t) {
		boolean big = Boolean.TRUE.equals(state.getData(WebSymbioteClient.MEAL_BIG));
		Integer reachData = state.getData(WebSymbioteClient.MEAL_REACH);
		int reach = reachData == null ? WebSymbiote.EAT_REACH : reachData;
		// the next bite's lash (each bite has one)
		float before = -1.0F;
		for (int n = 0; n <= (big ? 1 : 0); n++) {
			float b = WebSymbiote.biteAt(n, reach) - t;
			if (b >= 0.0F && b < TONGUE_LEAD) {
				before = b;
				break;
			}
		}
		float out;
		float whip;
		if (before >= 0.0F) {
			// lashed out at the meal, then reeled back in under the falling jaw
			float f = 1.0F - before / TONGUE_LEAD;
			out = f < TONGUE_OUT ? f / TONGUE_OUT : f > TONGUE_IN ? (1.0F - f) / (1.0F - TONGUE_IN) : 1.0F;
			out = Mth.clamp(out, 0.0F, 1.0F);
			out = out * out * (3.0F - 2.0F * out);
			whip = Mth.sin(f * Mth.PI);
		} else if (t < WebHulk.FLICK_TICKS) {
			// a taste of the air as it swells out of the head
			float f = t / WebHulk.FLICK_TICKS;
			out = WebHulk.flickOut(f) * TASTE;
			whip = Mth.sin(f * Mth.PI);
		} else {
			return;
		}
		if (out <= 0.02F) {
			return;
		}
		// the maw's pose now (the renderer poses it again the same way when it draws it)
		this.model.setupAnim(state);
		float jawAngle = this.model.jaw.xRot;
		float age = state.ageInTicks;
		float drawn = out;
		int light = state.lightCoords;
		poseStack.pushPose();
		this.model.root.translateAndRotate(poseStack);
		this.model.jaw.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebStrainLook.texture(WebTubes.TEXTURE, state)), (pose, buffer) ->
			// from the floor of the mouth, aimed level out of the dropped jaw
			WebHulk.tongue(buffer, pose, new net.minecraft.world.phys.Vec3(0.0, -0.5, -2.0), -0.05F - jawAngle * 0.5F, TONGUE_PIECES,
				TONGUE_PIECE, TONGUE_WIDTH, TONGUE_THICK, age, drawn, whip, TONGUE_DROOP, light));
		poseStack.popPose();
	}

	private static final class MouthModel extends Model<AvatarRenderState> {
		private final ModelPart root;
		private final ModelPart skull;
		private final ModelPart jaw;
		private final ModelPart[] fangs, jawFangs;

		MouthModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.root = root;
			this.skull = root.getChild("skull");
			this.jaw = root.getChild("jaw");
			this.fangs = new ModelPart[] {this.skull.getChild("fangs"), this.skull.getChild("fangs_r"), this.skull.getChild("fangs_l")};
			this.jawFangs = new ModelPart[] {this.jaw.getChild("jaw_fangs"), this.jaw.getChild("jaw_fangs_r"), this.jaw.getChild("jaw_fangs_l")};
		}

		/** Slides the fangs out of the gums ({@code bare} 0 hidden, 1 out), eased. */
		private void teeth(float bare) {
			bare = WebEase.smooth(bare);
			for (ModelPart fang : this.fangs) {
				fang.yScale = bare;
				fang.visible = bare > 0.02F;
			}
			for (ModelPart fang : this.jawFangs) {
				// (they point up out of the chin: shrunk toward their root in it)
				fang.y = JAW_FANG_ROOT * (1.0F - bare);
				fang.yScale = bare;
				fang.visible = bare > 0.02F;
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Float ticks = state.getData(WebSymbioteClient.EATING);
			Float stare = state.getData(WebSymbioteClient.STARE);
			Float roar = state.getData(WebSymbioteClient.ROAR);
			if (ticks == null && roar != null) {
				// roaring: it bulges out of the head snarling as it builds, then the maw is thrown open as wide as it goes
				float r = roar;
				float blast = WebSymbiote.ROAR_BLAST_AT / (float)WebSymbiote.ROAR_TICKS;
				float morph = Mth.clamp(Math.min(r / 0.2F, (1.0F - r) / 0.12F), 0.0F, 1.0F);
				float snarl = 0.18F + 0.08F * Mth.sin(state.ageInTicks * 1.7F);
				float gape = r < blast - 0.08F ? 0.0F : r < blast ? (r - blast + 0.08F) / 0.08F : 1.0F - Mth.clamp((r - blast - 0.12F) / 0.12F, 0.0F, 1.0F);
				float open = Math.max(snarl * morph, gape * gape * (3.0F - 2.0F * gape) * 1.25F);
				this.skull.xRot = -open * SKULL_GAPE;
				this.jaw.xRot = open * JAW_GAPE;
				float scale = Mth.lerp(morph, 0.8F, 1.1F) * (1.0F + SWELL * gape);
				this.root.xScale = scale;
				this.root.yScale = scale;
				this.root.zScale = scale;
				this.root.z = -LUNGE * gape;
				this.teeth(morph / TEETH_AT);
				return;
			}
			float[] counter = state.getData(WebSymbioteClient.COUNTER_MAW);
			if (ticks == null && counter != null) {
				// a counter: it bursts out of the head, gapes and snaps on each bite (or roars), and sinks back
				float t = counter[0];
				int kind = (int)counter[1];
				float end = com.jaymospowers.power.web.WebCounter.ticks(kind);
				float morph = Mth.clamp(Math.min(t / 3.0F, (end - t) / 4.0F), 0.0F, 1.0F);
				float open = com.jaymospowers.power.web.WebCounter.maw(kind, t);
				this.skull.xRot = -open * SKULL_GAPE;
				this.jaw.xRot = open * JAW_GAPE;
				float scale = Mth.lerp(morph, 0.8F, 1.15F) * (1.0F + SWELL * Math.min(open, 1.0F));
				this.root.xScale = scale;
				this.root.yScale = scale;
				this.root.zScale = scale;
				this.root.z = -LUNGE * Math.min(open, 1.0F);
				this.teeth(morph / TEETH_AT);
				return;
			}
			if (ticks == null && stare != null) {
				// staring at what it holds: swollen, the jaw hanging a little open, breathing
				float morph = Mth.clamp(stare / 8.0F, 0.0F, 1.0F);
				float breath = 0.12F + 0.06F * Mth.sin(stare * 0.25F);
				this.skull.xRot = -breath * SKULL_GAPE;
				this.jaw.xRot = breath * JAW_GAPE;
				float scale = Mth.lerp(morph, 0.8F, 1.15F);
				this.root.xScale = scale;
				this.root.yScale = scale;
				this.root.zScale = scale;
				this.root.z = -1.0F * morph;
				this.teeth(morph / TEETH_AT);
				return;
			}
			if (ticks == null) {
				return;
			}
			float t = ticks;
			boolean big = Boolean.TRUE.equals(state.getData(WebSymbioteClient.MEAL_BIG));
			Integer reachData = state.getData(WebSymbioteClient.MEAL_REACH);
			int reach = reachData == null ? WebSymbiote.EAT_REACH : reachData;
			int last = WebSymbiote.biteAt(big ? 1 : 0, reach);
			int end = WebSymbiote.mealTicks(big, reach);
			// hanging a little open and breathing as the meal comes up; gaping as wide as it goes over it, quivering,
			// and snapping shut on each bite, the skull jolting down on it
			float open = t < last ? REST_OPEN + REST_BREATH * Mth.sin(state.ageInTicks * 0.3F) : 0.0F;
			float jolt = 0.0F;
			for (int n = 0; n <= (big ? 1 : 0); n++) {
				float before = WebSymbiote.biteAt(n, reach) - t;
				if (before >= 0.0F && before < GAPE_TICKS + SNAP_TICKS) {
					if (before > SNAP_TICKS) {
						float g = 1.0F - (before - SNAP_TICKS) / GAPE_TICKS;
						float wide = g * g * (3.0F - 2.0F * g);
						open = Math.max(open, wide + Mth.sin(state.ageInTicks * 2.3F) * GAPE_QUIVER * wide);
					} else {
						// the snap: slammed shut, fastest at the end
						float s = before / SNAP_TICKS;
						open = s * s;
					}
				} else if (before < 0.0F && before > -JOLT_TICKS) {
					open = 0.0F;
					jolt = Math.max(jolt, Mth.sin(-before / JOLT_TICKS * Mth.PI));
				}
			}
			// then chewing
			if (t > last + JOLT_TICKS && t < end - WebSymbiote.EAT_SHRINK) {
				open = Math.max(open, CHEW_OPEN * Math.abs(Mth.sin((t - last) * 0.45F)));
			}
			this.skull.xRot = -open * SKULL_GAPE + jolt * SNAP_JOLT;
			this.jaw.xRot = open * JAW_GAPE;
			// it grows out of the head as the meal comes up, swells with every gape, and sinks back at the end
			float morph = Mth.clamp(Math.min(t / reach, (end - t) / WebSymbiote.EAT_SHRINK), 0.0F, 1.0F);
			float scale = Mth.lerp(morph, 0.8F, 1.0F) * (1.0F + SWELL * open);
			this.root.xScale = scale;
			this.root.yScale = scale;
			this.root.zScale = scale;
			this.root.z = -LUNGE * open;
			this.teeth(morph / TEETH_AT);
		}
	}
}
