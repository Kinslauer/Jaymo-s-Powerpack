package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FrostTech;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
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
 * What a villager wears of the Frostborn's work ({@link FrostTech}), for everyone to see: a scouter over one eye, a made
 * arm, a made leg; and the wooden or iron limb a smith fitted one with after they lost their own (Jaymo's Powers' own
 * wooden and iron limb sheets, the same box). The scouter is Jaymo's Powers' own, drawn from its armour sheet on a helmet-sized box (so
 * a resource pack that repaints the scouter repaints these too; without Powers there is no scouter to draw). A made
 * limb is plating of our own ({@code tools/gen_frost_tech.py}, in the scouter's greys and green light) drawn over the
 * arm or leg, just proud of the sleeve, with its lamps unlit by the dark. Both follow the body the way the Beastfolk's
 * parts do ({@link BeastParts}: on MCA's own posed body when MCA draws it). The scouter is hidden under a helmet or a
 * hood, a limb under a costume, and nothing is drawn on the invisible.
 */
public final class FrostTechLayer {
	/** How far a made limb stands off the bare limb: just past MCA's clothes (their outer layer sits at 0.3125). */
	static final float LIMB_INFLATE = 0.36F;
	/** The scouter's box off the head: a helmet's. */
	static final float SCOUTER_INFLATE = 1.0F;

	/** On a living body's render state: {@link FrostTech#of}, with {@link FrostTech#kinds} above it (shifted 8), or null for none. */
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:frost_tech");
	private static final Identifier SCOUTER = Identifier.fromNamespaceAndPath("jaymospowers", "textures/entity/equipment/humanoid/scouter.png");
	private static final Identifier LIMB = JaymosKingdoms.id("textures/entity/frost_tech/limb.png");
	private static final Identifier WOOD = Identifier.fromNamespaceAndPath("jaymospowers", "textures/entity/wounds/wooden_limb.png");
	private static final Identifier IRON = Identifier.fromNamespaceAndPath("jaymospowers", "textures/entity/wounds/iron_limb.png");
	private static final Identifier LIMB_GLOW = JaymosKingdoms.id("textures/entity/frost_tech/limb_glow.png");
	private static final boolean POWERS = FabricLoader.getInstance().isModLoaded("jaymospowers");

	private static @Nullable ModelPart scouter, rightArm, leftArm, leg;

	private FrostTechLayer() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?> && !(renderer.getModel() instanceof PlayerModel)) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: what this body wears (nothing for anyone but a grown villager or a patrolman). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		int worn = FrostTech.of(entity);
		state.setData(KEY, worn == 0 ? null : worn | FrostTech.kinds(entity) << 8);
	}

	private static ModelPart box(float x, float y, float z, float w, float h, float d, float inflate, int sheetW, int sheetH) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, w, h, d, new CubeDeformation(inflate)), PartPose.ZERO);
		return LayerDefinition.create(mesh, sheetW, sheetH).bakeRoot();
	}

	// each in its bone's own space, as the humanoid model's boxes are
	private static ModelPart scouter() {
		return scouter != null ? scouter : (scouter = box(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, SCOUTER_INFLATE, 64, 32));
	}

	private static ModelPart rightArm() {
		return rightArm != null ? rightArm : (rightArm = box(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, LIMB_INFLATE, 16, 16));
	}

	private static ModelPart leftArm() {
		return leftArm != null ? leftArm : (leftArm = box(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, LIMB_INFLATE, 16, 16));
	}

	private static ModelPart leg() {
		return leg != null ? leg : (leg = box(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, LIMB_INFLATE, 16, 16));
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Integer data = state.getData(KEY);
			if (data == null || state.isInvisible || state.getData(BioForms.KEY) != null) {
				return;
			}
			int worn = data & 0xFF, kinds = data >> 8;
			HumanoidModel<S> model = this.getParentModel();
			HumanoidModel<?> posed = model;
			if (McaCompat.LOADED) {
				// on MCA's skin body, in the pose MCA gives it (the Beastfolk's parts and the marks do the same)
				HumanoidModel<?> mca = McaLookModels.marks(model);
				if (mca != null) {
					posed = mca;
				}
			}
			if (POWERS && (worn & FrostTech.SCOUTER_BIT) != 0 && posed.head.visible && state.headEquipment.isEmpty() && !PowersCover.head(state)) {
				poseStack.pushPose();
				posed.head.translateAndRotate(poseStack);
				collector.order(1).submitModelPart(scouter(), poseStack, RenderTypes.entityCutout(SCOUTER), lightCoords, OverlayTexture.NO_OVERLAY, null, -1);
				poseStack.popPose();
			}
			if ((worn & FrostTech.LIMBS) == 0 || PowersCover.body(state)) {
				return;
			}
			limb(poseStack, collector, lightCoords, posed.rightArm, rightArm(), kinds & 3);
			limb(poseStack, collector, lightCoords, posed.leftArm, leftArm(), kinds >> 2 & 3);
			limb(poseStack, collector, lightCoords, posed.rightLeg, leg(), kinds >> 4 & 3);
			limb(poseStack, collector, lightCoords, posed.leftLeg, leg(), kinds >> 6 & 3);
		}

		/** One limb's stand-in, of its make. (Not asked whether the bone shows: a lost limb's is hidden, and this stands in for it.) */
		private static void limb(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart bone, ModelPart plating, int make) {
			if (make == FrostTech.NONE) {
				return;
			}
			Identifier sheet = make == FrostTech.WOOD ? WOOD : make == FrostTech.IRON ? IRON : LIMB;
			poseStack.pushPose();
			bone.translateAndRotate(poseStack);
			collector.order(1).submitModelPart(plating, poseStack, RenderTypes.entityCutout(sheet), light, OverlayTexture.NO_OVERLAY, null, -1);
			if (make == FrostTech.MADE) {
				collector.order(2).submitModelPart(plating, poseStack, RenderTypes.eyes(LIMB_GLOW), light, OverlayTexture.NO_OVERLAY, null, -1);
			}
			poseStack.popPose();
		}
	}
}
