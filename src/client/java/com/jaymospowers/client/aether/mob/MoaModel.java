package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The moa: a tall flightless bird on two long legs, with a long neck, a hooked beak, a crest, a tail fan and two
 * stubby wings that beat when it is in the air (a jump, a glide). Saddled it wears a saddle on its back.
 */
final class MoaModel extends AetherAnimalModel {
	private static final float NECK_FORWARD = 0.3F;

	private final ModelPart neck, head, legL, legR, wingL, wingR, tail, saddle;

	private MoaModel(ModelPart root) {
		super(root);
		this.neck = root.getChild("neck");
		this.head = this.neck.getChild("head");
		this.legL = root.getChild("leg_l");
		this.legR = root.getChild("leg_r");
		this.wingL = root.getChild("wing_l");
		this.wingR = root.getChild("wing_r");
		this.tail = root.getChild("tail");
		this.saddle = root.getChild("saddle");
	}

	static MoaModel create() {
		return new MoaModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 13, -4F, -4F, -6F, 8, 8, 12, 0F, 8F, 0F);
		PartDefinition neck = part(r, "neck", 36, 0, -1.5F, -10F, -1.5F, 3, 10, 3, 0F, 5F, -5F);
		PartDefinition head = part(neck, "head", 0, 0, -2F, -4F, -3F, 4, 4, 5, 0F, -10F, 0F);
		part(head, "beak", 18, 0, -1F, -2F, -6F, 2, 2, 3, 0F, 0F, 0F);
		part(head, "crest", 28, 0, -1F, -7F, 0F, 2, 3, 1, 0F, 0F, 0F);
		part(r, "tail", 50, 0, -3F, -1F, 0F, 6, 2, 5, 0F, 6F, 6F);
		PartDefinition legL = part(r, "leg_l", 0, 33, -1F, 0F, -1F, 2, 14, 2, 2.5F, 10F, 0F);
		PartDefinition legR = part(r, "leg_r", 8, 33, -1F, 0F, -1F, 2, 14, 2, -2.5F, 10F, 0F);
		part(legL, "foot_l", 16, 33, -1.5F, 13F, -3F, 3, 1, 4, 0F, 0F, 0F);
		part(legR, "foot_r", 30, 33, -1.5F, 13F, -3F, 3, 1, 4, 0F, 0F, 0F);
		part(r, "wing_l", 74, 0, 0F, 0F, -3F, 1, 5, 7, 4F, 5F, 0F);
		part(r, "wing_r", 90, 0, -1F, 0F, -3F, 1, 5, 7, -4F, 5F, 0F);
		part(r, "saddle", 40, 13, -4.5F, -4.5F, -3F, 9, 9, 6, 0F, 8F, 0F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		// the neck leans forward; the head stays level and follows the gaze
		this.neck.xRot = NECK_FORWARD;
		this.head.xRot = state.xRot * DEG - NECK_FORWARD;
		this.head.yRot = state.yRot * DEG;
		swing(this.legL, this.legR, state, 1.2F);
		// in the air the legs trail
		this.legL.xRot += state.flapSpeed * 0.5F;
		this.legR.xRot += state.flapSpeed * 0.5F;
		this.tail.xRot = 0.4F;
		// the wings hang at its sides and beat in the air
		float beat = Mth.sin(state.flap) * state.flapSpeed * 0.9F;
		this.wingL.zRot = -0.1F - beat;
		this.wingR.zRot = 0.1F + beat;
		this.saddle.visible = state.saddled;
	}
}
