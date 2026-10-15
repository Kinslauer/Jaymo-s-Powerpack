package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

/**
 * A zombie player's body: the player model's shape (its jacket, sleeves and trousers layers included, slim or wide
 * arms), walking like a zombie with its arms held out in front. Not a PlayerModel: a player's render state would get
 * it drawn by the game's player renderer, as a plain player.
 */
public class ZombiePlayerModel extends HumanoidModel<ZombiePlayerRenderer.State> {
	/** Slightly bigger copies for the torn-clothes overlay, so it sits just over the skin. */
	public static final ModelLayerLocation TATTERS = new ModelLayerLocation(JaymosKitchen.id("zombie_player"), "tatters");
	public static final ModelLayerLocation TATTERS_SLIM = new ModelLayerLocation(JaymosKitchen.id("zombie_player"), "tatters_slim");

	public ZombiePlayerModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition tatters(boolean slim) {
		return LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.26F), slim), 64, 64);
	}

	@Override
	public void setupAnim(ZombiePlayerRenderer.State state) {
		super.setupAnim(state);
		armsOut(rightArm, leftArm, state.swingAnimation, state.ageInTicks);
	}

	/** As vanilla's zombies hold their arms. (0.4.44) Also for a zombie player drawn as their MCA villager. */
	public static void armsOut(ModelPart rightArm, ModelPart leftArm, float swingAnimation, float ageInTicks) {
		float attack = Mth.sin(swingAnimation * Mth.PI);
		float swing = Mth.sin((1.0F - (1.0F - swingAnimation) * (1.0F - swingAnimation)) * Mth.PI);
		rightArm.zRot = 0.0F;
		leftArm.zRot = 0.0F;
		rightArm.yRot = -(0.1F - attack * 0.6F);
		leftArm.yRot = 0.1F - attack * 0.6F;
		float out = -Mth.PI / 2.25F;
		rightArm.xRot = out + attack * 1.2F - swing * 0.4F;
		leftArm.xRot = out + attack * 1.2F - swing * 0.4F;
		AnimationUtils.bobArms(rightArm, leftArm, ageInTicks);
	}
}
