package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The maggots, from {@code MoCModelMaggot}: its boxes (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}),
 * ported to 26.3 by {@code tools/gen_moc_small.py}, and its body stretching as it crawls.
 * <p>Andy: "the maggot entity always drops maggots plural, the entity should be drawn as multiple maggots, so the effect
 * is more intense than the single large one." One maggot entity is a heap of {@link #HEAP} small ones, a few lying over the
 * others, each writhing on its own beat: heads lifting and swinging, tails flicking, bodies stretching and bunching, the
 * heap slowly churning about itself. They all crawl together when it moves.
 */
final class MaggotModel extends EntityModel<BeastState> {
	// ================================================================================================================
	// Tuning
	/** Each maggot in the heap: where it lies (pixels from the middle, x and z), which way it faces (degrees), how far
	 * it lies up on the others (pixels), and its size (of the old single maggot). Half a block across in all. */
	private static final float[][] HEAP = {
		{-2.6F, -1.4F, 70.0F, 0.0F, 0.45F},
		{2.3F, -2.1F, -40.0F, 0.0F, 0.42F},
		{-1.9F, 2.4F, 150.0F, 0.0F, 0.47F},
		{2.7F, 1.7F, -120.0F, 0.0F, 0.40F},
		{0.2F, -3.1F, 95.0F, 0.0F, 0.38F},
		{0.4F, 0.2F, 15.0F, 1.1F, 0.46F},
		{-0.6F, 1.1F, -75.0F, 1.2F, 0.40F},
	};
	/** How hard they squirm: the bunch and stretch of a body, the swing of a head or tail (radians), the head's lift
	 * (radians), the slow turn of each about its spot (radians) and its drift (pixels). */
	private static final float SQUIRM = 0.16F, SWING = 0.5F, LIFT = 0.45F, TURN = 0.45F, DRIFT = 0.6F;
	// ================================================================================================================

	private final ModelPart[] maggot = new ModelPart[HEAP.length];
	private final ModelPart[] head = new ModelPart[HEAP.length];
	private final ModelPart[] tail = new ModelPart[HEAP.length];
	private final ModelPart[] tip = new ModelPart[HEAP.length];

	MaggotModel(ModelPart root) {
		super(root);
		for (int i = 0; i < HEAP.length; i++) {
			this.maggot[i] = root.getChild("maggot" + i);
			this.head[i] = this.maggot[i].getChild("Head");
			this.tail[i] = this.maggot[i].getChild("Tail");
			this.tip[i] = this.tail[i].getChild("Tailtip");
		}
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int i = 0; i < HEAP.length; i++) {
			float[] m = HEAP[i];
			// (each maggot's own pivot is on the ground under its middle, so it shrinks and turns where it lies)
			PartDefinition one = root.addOrReplaceChild("maggot" + i, CubeListBuilder.create(),
				PartPose.offsetAndRotation(m[0], 24.0F - m[3], m[1], 0.0F, m[2] * Mth.DEG_TO_RAD, 0.0F));
			one.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 11).addBox(-1F, -1F, -2F, 2F, 2F, 2F), PartPose.offset(0F, -1F, -2F));
			one.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2F, 0F, 3F, 3F, 4F), PartPose.offset(0F, -1F, -2F));
			PartDefinition tail = one.addOrReplaceChild("Tail", CubeListBuilder.create().texOffs(0, 7).addBox(-1F, -1F, 0F, 2F, 2F, 2F), PartPose.offset(0F, -1F, 2F));
			tail.addOrReplaceChild("Tailtip", CubeListBuilder.create().texOffs(8, 7).addBox(-0.5F, 0F, 0F, 1F, 1F, 1F), PartPose.offset(0F, 0F, 2F));
		}
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		float t = state.ageInTicks, walk = state.walkAnimationPos, speed = state.walkAnimationSpeed;
		for (int i = 0; i < HEAP.length; i++) {
			float[] m = HEAP[i];
			float beat = i * 1.7F, size = m[4];
			// the old maggot's crawl, each on its own beat, over a squirm of its own when it lies still
			float stretch = 1.0F - Mth.cos(walk * 3.0F + beat) * speed * 2.0F - Mth.cos(t * 0.45F + beat) * SQUIRM;
			ModelPart one = this.maggot[i];
			one.xScale = size;
			one.yScale = size;
			one.zScale = size * stretch;
			one.yRot = m[2] * Mth.DEG_TO_RAD + Mth.sin(t * 0.11F + beat) * TURN;
			one.x = m[0] + Mth.sin(t * 0.05F + beat) * DRIFT;
			one.z = m[1] + Mth.cos(t * 0.06F + beat * 1.3F) * DRIFT;
			this.head[i].yRot = Mth.sin(t * 0.6F + beat) * SWING;
			this.head[i].xRot = -Math.max(0.0F, Mth.sin(t * 0.27F + beat)) * LIFT;
			this.tail[i].yRot = -Mth.sin(t * 0.6F + beat + 0.8F) * SWING;
			this.tip[i].yRot = -Mth.sin(t * 0.6F + beat + 1.6F) * SWING;
		}
	}
}
