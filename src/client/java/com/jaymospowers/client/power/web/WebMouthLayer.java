package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
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
 * The symbiote's maw, while it eats (Consume): the whole head morphs into it. A skull and a jaw, bigger than the head
 * and closing right over it, rimmed with fangs, hinged at the back of the head. It swells out as the tendril brings
 * the meal up, gapes to its widest and snaps shut on each bite (one, or two for something cow-sized), chews a while,
 * and sinks back (timings in {@link WebSymbiote#EAT_REACH} and on). Everyone sees it. Texture: {@code tools/gen_web_maw.py}.
 */
final class WebMouthLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_mouth.png");
	/** How far the skull lifts and the jaw drops at the widest (radians). */
	private static final float SKULL_GAPE = 0.95F, JAW_GAPE = 0.8F;
	/** At the very widest, the maw is this much bigger, and this far forward (pixels). */
	private static final float SWELL = 0.45F, LUNGE = 3.0F;
	/** How many ticks before a bite the maw starts to gape, and how wide a chew opens it. */
	private static final float GAPE_TICKS = 8.0F, CHEW_OPEN = 0.18F;

	private final MouthModel model;

	WebMouthLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the throat, closing off the face behind it
		root.addOrReplaceChild("throat", CubeListBuilder.create().texOffs(32, 40).addBox(-5.0F, -5.5F, -5.0F, 10.0F, 5.0F, 0.5F), PartPose.ZERO);
		// the skull over the head: fangs down along its rim, a red palate inside
		root.addOrReplaceChild("skull", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-5.5F, -8.5F, -10.5F, 11.0F, 8.5F, 11.0F)
				.texOffs(32, 40).addBox(-4.5F, -0.6F, -10.0F, 9.0F, 0.5F, 7.0F)
				.texOffs(0, 40).addBox(-5.0F, 0.0F, -10.3F, 10.0F, 3.0F, 1.0F)
				.texOffs(0, 40).addBox(-5.0F, 0.0F, -9.3F, 1.0F, 3.0F, 8.0F)
				.texOffs(0, 40).addBox(4.0F, 0.0F, -9.3F, 1.0F, 3.0F, 8.0F),
			PartPose.offset(0.0F, -3.0F, 5.0F));
		// the jaw under it: fangs up, a tongue
		root.addOrReplaceChild("jaw", CubeListBuilder.create()
				.texOffs(0, 20).addBox(-5.5F, 0.0F, -10.5F, 11.0F, 3.5F, 11.0F)
				.texOffs(32, 40).addBox(-4.5F, -0.4F, -10.0F, 9.0F, 0.5F, 7.0F)
				.texOffs(0, 52).addBox(-5.0F, -3.0F, -10.3F, 10.0F, 3.0F, 1.0F)
				.texOffs(0, 52).addBox(-5.0F, -3.0F, -9.3F, 1.0F, 3.0F, 8.0F)
				.texOffs(0, 52).addBox(4.0F, -3.0F, -9.3F, 1.0F, 3.0F, 8.0F),
			PartPose.offset(0.0F, -3.0F, 5.0F));
		this.model = new MouthModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.getData(WebSymbioteClient.EATING) == null && state.getData(WebSymbioteClient.STARE) == null && state.getData(WebSymbioteClient.ROAR) == null
			&& state.getData(WebSymbioteClient.COUNTER_MAW) == null || state.isInvisible || WebSymbioteClient.ownFirstPerson(state)) {
			return;
		}
		poseStack.pushPose();
		WebFeral.applyModel(this.getParentModel(), state);
		this.getParentModel().head.translateAndRotate(poseStack);
		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		poseStack.popPose();
	}

	private static final class MouthModel extends Model<AvatarRenderState> {
		private final ModelPart root;
		private final ModelPart skull;
		private final ModelPart jaw;

		MouthModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.root = root;
			this.skull = root.getChild("skull");
			this.jaw = root.getChild("jaw");
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
			// gaping wide toward each bite, snapping shut on it
			float open = 0.0F;
			for (int n = 0; n <= (big ? 1 : 0); n++) {
				float before = WebSymbiote.biteAt(n, reach) - t;
				if (before >= 0.0F && before < GAPE_TICKS) {
					float g = 1.0F - before / GAPE_TICKS;
					open = Math.max(open, g * g * (3.0F - 2.0F * g));
				}
			}
			// then chewing
			if (t > last && t < end - WebSymbiote.EAT_SHRINK) {
				open = Math.max(open, CHEW_OPEN * Math.abs(Mth.sin((t - last) * 0.45F)));
			}
			this.skull.xRot = -open * SKULL_GAPE;
			this.jaw.xRot = open * JAW_GAPE;
			// it grows out of the head as the meal comes up, swells with every gape, and sinks back at the end
			float morph = Mth.clamp(Math.min(t / reach, (end - t) / WebSymbiote.EAT_SHRINK), 0.0F, 1.0F);
			float scale = Mth.lerp(morph, 0.8F, 1.0F) * (1.0F + SWELL * open);
			this.root.xScale = scale;
			this.root.yScale = scale;
			this.root.zScale = scale;
			this.root.z = -LUNGE * open;
		}
	}
}
