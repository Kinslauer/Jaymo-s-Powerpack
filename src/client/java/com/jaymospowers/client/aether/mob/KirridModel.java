package com.jaymospowers.client.aether.mob;

import com.jaymospowers.aether.mob.Zone;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The kirrid: a long-bodied hopper in a shell of wool, on a raised neck with a tall plate across its face (the plate and
 * the wool come and go; the arctic kind has a pair of long horns). It tucks its legs in the air and grazes with its head down.
 */
final class KirridModel extends AetherAnimalModel {
	/** The neck rises this far (radians); grazing lowers it. */
	private static final float NECK_UP = -0.55F, NECK_GRAZE = 1.0F;

	private final ModelPart neck, head, wool, tuft, plate, hornL, hornR, legFr, legFl, legBr, legBl, tail;

	private KirridModel(ModelPart root) {
		super(root);
		this.neck = root.getChild("neck");
		this.head = this.neck.getChild("head");
		this.wool = root.getChild("wool");
		this.tuft = this.head.getChild("tuft");
		this.plate = this.head.getChild("plate");
		this.hornL = this.head.getChild("horn_l");
		this.hornR = this.head.getChild("horn_r");
		this.legFr = root.getChild("leg_fr");
		this.legFl = root.getChild("leg_fl");
		this.legBr = root.getChild("leg_br");
		this.legBl = root.getChild("leg_bl");
		this.tail = root.getChild("tail");
	}

	static KirridModel create() {
		return new KirridModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -5F, -4F, -9F, 10, 8, 18, 0F, 12F, 0F);
		part(r, "wool", 0, 26, -6.5F, -6F, -7F, 13, 12, 14, 0F, 12F, 0F);
		PartDefinition neck = part(r, "neck", 0, 52, -2F, -2F, -6F, 4, 4, 6, 0F, 11F, -8F);
		PartDefinition head = part(neck, "head", 22, 52, -3F, -3F, -5F, 6, 6, 5, 0F, 0F, -6F);
		part(head, "plate", 44, 52, -4F, -5F, -7F, 8, 11, 2, 0F, 0F, 0F);
		part(head, "horn_l", 64, 52, 3F, -5F, -4F, 1, 2, 9, 0F, 0F, 0F);
		part(head, "horn_r", 84, 52, -4F, -5F, -4F, 1, 2, 9, 0F, 0F, 0F);
		part(head, "tuft", 104, 52, -3F, -6F, -4F, 6, 3, 5, 0F, 0F, 0F);
		part(r, "leg_fr", 0, 66, -1.5F, 0F, -2F, 3, 9, 4, -3.5F, 15F, -6F);
		part(r, "leg_fl", 14, 66, -1.5F, 0F, -2F, 3, 9, 4, 3.5F, 15F, -6F);
		part(r, "leg_br", 28, 66, -2F, 0F, -2.5F, 4, 9, 5, -3.5F, 15F, 6F);
		part(r, "leg_bl", 46, 66, -2F, 0F, -2.5F, 4, 9, 5, 3.5F, 15F, 6F);
		part(r, "tail", 64, 66, -1F, -1F, 0F, 2, 2, 4, 0F, 10F, 9F);
		return LayerDefinition.create(mesh, 128, 80);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		this.neck.xRot = Mth.lerp(state.eating, NECK_UP, NECK_GRAZE + Mth.sin(state.ageInTicks * 0.8F) * 0.1F);
		this.head.xRot = state.xRot * DEG * (1.0F - state.eating) - this.neck.xRot * 0.6F;
		this.head.yRot = state.yRot * DEG;
		if (state.air > 0.0F && state.walkAnimationSpeed > 0.1F) {
			// a hop: legs reach forward and back
			this.legFr.xRot = -0.7F;
			this.legFl.xRot = -0.7F;
			this.legBr.xRot = 0.7F;
			this.legBl.xRot = 0.7F;
		} else {
			swing(this.legFr, this.legBl, state, 0.6F);
			swing(this.legFl, this.legBr, state, 0.6F);
		}
		this.tail.xRot = 0.5F;
		this.wool.visible = !state.sheared;
		this.tuft.visible = !state.sheared;
		this.plate.visible = state.plated;
		this.hornL.visible = state.zone == Zone.ARCTIC.ordinal();
		this.hornR.visible = this.hornL.visible;
	}
}
