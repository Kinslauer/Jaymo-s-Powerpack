package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.PALADIN;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.OathGifts;
import java.util.List;
import java.util.Set;

/**
 * The Paladin's oath ({@link OathGifts}): Deep Wells, Smite, Shield of Faith, the Aura, the oath's own perk at 10, Divine
 * Sense, Aura of Courage or Aura of Warding at 15, and the Vigil at the top. Lay on Hands (the class key) is the Paladin's
 * from the start; the smite and the aura are kept by Paladins who had them.
 */
final class PaladinTree {
	private PaladinTree() {
	}

	static void build() {
		Set<PlayerClass> paladin = Set.of(PALADIN);
		ClassTrees.add(OathGifts.WELLS, paladin, 3, 2, 0, 0, 2, List.of(), List.of());
		ClassTrees.add(OathGifts.SMITE, paladin, 1, 2, 3, 1, 1, List.of(), List.of());
		ClassTrees.add(OathGifts.SHIELD, paladin, 1, 2, 5, 2, 3, List.of(), List.of());
		ClassTrees.add(OathGifts.AURA, paladin, 1, 2, 8, 3, 2, List.of(), List.of());
		ClassTrees.add(OathGifts.OATH, paladin, 1, 3, 10, 4, 2, List.of(), List.of());
		ClassTrees.add(OathGifts.SENSE, paladin, 1, 2, 12, 5, 2, List.of(), List.of());
		ClassTrees.add(OathGifts.COURAGE, paladin, 1, 3, 15, 6, 1, List.of(OathGifts.AURA), List.of(OathGifts.WARDING));
		ClassTrees.add(OathGifts.WARDING, paladin, 1, 3, 15, 6, 3, List.of(OathGifts.AURA), List.of(OathGifts.COURAGE));
		ClassTrees.add(OathGifts.VIGIL_KEY, paladin, 1, 3, 20, 7, 2, List.of(), List.of());
		ClassTrees.kept(PALADIN, OathGifts.SMITE, OathGifts.AURA);
	}
}
