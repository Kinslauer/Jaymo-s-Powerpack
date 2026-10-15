package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Pirates;
import net.minecraft.resources.Identifier;

/**
 * (0.7.37) The sailors' looks, all original (drawn by {@code tools/gen_pirates.py}): pirate deckhands in striped shirts
 * and bandanas, sooty gunners in leather jerkins, ragged powder monkeys, the pirate captain in his tricorn and red coat,
 * a harbour's shipwright in his apron, and a realm's sea captain in navy blue.
 */
final class PirateSkins {
	/** Faces drawn per role. */
	static final int DECKHANDS = 4, GUNNERS = 2, MONKEYS = 2;
	private static final Identifier[] DECKHAND = new Identifier[DECKHANDS];
	private static final Identifier[] GUNNER = new Identifier[GUNNERS];
	private static final Identifier[] MONKEY = new Identifier[MONKEYS];
	private static final Identifier CAPTAIN = JaymosKingdoms.id("textures/entity/pirate/captain.png");
	private static final Identifier SHIPWRIGHT = JaymosKingdoms.id("textures/entity/pirate/shipwright.png");
	private static final Identifier SEA_CAPTAIN = JaymosKingdoms.id("textures/entity/pirate/sea_captain.png");

	private PirateSkins() {
	}

	static void register() {
		for (int i = 0; i < DECKHANDS; i++) {
			DECKHAND[i] = JaymosKingdoms.id("textures/entity/pirate/deckhand_" + i + ".png");
		}
		for (int i = 0; i < GUNNERS; i++) {
			GUNNER[i] = JaymosKingdoms.id("textures/entity/pirate/gunner_" + i + ".png");
		}
		for (int i = 0; i < MONKEYS; i++) {
			MONKEY[i] = JaymosKingdoms.id("textures/entity/pirate/powder_monkey_" + i + ".png");
		}
		JaymosKingdomsClient.SKINS.put(Pirates.DECKHAND, state -> DECKHAND[state.variant % DECKHANDS]);
		JaymosKingdomsClient.SKINS.put(Pirates.GUNNER, state -> GUNNER[state.variant % GUNNERS]);
		JaymosKingdomsClient.SKINS.put(Pirates.POWDER_MONKEY, state -> MONKEY[state.variant % MONKEYS]);
		JaymosKingdomsClient.SKINS.put(Pirates.CAPTAIN, state -> CAPTAIN);
		JaymosKingdomsClient.SKINS.put(Pirates.SHIPWRIGHT, state -> SHIPWRIGHT);
		JaymosKingdomsClient.SKINS.put(Pirates.SEA_CAPTAIN, state -> SEA_CAPTAIN);
	}
}
