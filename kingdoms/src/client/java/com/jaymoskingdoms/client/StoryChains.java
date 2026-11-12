package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The "Still in chains" road's figure in chains (the NewRoads plan; Andy, 2026-11-12: "fix it"): irons on both wrists and
 * both ankles, and a chain from wrist to wrist across the front, drawn on the figure {@link StoryFigure} is drawing for
 * that road ({@link #drawing}). Our own boxes and paint ({@code textures/entity/story/chains.png}).
 */
public final class StoryChains {
	static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:story_chains");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/story/chains.png");
	/** The figure being drawn in chains just now (StoryFigure sets it around the draw). */
	static @Nullable LivingEntity drawing;

	private static @Nullable ModelPart rightCuff, leftCuff, legCuff, chain;

	private StoryChains() {
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

	/** From the living renderer's extractRenderState: whether {@code entity} is the figure in chains. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, drawing != null && entity == drawing ? Boolean.TRUE : null);
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
			if (!Boolean.TRUE.equals(state.getData(KEY)) || state.isInvisible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			if (McaCompat.LOADED && McaLookModels.marks(model) instanceof HumanoidModel<?> mca) {
				@SuppressWarnings("unchecked")
				HumanoidModel<S> posed = (HumanoidModel<S>)mca;
				model = posed;
			}
			build();
			part(poseStack, collector, light, model.rightArm, rightCuff);
			part(poseStack, collector, light, model.leftArm, leftCuff);
			part(poseStack, collector, light, model.rightLeg, legCuff);
			part(poseStack, collector, light, model.leftLeg, legCuff);
			// the chain between the wrists hangs in front of the body (root space: the hands hang at y 12, x 6 out)
			collector.order(2).submitModelPart(chain, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, -1);
		}
	}

	private static void part(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart limb, @Nullable ModelPart piece) {
		poseStack.pushPose();
		limb.translateAndRotate(poseStack);
		collector.order(2).submitModelPart(piece, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, -1);
		poseStack.popPose();
	}

	private static void build() {
		if (chain != null) {
			return;
		}
		rightCuff = cuff(-1.0F, 8.0F);
		leftCuff = cuff(1.0F, 8.0F);
		legCuff = cuff(0.0F, 9.0F);
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("chain", CubeListBuilder.create()
			.texOffs(0, 12).addBox(-5.5F, 12.5F, -3.0F, 11.0F, 1.0F, 1.0F)
			.texOffs(0, 16).addBox(-6.5F, 12.5F, -3.0F, 1.0F, 1.0F, 3.0F)
			.texOffs(0, 16).addBox(5.5F, 12.5F, -3.0F, 1.0F, 1.0F, 3.0F), PartPose.ZERO);
		chain = LayerDefinition.create(mesh, 32, 32).bakeRoot();
	}

	/** An iron about a limb whose middle is at {@code x}, from {@code y} down two pixels. */
	private static ModelPart cuff(float x, float y) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(0, 0).addBox(x - 2.0F, y, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.3F)),
			PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32).bakeRoot();
	}
}
