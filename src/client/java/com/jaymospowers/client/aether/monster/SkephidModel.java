package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The skephid: a flat body, a blunt head and four splayed legs; it is drawn upside down when it hangs from a ceiling. */
final class SkephidModel extends AetherMonsterModel {
	private final ModelPart head, legFl, legFr, legBl, legBr;

	private SkephidModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.legFl = root.getChild("leg_fl");
		this.legFr = root.getChild("leg_fr");
		this.legBl = root.getChild("leg_bl");
		this.legBr = root.getChild("leg_br");
	}

	static SkephidModel create() {
		return new SkephidModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -4.0F, -1.5F, -3.0F, 8, 3, 6, 0.0F, 19.0F, 0.0F);
		part(r, "head", 28, 0, -2.5F, -2.0F, -3.0F, 5, 4, 3, 0.0F, 19.0F, -3.0F);
		part(r, "leg_fl", 44, 0, 0.0F, -0.5F, -0.5F, 6, 1, 1, 3.0F, 19.0F, -2.0F);
		part(r, "leg_fr", 0, 9, -6.0F, -0.5F, -0.5F, 6, 1, 1, -3.0F, 19.0F, -2.0F);
		part(r, "leg_bl", 14, 9, 0.0F, -0.5F, -0.5F, 6, 1, 1, 3.0F, 19.0F, 2.0F);
		part(r, "leg_br", 28, 9, -6.0F, -0.5F, -0.5F, 6, 1, 1, -3.0F, 19.0F, 2.0F);
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		look(this.head, state);
		this.head.xRot *= 0.6F;
		this.head.yRot *= 0.6F;
		// legs splay out and down, and scuttle in pairs
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float scuttle = Mth.cos(state.walkAnimationPos * 1.2F) * 0.45F * speed;
		this.legFl.zRot = 0.8F;
		this.legFr.zRot = -0.8F;
		this.legBl.zRot = 0.8F;
		this.legBr.zRot = -0.8F;
		this.legFl.yRot = scuttle;
		this.legBr.yRot = scuttle;
		this.legFr.yRot = -scuttle;
		this.legBl.yRot = -scuttle;
		// casting: rears its head and lifts its front legs
		if (state.action == 1) {
			this.head.xRot -= 0.5F;
			this.legFl.zRot = 0.2F + Mth.sin(t * 0.9F) * 0.1F;
			this.legFr.zRot = -0.2F - Mth.sin(t * 0.9F) * 0.1F;
		}
	}
}
