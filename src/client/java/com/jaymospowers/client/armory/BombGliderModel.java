package com.jaymospowers.client.armory;

import com.jaymospowers.JaymosPowers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * The bomb glider's model ({@code textures/entity/bomb_glider.png}, 128x64, from tools/gen_bomb_glider.py), our own. In
 * pixels, model space (y down, +z the nose): an iron deck 12 wide and 24 long with a pointed nose, foot straps on top, a
 * tail fin, the burner and its nozzle under the tail and a bundle of sticks slung under the middle. The bat wings are
 * parts of their own, hinged at the deck's edges (x = +-{@link #HINGE}), so the renderer can flap them.
 */
final class BombGliderModel {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/bomb_glider.png");
	/** Where each wing is hinged: the deck's edge (pixels from the middle), a little under its top. */
	static final float HINGE = 6.0F, HINGE_Y = 0.5F;

	private static ModelPart body, wingRight, wingLeft;

	private BombGliderModel() {
	}

	/** Deck, nose, straps, fin, burner, nozzle and the slung sticks. */
	static ModelPart body() {
		build();
		return body;
	}

	/** The wing reaching out along +x from its hinge. */
	static ModelPart wingRight() {
		build();
		return wingRight;
	}

	/** The wing reaching out along -x from its hinge (the same, mirrored). */
	static ModelPart wingLeft() {
		build();
		return wingLeft;
	}

	private static void build() {
		if (body != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-6.0F, 0.0F, -12.0F, 12.0F, 2.0F, 24.0F)
			.texOffs(0, 28).addBox(-4.0F, 0.0F, 12.0F, 8.0F, 2.0F, 4.0F)
			.texOffs(24, 28).addBox(-2.0F, 0.0F, 16.0F, 4.0F, 2.0F, 2.0F)
			.texOffs(40, 28).addBox(-0.5F, -6.0F, -12.0F, 1.0F, 6.0F, 6.0F)
			.texOffs(56, 28).addBox(-3.0F, 2.0F, -12.0F, 6.0F, 4.0F, 6.0F)
			.texOffs(80, 28).addBox(-2.0F, 2.5F, -14.0F, 4.0F, 3.0F, 2.0F)
			.texOffs(96, 28).addBox(-2.0F, 2.0F, 0.0F, 4.0F, 2.0F, 6.0F)
			.texOffs(0, 40).addBox(-3.0F, -1.0F, 2.0F, 6.0F, 1.0F, 1.0F)
			.texOffs(0, 40).addBox(-3.0F, -1.0F, -4.0F, 6.0F, 1.0F, 1.0F), PartPose.ZERO);
		root.addOrReplaceChild("wing_right", CubeListBuilder.create()
			.texOffs(0, 44).addBox(0.0F, -0.5F, -9.0F, 20.0F, 1.0F, 18.0F)
			.texOffs(80, 40).addBox(0.0F, -1.0F, 7.5F, 20.0F, 1.0F, 1.0F), PartPose.ZERO);
		root.addOrReplaceChild("wing_left", CubeListBuilder.create().mirror()
			.texOffs(0, 44).addBox(-20.0F, -0.5F, -9.0F, 20.0F, 1.0F, 18.0F)
			.texOffs(80, 40).addBox(-20.0F, -1.0F, 7.5F, 20.0F, 1.0F, 1.0F), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 128, 64).bakeRoot();
		body = baked.getChild("body");
		wingRight = baked.getChild("wing_right");
		wingLeft = baked.getChild("wing_left");
	}
}
