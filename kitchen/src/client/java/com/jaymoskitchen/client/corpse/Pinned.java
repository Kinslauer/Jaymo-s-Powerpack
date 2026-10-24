package com.jaymoskitchen.client.corpse;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;

/**
 * (0.5.23) A body an Archer's killing shot nailed to a wall (see {@code CorpseEntity#pin}) hangs there upright and dead:
 * arms hanging a little out from its sides, legs dangling, head slumped onto its chest. Set on the body's render state by
 * {@code CorpseRenderer} and applied after the model has animated, as {@link Limp} is.
 */
public final class Pinned {
	public static final RenderStateDataKey<Pinned> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:pinned");
	public static final Pinned HANGING = new Pinned();

	private Pinned() {
	}

	void pose(ModelPart root) {
		set(root, "right_arm", 0.12F, 0.0F, 0.18F);
		set(root, "left_arm", 0.08F, 0.0F, -0.15F);
		set(root, "right_leg", 0.06F, 0.0F, 0.05F);
		set(root, "left_leg", -0.04F, 0.0F, -0.05F);
		// four-legged: legs hanging straight
		set(root, "right_hind_leg", 0.0F, 0.0F, 0.0F);
		set(root, "left_hind_leg", 0.0F, 0.0F, 0.0F);
		set(root, "right_front_leg", 0.0F, 0.0F, 0.0F);
		set(root, "left_front_leg", 0.0F, 0.0F, 0.0F);
		set(root, "head", 0.7F, 0.15F, 0.1F);
	}

	private static void set(ModelPart root, String name, float x, float y, float z) {
		if (root.hasChild(name)) {
			ModelPart part = root.getChild(name);
			part.xRot = x;
			part.yRot = y;
			part.zRot = z;
		}
	}
}
