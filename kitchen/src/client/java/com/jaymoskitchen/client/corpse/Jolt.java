package com.jaymoskitchen.client.corpse;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A corpse jolted by an axe blow (hard) or by being rummaged through (slight): each limb, and the head, flops its own
 * way and settles as the jolt dies away, while the body stays put. Set on the body's render state by
 * {@code CorpseRenderer} and applied after its pose (see {@link Limp#apply}).
 *
 * @param strength how hard it's jolting this frame (1 for a fresh axe blow, 0 once it's settled)
 * @param time ticks since the jolt
 * @param side which way it flops first (+1 or -1)
 */
public record Jolt(float strength, float time, float side) {
	public static final RenderStateDataKey<Jolt> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:jolt");

	/** The parts that flop, wherever a model has them (on the root, or on its body). */
	private static final String[] LIMBS = {
		"head", "right_arm", "left_arm", "right_leg", "left_leg",
		"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg",
		"right_wing", "left_wing", "tail", "right_ear", "left_ear"
	};

	public void apply(ModelPart root) {
		shake(root, 0);
		if (root.hasChild("body")) {
			shake(root.getChild("body"), LIMBS.length);
		}
	}

	private void shake(ModelPart parent, int offset) {
		for (int i = 0; i < LIMBS.length; i++) {
			if (!parent.hasChild(LIMBS[i])) {
				continue;
			}
			ModelPart limb = parent.getChild(LIMBS[i]);
			// each limb on its own beat, so they flail apart rather than together
			float phase = time * 1.9F + (i + offset) * 1.7F;
			float give = strength * (i == 0 ? 0.5F : 0.9F);
			limb.xRot += Mth.sin(phase) * give * side;
			limb.zRot += Mth.cos(phase * 1.3F) * give * 0.6F;
		}
	}
}
