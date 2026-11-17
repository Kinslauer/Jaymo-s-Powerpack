package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.RANGER;

import com.jaymoskingdoms.ClassForks;
import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.RogueExtras;
import com.jaymoskingdoms.Skills.Perk;
import java.util.List;
import java.util.Set;

/**
 * The Ranger's tree: Archery, moved off Survival and the Ranger's alone now (the Bear Trap and the net are shared, see their
 * trees), and a crossroads of its own at 10: Hunter or Beast Master ({@link ClassForks}).
 */
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
		// the crossroads at 10: Hunter or Beast Master
		ClassTrees.add(ClassForks.HUNTER, Set.of(RANGER), 1, 3, 10, 5, 1, List.of(), List.of(ClassForks.BEASTS));
		ClassTrees.add(ClassForks.BEASTS, Set.of(RANGER), 1, 3, 10, 5, 3, List.of(), List.of(ClassForks.HUNTER));
		// patch 8: the extras
		ClassTrees.add(RogueExtras.EYE, Set.of(RANGER), 3, 2, 0, 0, 1, List.of(), List.of());
		ClassTrees.add(RogueExtras.DRESS, Set.of(RANGER), 1, 2, 3, 0, 3, List.of(), List.of());
		ClassTrees.add(RogueExtras.FLETCH, Set.of(RANGER), 1, 2, 8, 2, 4, List.of(), List.of());
		ClassTrees.add(RogueExtras.STALKER, Set.of(RANGER), 1, 3, 18, 6, 1, List.of(), List.of(RogueExtras.WARDEN));
		ClassTrees.add(RogueExtras.WARDEN, Set.of(RANGER), 1, 3, 18, 6, 3, List.of(), List.of(RogueExtras.STALKER));
		ClassTrees.add(RogueExtras.HEART, Set.of(RANGER), 1, 3, 22, 7, 2, List.of(), List.of());
	}
}
