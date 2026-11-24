package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.ARTIFICER;

import com.jaymoskingdoms.Artificers;
import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.List;
import java.util.Set;

/**
 * The Artificer's tree: Keen Eye (3 ranks: a reforge may roll a grade higher) and Deep Infusion (an infusion a grade higher) on the
 * left; Long Brew (3 ranks: elixirs last half as long again a rank) and Twice Over (two elixirs from one pour) on the right; Mending
 * Hands at the foot (a reforge mends two fifths of what the gear has lost).
 */
final class ArtificerTree {
	private ArtificerTree() {
	}

	static void build() {
		Set<PlayerClass> artificer = Set.of(ARTIFICER);
		ClassTrees.add(Artificers.KEEN, artificer, 3, 1, 0, 0, 1, List.of(), List.of());
		ClassTrees.add(Artificers.DEEP, artificer, 1, 2, 5, 1, 1, List.of(Artificers.KEEN), List.of());
		ClassTrees.add(Artificers.LONG, artificer, 3, 1, 0, 0, 3, List.of(), List.of());
		ClassTrees.add(Artificers.TWICE, artificer, 1, 2, 8, 1, 3, List.of(Artificers.LONG), List.of());
		ClassTrees.add(Artificers.MENDING, artificer, 1, 2, 10, 2, 2, List.of(Artificers.KEEN), List.of());
	}
}
