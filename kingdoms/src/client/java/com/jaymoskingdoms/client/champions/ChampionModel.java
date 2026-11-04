package com.jaymoskingdoms.client.champions;

import com.jaymoskingdoms.Patron;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * (0.7.518) A patron's champion, all our own: a man's body with each patron's extra parts (an apron, a beard, a veil, antlers, a
 * hood, a crown, a mitre) below the humanoid layout of its 64x64 texture ({@code tools/gen_champions.py}). Asleep it bows its
 * head and rests its arms on its weapon; casting it raises both arms (the Antlered Hunter and the Inquisitor throw one forward
 * for their first move); turning, its arms are out wide and its head back.
 */
final class ChampionModel extends HumanoidModel<ChampionRenderer.State> {
	private final Patron patron;

	ChampionModel(ModelPart root, Patron patron) {
		super(root);
		this.patron = patron;
	}

	private static void box(PartDefinition parent, String name, int u, int v, float x, float y, float z, int w, int h, int d, PartPose pose, boolean mirror) {
		CubeListBuilder cube = CubeListBuilder.create().texOffs(u, v);
		if (mirror) {
			cube = cube.mirror();
		}
		parent.addOrReplaceChild(name, cube.addBox(x, y, z, w, h, d), pose);
	}

	static LayerDefinition createBodyLayer(Patron patron) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head"), body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm"), leftArm = root.getChild("left_arm");
		switch (patron) {
			case HEARTHKEEPER -> {
				box(body, "apron", 0, 32, -4.0F, 0.0F, -3.0F, 8, 10, 1, PartPose.ZERO, false);
				box(head, "bun", 20, 32, -2.0F, -7.0F, 4.0F, 4, 3, 3, PartPose.ZERO, false);
			}
			case ANVIL_FATHER -> {
				box(head, "beard", 0, 32, -3.0F, -2.0F, -5.0F, 6, 6, 2, PartPose.ZERO, false);
				box(rightArm, "pauldron", 16, 32, -4.0F, -3.0F, -2.5F, 5, 3, 5, PartPose.ZERO, false);
				box(leftArm, "pauldron", 16, 32, -1.0F, -3.0F, -2.5F, 5, 3, 5, PartPose.ZERO, true);
			}
			case DEEP_MOTHER -> {
				box(head, "veil", 0, 32, -4.5F, -9.0F, 3.5F, 9, 9, 1, PartPose.ZERO, false);
				box(body, "sash", 24, 32, -1.0F, 0.0F, -3.0F, 2, 12, 1, PartPose.ZERO, false);
			}
			case GREEN_HUNTSMAN -> {
				box(head, "right_beam", 0, 32, -1.0F, -6.0F, -1.0F, 2, 6, 2, PartPose.offsetAndRotation(-3.0F, -8.0F, 0.0F, 0.0F, 0.0F, -0.3F), false);
				box(head, "right_tine", 8, 32, -1.0F, -2.0F, -1.0F, 2, 2, 2, PartPose.offsetAndRotation(-4.5F, -13.0F, 0.0F, 0.0F, 0.0F, 0.5F), false);
				box(head, "left_beam", 0, 32, -1.0F, -6.0F, -1.0F, 2, 6, 2, PartPose.offsetAndRotation(3.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.3F), true);
				box(head, "left_tine", 8, 32, -1.0F, -2.0F, -1.0F, 2, 2, 2, PartPose.offsetAndRotation(4.5F, -13.0F, 0.0F, 0.0F, 0.0F, -0.5F), true);
				box(body, "collar", 0, 44, -5.0F, -1.0F, -3.0F, 10, 2, 6, PartPose.ZERO, false);
			}
			case LANTERN_WARDEN -> {
				box(head, "hood", 0, 32, -4.5F, -8.5F, -4.5F, 9, 9, 9, PartPose.ZERO, false);
				box(body, "lantern", 40, 32, 3.5F, 8.0F, -1.5F, 3, 4, 3, PartPose.ZERO, false);
			}
			case CROWNED_SUN -> {
				box(head, "band", 0, 32, -4.5F, -9.0F, -4.5F, 9, 1, 9, PartPose.ZERO, false);
				int[][] rays = {{-4, -4}, {3, -4}, {-4, 3}, {3, 3}, {0, 0}};
				for (int i = 0; i < rays.length; i++) {
					box(head, "ray" + i, 40, 32, rays[i][0] - 0.5F, -12.0F, rays[i][1] - 0.5F, 1, 3, 1, PartPose.ZERO, false);
				}
				box(body, "cape", 0, 44, -5.0F, 0.0F, 2.5F, 10, 16, 1, PartPose.ZERO, false);
			}
			case ASH_WHISPERER -> {
				box(head, "hood", 0, 32, -4.5F, -8.5F, -4.5F, 9, 9, 9, PartPose.ZERO, false);
				box(body, "scarf", 40, 32, -1.0F, 0.0F, -2.5F, 2, 8, 1, PartPose.ZERO, false);
			}
			case TRUTH -> {
				box(head, "mitre", 0, 32, -3.0F, -14.0F, -2.0F, 6, 6, 4, PartPose.ZERO, false);
				box(body, "stole_right", 24, 32, -3.0F, 0.0F, -3.0F, 2, 14, 1, PartPose.ZERO, false);
				box(body, "stole_left", 24, 32, 1.0F, 0.0F, -3.0F, 2, 14, 1, PartPose.ZERO, true);
			}
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(ChampionRenderer.State state) {
		super.setupAnim(state);
		if (state.dormant) {
			this.head.xRot = 0.6F;
			this.hat.xRot = 0.6F;
			this.rightArm.xRot = -0.4F;
			this.leftArm.xRot = -0.4F;
			this.rightArm.yRot = -0.2F;
			this.leftArm.yRot = 0.2F;
			return;
		}
		if (state.turning) {
			this.rightArm.zRot = 1.2F;
			this.leftArm.zRot = -1.2F;
			this.rightArm.xRot = 0.0F;
			this.leftArm.xRot = 0.0F;
			this.head.xRot = -0.5F;
			this.hat.xRot = -0.5F;
			return;
		}
		if (state.casting >= 0) {
			boolean throwArm = state.casting == 0 && (this.patron == Patron.GREEN_HUNTSMAN || this.patron == Patron.TRUTH);
			this.rightArm.xRot = throwArm ? -1.6F : -2.6F;
			if (!throwArm) {
				this.leftArm.xRot = -2.6F;
			}
		}
	}
}
