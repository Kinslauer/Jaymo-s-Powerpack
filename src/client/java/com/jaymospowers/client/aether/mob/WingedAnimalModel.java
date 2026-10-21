package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The phyg and the flying cow: a four-legged body with a pair of wings that lie folded at its sides on the ground and
 * open and beat in the air (as fast as {@code state.flap} runs), and a saddle when it wears one.
 */
final class WingedAnimalModel extends AetherAnimalModel {
	/** Wing tip angle (radians, up is positive): folded against the side, spread, and how far a beat swings. */
	private static final float FOLDED = -1.25F, SPREAD = 0.05F, BEAT = 0.65F;

	private final ModelPart head, legFr, legFl, legBr, legBl, wingL, wingR, saddle, tail;

	private WingedAnimalModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.legFr = root.getChild("leg_fr");
		this.legFl = root.getChild("leg_fl");
		this.legBr = root.getChild("leg_br");
		this.legBl = root.getChild("leg_bl");
		this.wingL = root.getChild("wing_l");
		this.wingR = root.getChild("wing_r");
		this.saddle = root.getChild("saddle");
		this.tail = root.getChild("tail");
	}

	static WingedAnimalModel phyg() {
		return new WingedAnimalModel(phygLayer().bakeRoot());
	}

	static WingedAnimalModel cow() {
		return new WingedAnimalModel(cowLayer().bakeRoot());
	}

	private static LayerDefinition phygLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		PartDefinition head = part(r, "head", 0, 0, -4F, -4F, -6F, 8, 8, 6, 0F, 12F, -7F);
		part(head, "snout", 28, 0, -2F, 0F, -7F, 4, 3, 1, 0F, 0F, 0F);
		part(head, "ear_l", 48, 0, 1F, -6F, -3F, 2, 2, 1, 0F, 0F, 0F);
		part(head, "ear_r", 54, 0, -3F, -6F, -3F, 2, 2, 1, 0F, 0F, 0F);
		part(r, "body", 0, 14, -4F, -4F, -7F, 8, 8, 14, 0F, 14F, 0F);
		part(r, "leg_fr", 0, 36, -2F, 0F, -2F, 4, 6, 4, -3F, 18F, -5F);
		part(r, "leg_fl", 16, 36, -2F, 0F, -2F, 4, 6, 4, 3F, 18F, -5F);
		part(r, "leg_br", 32, 36, -2F, 0F, -2F, 4, 6, 4, -3F, 18F, 5F);
		part(r, "leg_bl", 48, 36, -2F, 0F, -2F, 4, 6, 4, 3F, 18F, 5F);
		part(r, "tail", 40, 0, -1F, 0F, 0F, 2, 2, 2, 0F, 11F, 7F);
		part(r, "saddle", 0, 46, -4.5F, -4.5F, -3F, 9, 9, 6, 0F, 14F, -1F);
		part(r, "wing_l", 64, 0, 0F, -1F, -4F, 10, 1, 8, 4F, 11F, 0F);
		part(r, "wing_r", 64, 10, -10F, -1F, -4F, 10, 1, 8, -4F, 11F, 0F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	private static LayerDefinition cowLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		PartDefinition head = part(r, "head", 0, 0, -4F, -4F, -6F, 8, 8, 6, 0F, 8F, -9F);
		part(head, "muzzle", 36, 0, -3F, 1F, -7F, 6, 3, 1, 0F, 0F, 0F);
		part(head, "horn_l", 28, 0, 3F, -6F, -2F, 1, 3, 1, 0F, 0F, 0F);
		part(head, "horn_r", 32, 0, -4F, -6F, -2F, 1, 3, 1, 0F, 0F, 0F);
		part(r, "body", 0, 14, -5F, -5F, -8F, 10, 10, 16, 0F, 11F, 0F);
		part(r, "leg_fr", 0, 40, -2F, 0F, -2F, 4, 8, 4, -3F, 16F, -6F);
		part(r, "leg_fl", 16, 40, -2F, 0F, -2F, 4, 8, 4, 3F, 16F, -6F);
		part(r, "leg_br", 32, 40, -2F, 0F, -2F, 4, 8, 4, -3F, 16F, 6F);
		part(r, "leg_bl", 48, 40, -2F, 0F, -2F, 4, 8, 4, 3F, 16F, 6F);
		part(r, "udder", 64, 0, -1.5F, 0F, -2F, 3, 3, 4, 0F, 16F, 1F);
		part(r, "tail", 80, 0, -1F, 0F, 0F, 2, 9, 1, 0F, 7F, 8F);
		part(r, "saddle", 0, 52, -5.5F, -5.5F, -3F, 11, 11, 6, 0F, 11F, -1F);
		part(r, "wing_l", 64, 16, 0F, -1F, -4.5F, 12, 1, 9, 5F, 7F, 0F);
		part(r, "wing_r", 64, 26, -12F, -1F, -4.5F, 12, 1, 9, -5F, 7F, 0F);
		return LayerDefinition.create(mesh, 128, 80);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		look(this.head, state);
		swing(this.legFr, this.legBl, state, 1.4F);
		swing(this.legFl, this.legBr, state, 1.4F);
		// the hind legs trail behind it in the air
		float air = state.flapSpeed;
		this.legBr.xRot += air * 0.5F;
		this.legBl.xRot += air * 0.5F;
		this.tail.xRot = 0.5F + Mth.sin(state.ageInTicks * 0.2F) * 0.05F;
		// up is positive for a wing tip; the left wing turns the other way round the z axis from the right
		float up = Mth.lerp(air, FOLDED, SPREAD) + Mth.sin(state.flap) * air * BEAT;
		this.wingL.zRot = -up;
		this.wingR.zRot = up;
		this.saddle.visible = state.saddled;
	}
}
