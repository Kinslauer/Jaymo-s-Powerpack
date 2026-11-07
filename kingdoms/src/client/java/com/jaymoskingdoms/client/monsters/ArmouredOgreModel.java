package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * (0.7.623, Rivals patch 8) The armoured ogre: the ogre ({@link OgreModel}) in our own plate (a breastplate, a fauld,
 * pauldrons, vambraces, greaves and an open helm with a nasal) and a great wooden shield on its left forearm, raised
 * before it while its guard is up. Its skins are 128x256: the ogre's own on top, recoloured to an orc tone, and the plate
 * and shield below ({@code tools/armoured_ogre_assets.py}, whose UVs these must match).
 */
final class ArmouredOgreModel extends OgreModel {
	/** The guard: how far the shield arm comes forward, the forearm back up, and the arm across the body (radians). */
	static final float GUARD_LIFT = -0.65F, GUARD_FOREARM = 0.55F, GUARD_ACROSS = 0.5F;

	private final ModelPart lftShoulder, lftHand, shield;

	ArmouredOgreModel(ModelPart root) {
		super(root);
		this.lftShoulder = root.getChild("lft_shoulder");
		this.lftHand = this.lftShoulder.getChild("lft_arm").getChild("lft_hand");
		this.shield = this.lftHand.getChild("shield");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = OgreModel.mesh();
		PartDefinition root = mesh.getRoot();
		plate(root.getChild("chest"), "breastplate", 0, 128, -9.5F, -17.8F, -7.3F, 19, 11, 13, 0.6F);
		plate(root.getChild("stomach"), "fauld", 0, 152, -11, -8, -6, 22, 11, 14, 0.5F);
		PartDefinition lftShoulder = root.getChild("lft_shoulder");
		PartDefinition rgtShoulder = root.getChild("rgt_shoulder");
		plate(lftShoulder, "pauldron", 64, 128, 0, -3, -4, 8, 7, 8, 1.0F);
		plate(rgtShoulder, "pauldron", 96, 128, 0, -3, -4, 8, 7, 8, 1.0F);
		PartDefinition lftHand = lftShoulder.getChild("lft_arm").getChild("lft_hand");
		plate(lftHand, "vambrace", 72, 152, -4, 0, -4, 8, 12, 8, 0.4F);
		plate(rgtShoulder.getChild("rgt_arm").getChild("rgt_hand"), "vambrace", 72, 172, -4, 0, -4, 8, 12, 8, 0.4F);
		plate(root.getChild("lft_thigh").getChild("lft_leg"), "greave", 0, 177, -4, -1, -4, 8, 11, 8, 0.4F);
		plate(root.getChild("rgt_thigh").getChild("rgt_leg"), "greave", 32, 177, -4, -1, -4, 8, 11, 8, 0.4F);
		PartDefinition head = root.getChild("head");
		plate(head, "helm", 0, 196, -6, -12.6F, -6, 12, 5, 12, 0.6F);
		plate(head, "nasal", 48, 196, -1, -8.5F, -7.4F, 2, 4, 1, 0.0F);
		// the shield, on the forearm's front; its boss in the middle
		PartDefinition shield = lftHand.addOrReplaceChild("shield", CubeListBuilder.create().texOffs(0, 213).addBox(-8, -4, -7, 16, 22, 2),
			PartPose.ZERO);
		shield.addOrReplaceChild("boss", CubeListBuilder.create().texOffs(36, 213).addBox(-2, 5, -9, 4, 4, 2), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 256);
	}

	private static void plate(PartDefinition on, String name, int u, int v, float x, float y, float z, int w, int h, int d, float grow) {
		on.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d, new CubeDeformation(grow)), PartPose.ZERO);
	}

	@Override
	public void setupAnim(LegendState state) {
		state.twoHeads = false;
		super.setupAnim(state);
		this.shield.visible = state.shield;
		if (state.shield && state.guard && (state.arm & 1) == 0) {
			// the shield held up before it
			this.lftShoulder.xRot = GUARD_LIFT;
			this.lftShoulder.yRot = GUARD_ACROSS;
			this.lftShoulder.zRot = 0.0F;
			this.lftHand.xRot = GUARD_FOREARM;
		}
	}
}
