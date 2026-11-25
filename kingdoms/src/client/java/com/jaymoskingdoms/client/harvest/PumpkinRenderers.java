package com.jaymoskingdoms.client.harvest;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.harvest.ThrownRottenPumpkin;
import com.jaymoskingdoms.harvest.WickedPumpkin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;

/** The wicked pumpkin and the thrown rotten one (ported from Seasonal: The Wicked Harvest, MIT; see {@code licenses/seasonal}). */
public final class PumpkinRenderers {
	static final ModelLayerLocation WICKED = new ModelLayerLocation(JaymosKingdoms.id("wicked_pumpkin"), "main");
	static final ModelLayerLocation THROWN = new ModelLayerLocation(JaymosKingdoms.id("thrown_rotten_pumpkin"), "main");
	private static final Identifier WICKED_TEXTURE = JaymosKingdoms.id("textures/entity/wicked_pumpkin.png");
	private static final Identifier WICKED_GLOW = JaymosKingdoms.id("textures/entity/wicked_pumpkin_glow.png");
	private static final Identifier THROWN_TEXTURE = JaymosKingdoms.id("textures/entity/thrown_rotten_pumpkin.png");
	/** The wicked pumpkin is built twice its size and drawn at this fraction of it, bobbing this far (radians' worth of bob) and swaying its vines. */
	static final float BUILD_SCALE = 0.5F, DRAW_SCALE = 1.4F, BOB = 0.6F, BOB_PERIOD = 42.0F, VINE_SWAY = 0.08F, VINE_LAG = 0.7F;
	/** Down from the entity's origin to the middle of the pumpkin (blocks). */
	static final float CENTRE_HEIGHT = 0.45F;

	private PumpkinRenderers() {
	}

	static LayerDefinition wickedLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition head = mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.ZERO);
		head.addOrReplaceChild("stem", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, -13.0F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.ZERO);
		float[][] vines = {{-4.0F, -5.0F}, {4.0F, -5.0F}, {-3.0F, 1.0F}, {3.0F, 1.0F}};
		for (int i = 0; i < vines.length; i++) {
			head.addOrReplaceChild("vine" + i, CubeListBuilder.create().texOffs(16, 32).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
				PartPose.offset(vines[i][0], 7.0F, vines[i][1]));
		}
		return LayerDefinition.create(mesh, 64, 64).apply(MeshTransformer.scaling(BUILD_SCALE));
	}

	static LayerDefinition thrownLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F), PartPose.ZERO);
		root.addOrReplaceChild("stem", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, -9.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The wicked pumpkin's model: bobs, turns its face where it looks and sways its vines. */
	static final class WickedModel extends EntityModel<LivingEntityRenderState> {
		private final ModelPart head;
		private final ModelPart[] vines = new ModelPart[4];

		WickedModel(ModelPart root) {
			super(root);
			this.head = root.getChild("head");
			for (int i = 0; i < 4; i++) {
				this.vines[i] = this.head.getChild("vine" + i);
			}
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			this.head.y = Mth.sin(state.ageInTicks / BOB_PERIOD * Mth.TWO_PI) * BOB;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.8F;
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.6F;
			for (int i = 0; i < 4; i++) {
				this.vines[i].xRot = Mth.sin(state.ageInTicks * 0.08F - i * VINE_LAG) * VINE_SWAY;
				this.vines[i].zRot = Mth.cos(state.ageInTicks * 0.08F * 0.5F - i * VINE_LAG) * VINE_SWAY;
			}
		}
	}

	static final class Wicked extends MobRenderer<WickedPumpkin, LivingEntityRenderState, WickedModel> {
		Wicked(EntityRendererProvider.Context context) {
			super(context, new WickedModel(context.bakeLayer(WICKED)), 0.4F);
			this.addLayer(new EyesLayer<LivingEntityRenderState, WickedModel>(this) {
				@Override
				public RenderType renderType() {
					return RenderTypes.eyes(WICKED_GLOW);
				}
			});
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new LivingEntityRenderState();
		}

		@Override
		public Identifier getTextureLocation(LivingEntityRenderState state) {
			return WICKED_TEXTURE;
		}

		@Override
		protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
			poseStack.scale(DRAW_SCALE, DRAW_SCALE, DRAW_SCALE);
			// (the model's middle at the middle of the body, not 1.5 blocks up: vanilla lifts every model that far)
			poseStack.translate(0.0F, (1.501F - CENTRE_HEIGHT) / DRAW_SCALE, 0.0F);
		}
	}

	static final class ShotModel extends EntityModel<ThrownState> {
		ShotModel(ModelPart root) {
			super(root);
		}
	}

	static final class ThrownState extends EntityRenderState {
		float spin;
	}

	static final class Thrown extends EntityRenderer<ThrownRottenPumpkin, ThrownState> {
		private final ShotModel model;

		Thrown(EntityRendererProvider.Context context) {
			super(context);
			this.model = new ShotModel(context.bakeLayer(THROWN));
		}

		@Override
		public ThrownState createRenderState() {
			return new ThrownState();
		}

		@Override
		public void extractRenderState(ThrownRottenPumpkin entity, ThrownState state, float partialTick) {
			super.extractRenderState(entity, state, partialTick);
			state.spin = (entity.tickCount + partialTick) * 18.0F;
		}

		@Override
		public void submit(ThrownState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
			poseStack.pushPose();
			poseStack.translate(0.0F, 0.35F, 0.0F);
			poseStack.rotateDegrees(Axis.XP, state.spin);
			poseStack.rotateDegrees(Axis.ZP, state.spin * 0.6F);
			collector.submitModel(this.model, state, poseStack, THROWN_TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
			super.submit(state, poseStack, collector, camera);
		}
	}
}
