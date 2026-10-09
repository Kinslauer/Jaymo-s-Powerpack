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
 * The symbiote's jaws, while it eats (Consume): the face splits open over the lower head, a row of teeth above and a
 * hinged jaw below that chomps {@link WebSymbiote#EAT_CHOMPS} times. Everyone sees it.
 */
final class WebMouthLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_mouth.png");
	/** How far in front of the head's face the jaws sit (the suit, and Living Armor's bulk, stand proud of it). */
	private static final float FACE = -4.9F;
	/** The widest the jaw drops (radians). */
	private static final float GAPE = 1.0F;

	private final MouthModel model;

	WebMouthLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the dark-red inside of the mouth, set back a little
		root.addOrReplaceChild("maw", CubeListBuilder.create().texOffs(0, 8).addBox(-3.0F, -3.4F, 0.4F, 6.0F, 3.4F, 0.4F), PartPose.offset(0.0F, 0.0F, FACE));
		// upper teeth, fixed
		root.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.8F, -0.3F, 7.0F, 1.6F, 1.0F), PartPose.offset(0.0F, 0.0F, FACE));
		// lower jaw, hinged at the back of the cheeks
		root.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 4).addBox(-3.5F, -0.2F, -1.0F, 7.0F, 1.6F, 1.6F),
			PartPose.offset(0.0F, -2.2F, FACE + 0.6F));
		this.model = new MouthModel(LayerDefinition.create(mesh, 16, 16).bakeRoot());
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.getData(WebSymbioteClient.EATING) == null || state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		this.getParentModel().head.translateAndRotate(poseStack);
		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		poseStack.popPose();
	}

	private static final class MouthModel extends Model<AvatarRenderState> {
		private final ModelPart jaw;
		private final ModelPart maw;

		MouthModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.jaw = root.getChild("jaw");
			this.maw = root.getChild("maw");
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Float phase = state.getData(WebSymbioteClient.EATING);
			if (phase == null) {
				return;
			}
			// wide on the way in, snapping shut on each bite
			float chomp = Math.abs(Mth.sin(phase * WebSymbiote.EAT_CHOMPS * Mth.PI));
			float open = (float)Math.pow(chomp, 0.6);
			this.jaw.xRot = open * GAPE;
			this.maw.yScale = 0.3F + 0.7F * open;
		}
	}
}
