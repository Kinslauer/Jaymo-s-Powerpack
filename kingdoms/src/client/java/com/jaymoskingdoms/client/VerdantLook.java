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
 * (0.7.23) A Verdant's antennae ({@link Verdants}): two thin stalks from the top of the forehead in one of seven shapes
 * ({@link #SHAPES}: curled, tall, swept, stubby, drooping, forked, moth), in the same green as their skin ({@code textures/entity/verdant_antennae.png} is
 * greyscale, tinted). Drawn on any humanoid that's a Verdant (MCA's villagers above all); their skin is
 * {@code mixin/mca/McaVerdantSkinMixin}.
 */
public final class VerdantLook {
	/** A Verdant's green (opaque ARGB) and antenna shape ({@link #SHAPES}). */
	public record Look(int green, int variant) {
	}

	/** The Verdant's look, or absent for anyone else. */
	static final RenderStateDataKey<Look> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:verdant");
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/verdant_antennae.png");
	/**
	 * The antenna shapes, one per Verdant (picked from their UUID): where the stalks stand (pixels out from the head's
	 * middle, back from the brow), how they lean (out, back), the base stalk's length, the upper stalk's curl forward
	 * and out and its length, the knob's size (0: none), and a side prong (1) or not (0).
	 */
	static final float[][] SHAPES = {
		// apart, back, leanOut, leanBack, base, curl, curlOut, tip, knob, prong
		{1.5F, -2.0F, 0.30F, -0.20F, 4, 0.75F, 0.10F, 4, 2, 0}, // curled: up, then forward, a knob on the end
		{1.5F, -1.5F, 0.12F, -0.05F, 6, 0.00F, 0.00F, 3, 1, 0}, // tall: straight up, a bead on top
		{2.0F, -1.0F, 0.35F, -0.90F, 5, -0.40F, 0.15F, 5, 0, 0}, // swept: laid back over the head like feelers
		{1.5F, -2.0F, 0.45F, 0.00F, 2, 0.30F, 0.00F, 1, 3, 0}, // stubby: short, with big bulbs
		{2.0F, -2.5F, 0.60F, -0.10F, 4, 0.00F, 1.20F, 4, 2, 0}, // drooping: out to the sides and hanging down
		{1.5F, -2.0F, 0.25F, -0.15F, 4, 0.50F, -0.40F, 3, 1, 1}, // forked: a prong off each, the tips bending in
		{2.5F, -2.5F, 0.70F, -0.30F, 3, 0.90F, 0.30F, 5, 0, 0}, // moth: wide-set, long and arching forward
	};

	private static final ModelPart[] ANTENNAE = new ModelPart[SHAPES.length];

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
		state.setData(KEY, green == 0 ? null : new Look(green, variant(entity.getUUID())));
	}

	/** Which shape {@code id}'s antennae are. */
	static int variant(java.util.UUID id) {
		return Math.floorMod(Long.hashCode(id.getLeastSignificantBits() * 31L + id.getMostSignificantBits()), SHAPES.length);
	}

	private static ModelPart antennae(int variant) {
		if (ANTENNAE[variant] == null) {
			float[] s = SHAPES[variant];
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			for (int side = -1; side <= 1; side += 2) {
				String name = side < 0 ? "right" : "left";
				PartDefinition stalk = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -s[4], -0.5F, 1.0F, s[4], 1.0F),
					PartPose.offsetAndRotation(side * s[0], -8.0F, s[1], s[3], 0.0F, side * s[2]));
				if (s[9] > 0) {
					stalk.addOrReplaceChild(name + "_prong", CubeListBuilder.create().texOffs(0, 8).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
						PartPose.offsetAndRotation(0.0F, -s[4] + 0.5F, 0.0F, 0.2F, 0.0F, side * 0.9F));
				}
				PartDefinition tip = stalk.addOrReplaceChild(name + "_tip", CubeListBuilder.create().texOffs(4, 0).addBox(-0.5F, -s[7], -0.5F, 1.0F, s[7], 1.0F),
					PartPose.offsetAndRotation(0.0F, -s[4], 0.0F, s[5], 0.0F, side * s[6]));
				float knob = s[8];
				if (knob > 0) {
					tip.addOrReplaceChild(name + "_knob", CubeListBuilder.create().texOffs(8, 0).addBox(-knob / 2, -knob, -knob / 2, knob, knob, knob),
						PartPose.offset(0.0F, -s[7] + 0.5F, 0.0F));
				}
			}
			ANTENNAE[variant] = LayerDefinition.create(mesh, 32, 16).bakeRoot();
		}
		return ANTENNAE[variant];
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Look look = state.getData(KEY);
			if (look == null || state.isInvisible || !this.getParentModel().head.visible) {
				return;
			}
			poseStack.pushPose();
			this.getParentModel().head.translateAndRotate(poseStack);
			collector.submitModelPart(antennae(look.variant()), poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, null,
				look.green());
			poseStack.popPose();
		}
	}
}
