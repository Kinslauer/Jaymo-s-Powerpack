package com.jaymoskingdoms.client;

import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
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
	private static @Nullable PlayerEntityExtendedModel<?> playerMarks, playerGlow;

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

	/**
	 * (0.7.218) The same for a player MCA draws ({@link HeritageLooks}): MCA's skin layer's body, posed by MCA's own pose
	 * for the player (the one its skin, clothes and hair take, and Touch'n Motion's over it). Drawn on the player's
	 * animated model instead, the marks were posed a second time on their own, and with Touch'n Motion the two came
	 * apart (Andy, 2026-10-26: the character stood still while the heritage marks moved). Null when MCA isn't drawing them.
	 */
	static @Nullable PlayerModel playerMarks(PlayerModel parent) {
		if (!(parent instanceof PlayerEntityExtendedModel<?> player)) {
			return null;
		}
		if (playerMarks == null) {
			playerMarks = createPlayer(MARKS_INFLATE);
		}
		return pose(player, playerMarks);
	}

	/** The player's body for the glow, just over MCA's face; null when MCA isn't drawing the player. */
	static @Nullable PlayerModel playerGlow(PlayerModel parent) {
		if (!(parent instanceof PlayerEntityExtendedModel<?> player)) {
			return null;
		}
		if (playerGlow == null) {
			playerGlow = createPlayer(GLOW_INFLATE);
		}
		return pose(player, playerGlow);
	}

	private static PlayerEntityExtendedModel<?> createPlayer(float inflate) {
		// as MCA builds a player's skin layer body: wide arms, the 64x64 skin, no wear parts
		PlayerEntityExtendedModel<?> model = new PlayerEntityExtendedModel<>(
			LayerDefinition.create(VillagerEntityModelMCA.bodyData(new CubeDeformation(inflate), false), 64, 64).bakeRoot(), false);
		return model.receiveDeferredAnimationPose().hideWears();
	}

	private static PlayerEntityExtendedModel<?> pose(PlayerEntityExtendedModel<?> parent, PlayerEntityExtendedModel<?> model) {
		parent.copyPropertiesTo(model);
		model.copyVisibility(parent);
		return model;
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
