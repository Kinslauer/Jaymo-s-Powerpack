package com.jaymoskitchen.client.corpse;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

/**
 * A carried corpse is limp: its limbs hang from the shoulder or hip straight down under gravity (it's carried on its
 * back, so that's out behind it), swinging like pendulums as the carrier moves (see CorpseEntity#swingLimbs), and its
 * head lolls back. Set on the body's render state by {@code CorpseRenderer} and applied, by part name, right after
 * the body's model has animated (see {@code ModelFeatureRendererMixin}). A {@link Ragdoll} (a body lying on the
 * ground) is applied the same way.
 *
 * @param back how far the limbs have swung toward the carrier's back (radians)
 * @param left how far they've swung toward the carrier's left (along the body, toward its feet)
 */
public record Limp(float back, float left) {
	public static final RenderStateDataKey<Limp> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:limp");

	/** Four-legged animals are carried belly down: their legs hang straight down (their resting way), swinging. */

	/** Poses the limbs of a model that's just been animated, if it's a carried or sprawled corpse's. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState renderState)) {
			return;
		}
		Ragdoll ragdoll = renderState.getData(Ragdoll.KEY);
		Limp limp = renderState.getData(KEY);
		Pinned pinned = renderState.getData(Pinned.KEY);
		if (pinned != null) {
			pinned.pose(model.root());
		} else if (ragdoll != null) {
			ragdoll.apply(model.root());
		} else if (limp != null) {
			limp.pose(model.root());
		}
		// (0.4.39) then its ragdoll physics: limbs flung about and flopping back (see Flop)
		Flop.apply(model, state);
	}

	private void pose(ModelPart root) {
		Limp limp = this;
		// humanoids: arms and legs straight down (a quarter turn back from the body), each swinging a little its own way
		if (root.hasChild("right_arm")) {
			hang(root, "right_arm", limp, 1.0F, 0.06F);
			hang(root, "left_arm", limp, 0.9F, -0.06F);
			hang(root, "right_leg", limp, 0.7F, 0.03F);
			hang(root, "left_leg", limp, 0.75F, -0.03F);
			if (root.hasChild("head")) {
				// head thrown back, rolling with the swing
				ModelPart head = root.getChild("head");
				head.xRot = -1.0F - limp.left() * 0.3F;
				head.yRot = limp.back() * 0.4F;
				head.zRot = 0.0F;
			}
		}
		// four-legged animals: legs straight down, swinging, and the head hanging
		if (root.hasChild("right_hind_leg")) {
			dangle(root, "right_hind_leg", limp, 0.8F);
			dangle(root, "left_hind_leg", limp, 0.85F);
			dangle(root, "right_front_leg", limp, 1.0F);
			dangle(root, "left_front_leg", limp, 0.95F);
			if (root.hasChild("head")) {
				ModelPart head = root.getChild("head");
				head.xRot = 0.9F + limp.back() * 0.2F;
				head.yRot = limp.left() * 0.3F;
			}
		}
	}

	/** A humanoid limb hanging straight down from the body lying on its back, swung by the carrier's movement. */
	private static void hang(ModelPart root, String name, Limp limp, float give, float spread) {
		if (root.hasChild(name)) {
			ModelPart limb = root.getChild(name);
			limb.xRot = Mth.HALF_PI - limp.left() * give;
			limb.yRot = 0.0F;
			limb.zRot = spread + limp.back() * give;
		}
	}

	/** A leg hanging from the hip under a body carried belly down (sideways across the arms), swung by the carrier. */
	private static void dangle(ModelPart root, String name, Limp limp, float give) {
		if (root.hasChild(name)) {
			ModelPart leg = root.getChild(name);
			leg.xRot = -limp.left() * give;
			leg.yRot = 0.0F;
			leg.zRot = limp.back() * give;
		}
	}
}
