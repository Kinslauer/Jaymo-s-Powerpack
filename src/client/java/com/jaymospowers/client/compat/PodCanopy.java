package com.jaymospowers.client.compat;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Andy (2026-11-25): "you should be sitting in that cockpit, and there needs to be a glass enclosure over it. maybe it
 * animates tilting up then back down after you enter." Our own glass canopy over the submarine's open cockpit
 * ({@link PodSubmarine}): a stepped bubble in slate frames with a riveted rib, hinged at the top of the hull behind the
 * pilot, so it tilts up to let them in and shuts after. A damaged canopy is drawn cracked. Its boxes are in the
 * submarine's hull space measured from the hinge; tools/gen_pod_canopy.py writes the table below and paints the sheets,
 * so change the shape there.
 */
final class PodCanopy {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/space_pod_canopy.png");
	private static final Identifier CRACKED = JaymosPowers.id("textures/entity/space_pod_canopy_cracked.png");
	/** The hinge, in the submarine's hull space (pixels): the top edge of the hull's core, just behind the pilot's head. */
	static final float HINGE_X = 0.0F, HINGE_Y = -21.0F, HINGE_Z = -7.0F;

	/** {u, v, x, y, z, w, h, d}, from the hinge. */
	private static final float[][] CUBES = {
		// cubes:start
		{0, 0, -19F, 15F, -29F, 38F, 14F, 29F},
		{134, 0, -16F, 5F, -26F, 32F, 10F, 26F},
		{0, 43, -11F, 0F, -21F, 22F, 5F, 21F},
		{86, 43, -19.5F, 15F, -16F, 2.0F, 14F, 2F},
		{94, 43, 17.5F, 15F, -16F, 2.0F, 14F, 2F},
		{102, 43, -19.5F, 14F, -16F, 4.0F, 2F, 2F},
		{114, 43, 15.5F, 14F, -16F, 4.0F, 2F, 2F},
		{126, 43, -16.5F, 5F, -16F, 2.0F, 10F, 2F},
		{134, 43, 14.5F, 5F, -16F, 2.0F, 10F, 2F},
		{142, 43, -16.5F, 4F, -16F, 6.0F, 2F, 2F},
		{158, 43, 10.5F, 4F, -16F, 6.0F, 2F, 2F},
		{174, 43, -11.5F, 0F, -16F, 2.0F, 5F, 2F},
		{182, 43, 9.5F, 0F, -16F, 2.0F, 5F, 2F},
		{190, 43, -11.5F, -1F, -16F, 23.0F, 2F, 2F},
		{0, 69, -19.5F, 27F, -30F, 39.0F, 2F, 2F},
		// cubes:end
	};

	private static ModelPart part;

	private PodCanopy() {
	}

	/**
	 * Queues the canopy, tilted up {@code open} degrees about its hinge. {@code poseStack} is at the submarine's hull pivot,
	 * in its model space (pixels to the block already scaled by the caller, y down).
	 */
	static void submit(SubmitNodeCollector collector, PoseStack poseStack, int light, float open, boolean cracked) {
		if (part == null) {
			part = build();
		}
		poseStack.pushPose();
		poseStack.translate(HINGE_X / 16.0F, HINGE_Y / 16.0F, HINGE_Z / 16.0F);
		// y is down and the front is -z: a negative turn about x lifts the front
		poseStack.rotateDegrees(Axis.XP, -open);
		collector.submitModelPart(part, poseStack, RenderTypes.entityTranslucent(cracked ? CRACKED : TEXTURE), light,
			OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}

	private static ModelPart build() {
		CubeListBuilder cubes = CubeListBuilder.create();
		for (float[] c : CUBES) {
			cubes.texOffs((int)c[0], (int)c[1]).addBox(c[2], c[3], c[4], c[5], c[6], c[7]);
		}
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("canopy", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, 256, 128).bakeRoot().getChild("canopy");
	}
}
