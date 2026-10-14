package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Verdants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.23) A Verdant's antennae ({@link Verdants}): two thin stalks from the top of the forehead, leaning out and curling
 * forward, each with a little knob, in the same green as their skin ({@code textures/entity/verdant_antennae.png} is
 * greyscale, tinted). Drawn on any humanoid that's a Verdant (MCA's villagers above all); their skin is
 * {@code mixin/mca/McaVerdantSkinMixin}.
 */
public final class VerdantLook {
	/** The Verdant's green (opaque ARGB), or absent for anyone else. */
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:verdant");
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/verdant_antennae.png");
	/** Where the stalks stand (pixels from the head's middle, out to each side and back from the brow), how far they lean. */
	static final float APART = 1.5F, BACK = -2.0F, LEAN_OUT = 0.3F, LEAN_BACK = -0.2F, CURL = 0.75F;

	private static ModelPart antennae;

	private VerdantLook() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: the Verdant's green on their render state. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		int green = Verdants.skin(entity);
		state.setData(KEY, green == 0 ? null : green);
	}

	private static ModelPart antennae() {
		if (antennae == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			for (int side = -1; side <= 1; side += 2) {
				String name = side < 0 ? "right" : "left";
				PartDefinition stalk = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
					PartPose.offsetAndRotation(side * APART, -8.0F, BACK, LEAN_BACK, 0.0F, side * LEAN_OUT));
				PartDefinition tip = stalk.addOrReplaceChild(name + "_tip", CubeListBuilder.create().texOffs(4, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
					PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, CURL, 0.0F, side * 0.1F));
				tip.addOrReplaceChild(name + "_knob", CubeListBuilder.create().texOffs(0, 6).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 2.0F, 2.0F),
					PartPose.offset(0.0F, -4.0F, 0.0F));
			}
			antennae = LayerDefinition.create(mesh, 16, 16).bakeRoot();
		}
		return antennae;
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Integer green = state.getData(KEY);
			if (green == null || state.isInvisible || !this.getParentModel().head.visible) {
				return;
			}
			poseStack.pushPose();
			this.getParentModel().head.translateAndRotate(poseStack);
			collector.submitModelPart(antennae(), poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, null, green);
			poseStack.popPose();
		}
	}
}
