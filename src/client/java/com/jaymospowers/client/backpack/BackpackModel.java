package com.jaymospowers.client.backpack;

import com.jaymospowers.JaymosPowers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * The backpack's model ({@code textures/entity/backpack.png}, from tools/gen_backpack.py), the same worn and set down.
 * In pixels, from the top of the bag where it rests against the back (+z away from the wearer): a leather bag 8 wide,
 * 10 tall and 4 deep, a buckled pocket on its outer face, a pocket on each side, a bedroll strapped under it, and a
 * flap hinged across the top that lifts and drops as it bounces. The shoulder straps are a part of their own, drawn
 * only when worn.
 * <p>
 * Models are drawn after every renderer has submitted, so these shared parts are never posed: each one is placed by
 * the pose stack it is submitted with.
 */
final class BackpackModel {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/backpack.png");
	/** How far the bag reaches below its top, bedroll included (pixels), and how deep it is with its pocket. */
	static final float HEIGHT = 13.0F;
	static final float DEPTH = 6.0F;

	private static ModelPart bag, flap, straps;

	private BackpackModel() {
	}

	/** The bag with its pockets and bedroll. */
	static ModelPart bag() {
		build();
		return bag;
	}

	/** The flap, hinged at its origin across the top of the bag (turning it about x negatively lifts it). */
	static ModelPart flap() {
		build();
		return flap;
	}

	/** The straps over the shoulders and down the chest, in the body's space. */
	static ModelPart straps() {
		build();
		return straps;
	}

	private static void build() {
		if (bag != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("bag", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 10.0F, 4.0F)
			.texOffs(28, 0).addBox(-2.5F, 5.0F, 4.0F, 5.0F, 4.0F, 2.0F)
			.texOffs(44, 0).addBox(4.0F, 4.0F, 0.5F, 2.0F, 5.0F, 3.0F)
			.texOffs(44, 0).mirror().addBox(-6.0F, 4.0F, 0.5F, 2.0F, 5.0F, 3.0F).mirror(false)
			.texOffs(0, 16).addBox(-5.0F, 10.0F, 0.5F, 10.0F, 3.0F, 3.0F), PartPose.ZERO);
		root.addOrReplaceChild("flap", CubeListBuilder.create()
			.texOffs(0, 24).addBox(-4.5F, -1.0F, -0.25F, 9.0F, 1.0F, 5.0F)
			.texOffs(0, 32).addBox(-4.5F, 0.0F, 3.75F, 9.0F, 4.0F, 1.0F)
			.texOffs(22, 32).addBox(-1.0F, 2.5F, 4.5F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
		root.addOrReplaceChild("straps", CubeListBuilder.create()
			.texOffs(32, 24).addBox(1.5F, -1.0F, -2.5F, 2.0F, 1.0F, 5.0F)
			.texOffs(32, 24).mirror().addBox(-3.5F, -1.0F, -2.5F, 2.0F, 1.0F, 5.0F).mirror(false)
			.texOffs(32, 32).addBox(1.5F, 0.0F, -3.0F, 2.0F, 5.0F, 1.0F)
			.texOffs(32, 32).mirror().addBox(-3.5F, 0.0F, -3.0F, 2.0F, 5.0F, 1.0F).mirror(false), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		bag = baked.getChild("bag");
		flap = baked.getChild("flap");
		straps = baked.getChild("straps");
	}
}
