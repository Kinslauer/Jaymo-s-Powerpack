package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.SORCERER;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Sigils;
import com.jaymoskingdoms.Skills.Perk;
import java.util.List;
import java.util.Set;

/**
 * The Sorcerer's tree: the sigil is theirs from the start (a tap of the class key, {@link Sigils}); Sigilcraft grows it,
 * the Imbue is learned at 5, two crossroads (Ward Sigil or Twin Imbue at 10, Elemental Burst or Lingering Imbue at 15),
 * Absorbing (the old Sorcery page) with Forbidden Arts after it, and Etched Blade at the top.
 */
final class SorcererTree {
	private SorcererTree() {
	}

	static void build() {
		Set<com.jaymoskingdoms.Classes.PlayerClass> sorcerer = Set.of(SORCERER);
		ClassTrees.add(Sigils.CRAFT, sorcerer, 3, 2, 0, 0, 1, List.of(), List.of());
		ClassTrees.moved(Perk.SORCERY, sorcerer, 0, 0, 3);
		ClassTrees.add(Sigils.IMBUE, sorcerer, 1, 2, 5, 1, 1, List.of(), List.of());
		ClassTrees.add(Sigils.WARD, sorcerer, 1, 2, 10, 2, 0, List.of(Sigils.CRAFT), List.of(Sigils.TWIN));
		ClassTrees.add(Sigils.TWIN, sorcerer, 1, 2, 10, 2, 2, List.of(Sigils.IMBUE), List.of(Sigils.WARD));
		ClassTrees.moved(Perk.FORBIDDEN_ARTS, sorcerer, 10, 2, 3, "sorcery");
		ClassTrees.add(Sigils.BURST, sorcerer, 1, 2, 15, 3, 0, List.of(Sigils.IMBUE), List.of(Sigils.LINGER));
		ClassTrees.add(Sigils.LINGER, sorcerer, 1, 2, 15, 3, 2, List.of(Sigils.IMBUE), List.of(Sigils.BURST));
		ClassTrees.add(Sigils.ETCHED, sorcerer, 1, 3, 20, 5, 1, List.of(Sigils.IMBUE), List.of());
		// a Sorcerer who had the old Sorcery page could imbue: they keep it
		ClassTrees.keptIf(SORCERER, Perk.SORCERY.key(), Sigils.IMBUE);
	}
}
