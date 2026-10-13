package com.jaymoskingdoms.client.wild;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * What the wild beasts' models share (Better Animals Plus's {@code ModelBAP} helpers, in 26.3 terms): parts by name, a
 * quadruped's walk, head turns, and the slow wiggles. Each starts from its resting pose ({@link #setupAnim} resets it).
 */
abstract class WildModel extends EntityModel<WildRenderer.State> {
	private final ModelPart root;
	private final Map<String, ModelPart> parts = new HashMap<>();

	WildModel(ModelPart root) {
		super(root);
		this.root = root;
	}

	/** The part called {@code name}, wherever it hangs (found once, then kept). */
	ModelPart part(String name) {
		return this.parts.computeIfAbsent(name, n -> {
			for (ModelPart parent : this.root.getAllParts()) {
				if (parent.hasChild(n)) {
					return parent.getChild(n);
				}
			}
			throw new IllegalStateException("no part " + n);
		});
	}

	void angles(String name, float x, float y, float z) {
		ModelPart part = this.part(name);
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}

	/** Front and hind legs swinging in opposition, onto their resting angles. */
	void quadruped(String lLeg, String lArm, String rLeg, String rArm, float swing, float amount) {
		this.part(lArm).xRot += Mth.cos(swing) * amount;
		this.part(rArm).xRot += Mth.cos(swing + Mth.PI) * amount;
		this.part(rLeg).xRot += Mth.cos(swing) * amount;
		this.part(lLeg).xRot += Mth.cos(swing + Mth.PI) * amount;
	}

	/** The head following where it looks. */
	void look(String head, WildRenderer.State state, float amount) {
		ModelPart part = this.part(head);
		part.xRot += state.xRot * Mth.DEG_TO_RAD * amount;
		part.yRot += state.yRot * Mth.DEG_TO_RAD * amount;
	}

	static float wiggle(float age) {
		return Mth.cos(age * 0.09F) * 0.05F;
	}

	static float wiggleAlt(float age) {
		return Mth.sin(age * 0.09F) * 0.05F;
	}
}
