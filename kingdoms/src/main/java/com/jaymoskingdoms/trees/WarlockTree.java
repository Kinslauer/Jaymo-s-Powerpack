package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.WARLOCK;

import com.jaymoskingdoms.ArcaneExtras;
import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.PactArts;
import java.util.List;
import java.util.Set;

/**
 * The Warlock's pact ({@link PactArts}): Pact Slots, Patient Creditor and Eldritch Bolt, In Kind, Renege, the Blade, the Chain
 * or the Tome at 10 (a fork of three), and the Arcanum at the top. One pact slot is the Warlock's from the start; Warlocks who
 * had two keep a rank of Pact Slots.
 */
final class WarlockTree {
	private WarlockTree() {
	}

	static void build() {
		Set<PlayerClass> warlock = Set.of(WARLOCK);
		ClassTrees.add(PactArts.SLOTS, warlock, 3, 2, 0, 0, 2, List.of(), List.of());
		ClassTrees.add(PactArts.CREDITOR, warlock, 1, 2, 3, 1, 1, List.of(), List.of());
		ClassTrees.add(PactArts.BOLT, warlock, 1, 2, 3, 1, 3, List.of(), List.of());
		ClassTrees.add(PactArts.KIND, warlock, 1, 2, 5, 2, 2, List.of(), List.of());
		ClassTrees.add(PactArts.RENEGE, warlock, 1, 2, 8, 3, 2, List.of(), List.of());
		ClassTrees.add(PactArts.BLADE, warlock, 1, 3, 10, 4, 1, List.of(), List.of(PactArts.CHAIN, PactArts.TOME));
		ClassTrees.add(PactArts.CHAIN, warlock, 1, 3, 10, 4, 2, List.of(), List.of(PactArts.BLADE, PactArts.TOME));
		ClassTrees.add(PactArts.TOME, warlock, 1, 3, 10, 4, 3, List.of(), List.of(PactArts.BLADE, PactArts.CHAIN));
		ClassTrees.add(PactArts.ARCANUM, warlock, 1, 3, 20, 5, 2, List.of(), List.of());
		ClassTrees.kept(WARLOCK, PactArts.SLOTS);
		// patch 8: the extras
		ClassTrees.add(ArcaneExtras.SIGHT, warlock, 1, 2, 5, 2, 1, List.of(), List.of());
		ClassTrees.add(ArcaneExtras.TIME, warlock, 1, 2, 12, 4, 0, List.of(), List.of());
		ClassTrees.add(ArcaneExtras.TITHE, warlock, 1, 3, 15, 5, 1, List.of(), List.of(ArcaneExtras.USURY_KEY));
		ClassTrees.add(ArcaneExtras.USURY_KEY, warlock, 1, 3, 15, 5, 3, List.of(), List.of(ArcaneExtras.TITHE));
		ClassTrees.add(ArcaneExtras.HAND, warlock, 1, 3, 22, 6, 2, List.of(), List.of());
	}
}
