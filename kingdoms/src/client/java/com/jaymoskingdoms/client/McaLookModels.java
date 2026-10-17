package com.jaymoskingdoms.client;

import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.92) The bodies a villager's heritage looks are drawn on when it's an MCA villager ({@link VillagerLooks}). MCA
 * doesn't skin the animated model: it draws the skin, face, clothes and hair each on its own copy of its body, inflated
 * a little more each time (skin 0, face 0.01, clothes 0.0625, hair 0.125). Drawn on the bare animated model, the marks
 * sat at the skin's own depth and fought it, so they get MCA's body too: the marks just over the skin
 * ({@link #MARKS_INFLATE}), the glow just over the face ({@link #GLOW_INFLATE}), both under the clothes, posed and
 * shown like the animated model the way MCA's own layers are. Only loaded with MCA.
 */
final class McaLookModels {
	/** How far past MCA's skin the marks sit, and the glow (marks, eyes) past MCA's face (0.01). */
	static final float MARKS_INFLATE = 0.005F, GLOW_INFLATE = 0.02F;

	private static @Nullable VillagerEntityModelMCA marks, glow;

	private McaLookModels() {
	}

	/** The body to draw the marks on, posed like {@code parent}; null when the parent isn't MCA's (folk, others). */
	static @Nullable HumanoidModel<?> marks(HumanoidModel<?> parent) {
		if (!(parent instanceof VillagerEntityModelMCA villager)) {
			return null;
		}
		if (marks == null) {
			marks = create(MARKS_INFLATE);
		}
		return pose(villager, marks);
	}

	/** The body to draw the glow (glowing marks and eyes) on, just over MCA's face; null when the parent isn't MCA's. */
	static @Nullable HumanoidModel<?> glow(HumanoidModel<?> parent) {
		if (!(parent instanceof VillagerEntityModelMCA villager)) {
			return null;
		}
		if (glow == null) {
			glow = create(GLOW_INFLATE);
		}
		return pose(villager, glow);
	}

	private static VillagerEntityModelMCA create(float inflate) {
		// as MCA builds its skin layer's body: the 64x64 skin, no wear parts
		VillagerEntityModelMCA model = new VillagerEntityModelMCA(
			LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(inflate)), 64, 64).bakeRoot());
		model.receiveDeferredAnimationPose();
		return model.hideWears();
	}

	private static VillagerEntityModelMCA pose(VillagerEntityModelMCA parent, VillagerEntityModelMCA model) {
		parent.copyPropertiesTo(model);
		model.copyVisibility(parent);
		return model;
	}
}
