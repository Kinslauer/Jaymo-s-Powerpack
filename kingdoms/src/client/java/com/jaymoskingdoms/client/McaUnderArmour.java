package com.jaymoskingdoms.client;

import net.conczin.mca.Config;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.484) The under-armour suit ({@link UnderArmourLayer}) on a body MCA draws (every player, MCA's villagers and our
 * folk). Andy (2026-11-03): "the skins are clipping ... a similar issue with the MCA drawing layer". The suit was drawn on
 * the vanilla-sized player model, 0.3 out: MCA's clothes sit at 0.0625 with their jacket and sleeve layer 0.25 past that
 * (0.3125), so the doublet and the shirt fought pixel for pixel, and MCA's body is stretched to the wearer's height, width
 * and heritage build ({@code VillagerDimensions}) where the suit wasn't. Here it is drawn as MCA draws a chestplate on
 * those bodies ({@code MixinHumanoidArmorLayer}): on MCA's armour model, which takes the wearer's dimensions, breasts and
 * MCA's own pose, just past the clothes ({@link #SIT}) and still under the leggings' belt (0.5). Only touched with MCA.
 */
final class McaUnderArmour {
	/** Past MCA's clothes and their jacket layer (0.3125), under the leggings (0.5). */
	static final float SIT = 0.4F;

	private static @Nullable Suit suit;

	private McaUnderArmour() {
	}

	/** The suit model for {@code state} when MCA is drawing that body (posed as it's drawn); null for any other body. */
	static @Nullable HumanoidModel<HumanoidRenderState> model(HumanoidRenderState state) {
		if (!(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
			return null;
		}
		if (suit == null) {
			suit = new Suit(LayerDefinition.create(mesh(), 64, 32).bakeRoot());
		}
		return suit;
	}

	/** A humanoid at {@link #SIT} with MCA's breasts (its armour model needs the part), as MCA builds its chestplate. */
	private static MeshDefinition mesh() {
		CubeDeformation sit = new CubeDeformation(SIT);
		MeshDefinition mesh = HumanoidModel.createMesh(sit, 0.0F);
		CubeListBuilder breasts = CubeListBuilder.create();
		if (Config.getInstance().enableBoobs) {
			// VillagerEntityBaseModelMCA.newBreasts(deformation, 0), on the chest's own texture
			breasts.texOffs(18, 21).addBox(-3.25F, -1.25F, -1.5F, 6.0F, 3.0F, 3.0F, sit);
		}
		mesh.getRoot().addOrReplaceChild("breasts", breasts, PartPose.ZERO);
		return mesh;
	}

	/** Body, arms and breasts only. */
	private static final class Suit extends PlayerArmorExtendedModel<net.minecraft.world.entity.LivingEntity> {
		Suit(ModelPart root) {
			super(root);
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			this.head.visible = false;
			this.hat.visible = false;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
		}
	}
}
