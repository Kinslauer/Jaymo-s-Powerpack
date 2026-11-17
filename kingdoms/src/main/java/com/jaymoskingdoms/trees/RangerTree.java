package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.RANGER;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Skills.Perk;
import java.util.Set;

/** The Ranger's tree: Archery, moved off Survival and the Ranger's alone now (the Bear Trap and the net are shared, see their trees). */
final class RangerTree {
	private RangerTree() {
	}

	static void build() {
		ClassTrees.moved(Perk.POWER_SHOT, Set.of(RANGER), 5, 1, 1);
		ClassTrees.moved(Perk.ENTANGLING_ROOTS, Set.of(RANGER), 5, 1, 3);
		ClassTrees.moved(Perk.BARRAGE, Set.of(RANGER), 9, 3, 1, "power_shot");
		ClassTrees.moved(Perk.SPIRIT_WOLF, Set.of(RANGER), 9, 3, 3, "entangling_roots");
		ClassTrees.moved(Perk.MAGIC_ARROW, Set.of(RANGER), 13, 4, 1, "barrage");
		ClassTrees.moved(Perk.RAIN_OF_ARROWS, Set.of(RANGER), 13, 4, 3, "spirit_wolf");
	}
}
