package com.jaymoskingdoms.client.harvest;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.harvest.HarvestLich;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Draws the {@link HarvestLich} (ported from Seasonal: The Wicked Harvest, MIT; see {@code licenses/seasonal}): the
 * {@link LichModel} at 0.6, its eyes and crown glowing, a gem set in its chest and a scythe in its hand that is always there
 * (it is the lich's own, not an item it holds).
 */
public final class LichRenderer extends HumanoidMobRenderer<HarvestLich, HumanoidRenderState, LichModel> {
	static final ModelLayerLocation BODY = layer("harvest_lich", "main"), GEM = layer("harvest_lich", "gem"), SCYTHE = layer("harvest_lich", "scythe");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/harvest_lich.png");
	private static final Identifier GLOW = JaymosKingdoms.id("textures/entity/harvest_lich_glow.png");
	private static final Identifier GEM_TEXTURE = JaymosKingdoms.id("textures/entity/harvest_lich_gem.png");
	private static final Identifier SCYTHE_TEXTURE = JaymosKingdoms.id("textures/entity/harvest_lich_scythe.png");
	private static final Identifier SCYTHE_GLOW = JaymosKingdoms.id("textures/entity/harvest_lich_scythe_glow.png");
	/** How much smaller than its model's own units it is drawn, and how far down its feet are in them. */
	static final float SCALE = 0.6F, FEET_DROP = 1.499F;
	/** The scythe: plates in the blade, each this long (model units), and how far each turns on the one before. */
	static final int BLADE_PLATES = 7;
	static final float PLATE_LENGTH = 6.4F, PLATE_TURN = 0.17F;

	public LichRenderer(EntityRendererProvider.Context context) {
		super(context, new LichModel(context.bakeLayer(BODY)), 0.6F);
		this.addLayer(new Glow(this));
		this.addLayer(new Gem(this, context));
		this.addLayer(new Scythe(this, context));
	}

	private static ModelLayerLocation layer(String model, String part) {
		return new ModelLayerLocation(JaymosKingdoms.id(model), part);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(HumanoidRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
		poseStack.translate(0.0F, -FEET_DROP, 0.0F);
	}

	static LayerDefinition gemLayer() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("gem", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 5.0F, -7.0F, 4.0F, 4.0F, 1.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16);
	}

	/** A scythe: a long shaft with a knob and a spike, a blade of plates curving down off its top and a bright edge along it. */
	static LayerDefinition scytheLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartPose hold = PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 1.4F, 0.0F, 0.0F);
		root.addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -38.0F, -1.0F, 2.0F, 48.0F, 2.0F)
			.texOffs(8, 0).addBox(-1.5F, -42.0F, -1.5F, 3.0F, 4.0F, 3.0F).addBox(-0.8F, -40.8F, 1.5F, 1.6F, 1.6F, 4.0F), hold);
		PartDefinition blade = root.addOrReplaceChild("blade", CubeListBuilder.create(), hold);
		float x = 1.5F, y = -40.0F, turn = 0.0F;
		for (int i = 0; i < BLADE_PLATES; i++) {
			float width = 3.6F - i * 0.4F;
			blade.addOrReplaceChild("plate" + i, CubeListBuilder.create().texOffs(40, 0).addBox(0.0F, 0.0F, -width / 2.0F, PLATE_LENGTH, 0.7F, width),
				PartPose.offsetAndRotation(x, y, 0.0F, 0.0F, 0.0F, turn));
			x += (float)Math.cos(turn) * (PLATE_LENGTH - 0.6F);
			y += (float)Math.sin(turn) * (PLATE_LENGTH - 0.6F);
			turn += PLATE_TURN;
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The eyes and crown, lit from within. */
	private static final class Glow extends EyesLayer<HumanoidRenderState, LichModel> {
		Glow(RenderLayerParent<HumanoidRenderState, LichModel> parent) {
			super(parent);
		}

		@Override
		public RenderType renderType() {
			return RenderTypes.eyes(GLOW);
		}
	}

	/** The gem in its chest. */
	private static final class Gem extends RenderLayer<HumanoidRenderState, LichModel> {
		private final ModelPart gem;

		Gem(RenderLayerParent<HumanoidRenderState, LichModel> parent, EntityRendererProvider.Context context) {
			super(parent);
			this.gem = context.bakeLayer(GEM);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
			poseStack.pushPose();
			this.getParentModel().root().translateAndRotate(poseStack);
			this.getParentModel().body.translateAndRotate(poseStack);
			collector.submitModelPart(this.gem, poseStack, RenderTypes.entityCutout(GEM_TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
			collector.order(1).submitModelPart(this.gem, poseStack, RenderTypes.eyes(GEM_TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
			poseStack.popPose();
		}
	}

	/** The scythe, in the right hand. */
	private static final class Scythe extends RenderLayer<HumanoidRenderState, LichModel> {
		private final ModelPart scythe;

		Scythe(RenderLayerParent<HumanoidRenderState, LichModel> parent, EntityRendererProvider.Context context) {
			super(parent);
			this.scythe = context.bakeLayer(SCYTHE);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot) {
			poseStack.pushPose();
			this.getParentModel().translateToHand(state, HumanoidArm.RIGHT, poseStack);
			poseStack.translate(-4.0F / 16.0F, 0.0F, 0.0F);
			collector.submitModelPart(this.scythe, poseStack, RenderTypes.entityCutout(SCYTHE_TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
			collector.order(1).submitModelPart(this.scythe, poseStack, RenderTypes.eyes(SCYTHE_GLOW), light, OverlayTexture.NO_OVERLAY, null);
			poseStack.popPose();
		}
	}
}
