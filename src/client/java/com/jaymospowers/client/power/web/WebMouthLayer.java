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
 * and closing right over it, rimmed with fangs, hinged at the back of the head. With each of the
 * {@link WebSymbiote#EAT_CHOMPS} chomps they gape wide, the whole maw swelling and lunging forward as if to swallow
 * what's in front, then snap shut. Everyone sees it. Texture: {@code tools/gen_web_maw.py}.
 */
final class WebMouthLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_mouth.png");
	/** How far the skull lifts and the jaw drops at the widest (radians). */
	private static final float SKULL_GAPE = 0.95F, JAW_GAPE = 0.8F;
	/** At the very widest, the maw is this much bigger, and this far forward (pixels). */
	private static final float SWELL = 0.45F, LUNGE = 3.0F;
	/** It grows out of the head over this much of the meal, and sinks back over the last of it. */
	private static final float MORPH = 0.08F;

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
		if (state.getData(WebSymbioteClient.EATING) == null || state.isInvisible) {
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
			Float phase = state.getData(WebSymbioteClient.EATING);
			if (phase == null) {
				return;
			}
			// wide on the way in, snapping shut on each bite; each bite its own size
			float chomp = Math.abs(Mth.sin(phase * WebSymbiote.EAT_CHOMPS * Mth.PI));
			int bite = Math.min(WebSymbiote.EAT_CHOMPS - 1, (int)(phase * WebSymbiote.EAT_CHOMPS));
			// each bite a random size (WebSymbiote#biteSize, the same the server rolls: a full last bite swallows it whole)
			Long start = state.getData(WebSymbioteClient.EATING_SEED);
			Integer eater = state.getData(WebSymbioteClient.EATER);
			float size = start == null || eater == null ? 1.0F : WebSymbiote.biteSize(start, eater, bite);
			float open = (float)Math.pow(chomp, 0.6) * size;
			this.skull.xRot = -open * SKULL_GAPE - (float)Math.pow(chomp, 0.6) * 0.25F;
			this.jaw.xRot = open * JAW_GAPE + (float)Math.pow(chomp, 0.6) * 0.2F;
			// it grows out of the head, swells with every gape, and sinks back at the end
			float morph = Mth.clamp(Math.min(phase, 1.0F - phase) / MORPH, 0.0F, 1.0F);
			float scale = Mth.lerp(morph, 0.8F, 1.0F) * (1.0F + SWELL * open);
			this.root.xScale = scale;
			this.root.yScale = scale;
			this.root.zScale = scale;
			this.root.z = -LUNGE * open;
		}
	}
}
