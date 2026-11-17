package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.CLERIC;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.FaithExtras;
import com.jaymoskingdoms.Litany;
import java.util.List;
import java.util.Set;

/**
 * The Cleric's litany ({@link Litany}): Devout, Bless the Band, Sanctify, Last Rites, Healer or War Priest at 10, the
 * Patron's Gift and Destroy Undead at the top. Turn the Undead (the raised symbol) is the Cleric's from the start; the
 * blessing is kept by Clerics who had it.
 */
final class ClericTree {
	private ClericTree() {
	}

	static void build() {
		Set<PlayerClass> cleric = Set.of(CLERIC);
		ClassTrees.add(Litany.DEVOUT, cleric, 3, 2, 0, 0, 2, List.of(), List.of());
		ClassTrees.add(Litany.BLESS, cleric, 1, 2, 3, 1, 1, List.of(), List.of());
		ClassTrees.add(Litany.SANCTIFY, cleric, 1, 2, 5, 2, 3, List.of(), List.of());
		ClassTrees.add(Litany.RITES, cleric, 1, 2, 8, 3, 2, List.of(), List.of());
		ClassTrees.add(Litany.HEALER_KEY, cleric, 1, 3, 10, 4, 1, List.of(), List.of(Litany.WAR_PRIEST));
		ClassTrees.add(Litany.WAR_PRIEST, cleric, 1, 3, 10, 4, 3, List.of(), List.of(Litany.HEALER_KEY));
		ClassTrees.add(Litany.PATRON, cleric, 1, 3, 15, 5, 2, List.of(), List.of());
		ClassTrees.add(Litany.DESTROY_KEY, cleric, 1, 3, 20, 6, 2, List.of(), List.of());
		ClassTrees.kept(CLERIC, Litany.BLESS);
		// patch 8: the extras
		ClassTrees.add(FaithExtras.STEADY, cleric, 1, 2, 3, 1, 3, List.of(), List.of());
		ClassTrees.add(FaithExtras.CANDLE, cleric, 1, 2, 12, 5, 1, List.of(), List.of());
		ClassTrees.add(FaithExtras.SHEPHERD, cleric, 1, 3, 18, 6, 1, List.of(), List.of(FaithExtras.EXORCIST));
		ClassTrees.add(FaithExtras.EXORCIST, cleric, 1, 3, 18, 6, 3, List.of(), List.of(FaithExtras.SHEPHERD));
		ClassTrees.add(FaithExtras.INTERCESSION, cleric, 1, 2, 22, 7, 2, List.of(), List.of());
	}
}
