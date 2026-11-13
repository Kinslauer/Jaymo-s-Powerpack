package com.jaymoskingdoms.client;

import com.jaymoskingdoms.CaptainWays;
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
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.616, Rivals patch 1) The marks a captain carries back from how he nearly died ({@link CaptainWays#MARKS}): a burned
 * face, an eye patch, an iron jaw, a stitched cheek, a pale eye, claw rakes. Painted in vanilla's style over his head
 * ({@code tools/gen_scars.py}), on MCA's body when MCA draws him (as {@link GreenskinLooks} lays its scars), on anything
 * humanoid.
 * <p>
 * Andy (2026-11-12, "please add them all"): what he wears for a limb he came back without ({@link CaptainWays#LIMBS}), a
 * hook on a leather cuff where an arm was and an oak peg strapped on where a leg was, posed with the limb that's gone
 * ({@code tools/gen_captain_limbs.py}); and an illager captain's marks too, on its taller head (own copies of the
 * textures), the hook only while his arms aren't folded.
 */
public final class CaptainMarks {
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:captain_marks");
	private static final Identifier[] TEXTURES = new Identifier[CaptainWays.MARKS.size()], ILLAGER = new Identifier[CaptainWays.MARKS.size()];
	private static final Identifier LIMBS = JaymosKingdoms.id("textures/entity/captain/limbs.png");

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = JaymosKingdoms.id("textures/entity/captain/mark_" + CaptainWays.MARKS.get(i) + ".png");
			ILLAGER[i] = JaymosKingdoms.id("textures/entity/captain/mark_" + CaptainWays.MARKS.get(i) + "_illager.png");
		}
	}

	private static @Nullable ModelPart hookRight, hookLeft, peg;

	private CaptainMarks() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			} else if (renderer.getModel() instanceof IllagerModel<?>) {
				helper.register(createIllager(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static IllagerLayer<?> createIllager(RenderLayerParent<?, ?> renderer) {
		return new IllagerLayer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: the marks {@code entity} shows, if any. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		int bits = entity.getAttachedOrElse(CaptainWays.SHOWN, 0);
		state.setData(KEY, bits == 0 ? null : bits);
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Integer bits = state.getData(KEY);
			if (bits == null || state.isInvisible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			if (McaCompat.LOADED) {
				HumanoidModel<S> mca = mcaMarks(model);
				if (mca != null) {
					model = mca;
				}
			}
			if (this.getParentModel().head.visible) {
				for (int i = 0; i < TEXTURES.length; i++) {
					if ((bits & (1 << i)) != 0) {
						collector.order(2).submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURES[i]), lightCoords, OverlayTexture.NO_OVERLAY, -1,
							null, state.outlineColor);
					}
				}
			}
			limbs(poseStack, collector, lightCoords, bits, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg, true);
		}
	}

	static final class IllagerLayer<S extends IllagerRenderState> extends RenderLayer<S, IllagerModel<S>> {
		IllagerLayer(RenderLayerParent<S, IllagerModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Integer bits = state.getData(KEY);
			if (bits == null || state.isInvisible) {
				return;
			}
			IllagerModel<S> model = this.getParentModel();
			MobAnimations.IllagerParts parts = (MobAnimations.IllagerParts)model;
			if (model.getHead().visible) {
				for (int i = 0; i < ILLAGER.length; i++) {
					if ((bits & (1 << i)) != 0) {
						// (the head alone: an illager's legs share one patch of skin, and his marks are all on the face)
						collector.order(2).submitModelPart(model.getHead(), poseStack, RenderTypes.entityCutout(ILLAGER[i]), lightCoords,
							OverlayTexture.NO_OVERLAY, null, -1, state.outlineColor);
					}
				}
			}
			boolean folded = state.armPose == AbstractIllager.IllagerArmPose.CROSSED;
			limbs(poseStack, collector, lightCoords, bits, parts.jaymoskingdoms$rightArm(), parts.jaymoskingdoms$leftArm(), parts.jaymoskingdoms$rightLeg(),
				parts.jaymoskingdoms$leftLeg(), !folded);
		}
	}

	/** His hooks and pegs, each posed with the limb it stands in for. */
	private static void limbs(PoseStack poseStack, SubmitNodeCollector collector, int light, int bits, ModelPart rightArm, ModelPart leftArm,
		ModelPart rightLeg, ModelPart leftLeg, boolean arms) {
		int limbs = bits >>> CaptainWays.LIMB_BIT;
		if (limbs == 0) {
			return;
		}
		build();
		if (arms && (limbs & 1) != 0) {
			limb(poseStack, collector, light, rightArm, hookRight);
		}
		if (arms && (limbs & 2) != 0) {
			limb(poseStack, collector, light, leftArm, hookLeft);
		}
		if ((limbs & 4) != 0) {
			limb(poseStack, collector, light, rightLeg, peg);
		}
		if ((limbs & 8) != 0) {
			limb(poseStack, collector, light, leftLeg, peg);
		}
	}

	private static void limb(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart limb, @Nullable ModelPart piece) {
		if (piece == null) {
			return;
		}
		poseStack.pushPose();
		limb.translateAndRotate(poseStack);
		collector.order(2).submitModelPart(piece, poseStack, RenderTypes.entityCutout(LIMBS), light, OverlayTexture.NO_OVERLAY, null, -1);
		poseStack.popPose();
	}

	/**
	 * The pieces, made the first time they're drawn, in limb space: an arm hangs from y -2 to 10 (a right arm's x -3 to 1,
	 * a left's -1 to 3), a leg from y 0 to 12 about x 0; the front is -z. The texture's patches are laid out in
	 * {@code gen_captain_limbs.py}.
	 */
	private static void build() {
		if (peg != null) {
			return;
		}
		hookRight = hook(-1.0F);
		hookLeft = hook(1.0F);
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("peg", CubeListBuilder.create()
			.texOffs(0, 12).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.3F))
			.texOffs(16, 0).addBox(-1.0F, 3.0F, -1.0F, 2.0F, 9.0F, 2.0F)
			.texOffs(24, 0).addBox(-1.0F, 11.0F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.15F)), PartPose.ZERO);
		peg = LayerDefinition.create(mesh, 32, 32).bakeRoot();
	}

	/** A cuff over the hand and a hook curling forward under it, about an arm whose middle is at {@code x}. */
	private static ModelPart hook(float x) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("hook", CubeListBuilder.create()
			.texOffs(0, 0).addBox(x - 2.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.35F))
			.texOffs(0, 8).addBox(x - 0.5F, 10.0F, -0.5F, 1.0F, 3.0F, 1.0F)
			.texOffs(4, 8).addBox(x - 0.5F, 12.0F, -2.5F, 1.0F, 1.0F, 2.0F)
			.texOffs(10, 8).addBox(x - 0.5F, 10.5F, -2.5F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32).bakeRoot();
	}

	@SuppressWarnings("unchecked")
	private static <S extends HumanoidRenderState> @Nullable HumanoidModel<S> mcaMarks(HumanoidModel<S> parent) {
		return (HumanoidModel<S>)McaLookModels.marks(parent);
	}
}
