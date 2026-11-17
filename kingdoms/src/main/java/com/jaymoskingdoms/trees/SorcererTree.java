package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.SORCERER;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Skills.Perk;
import java.util.Set;

/** The Sorcerer's tree: Sorcery (the imbue, absorbing and barrage) and Forbidden Arts after it, moved off Survival. */
final class SorcererTree {
	private SorcererTree() {
	}

	static void build() {
		ClassTrees.moved(Perk.SORCERY, Set.of(SORCERER), 0, 0, 2);
		ClassTrees.moved(Perk.FORBIDDEN_ARTS, Set.of(SORCERER), 10, 3, 2, "sorcery");
	}
}
