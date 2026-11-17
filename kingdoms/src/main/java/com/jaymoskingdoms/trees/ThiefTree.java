package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.RANGER;
import static com.jaymoskingdoms.Classes.PlayerClass.THIEF;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Skills.Perk;
import java.util.Set;

/** The Thief's tree: the Rogue's Arts, moved off Stealth (the Bear Trap the Ranger's too, as it was the Survivalist's). */
final class ThiefTree {
	private ThiefTree() {
	}

	static void build() {
		ClassTrees.moved(Perk.SLICE_AND_DICE, Set.of(THIEF), 5, 1, 1);
		ClassTrees.moved(Perk.SHOCK_POWDER, Set.of(THIEF), 5, 1, 3);
		ClassTrees.moved(Perk.BEAR_TRAP, Set.of(THIEF, RANGER), 9, 2, 2);
		ClassTrees.moved(Perk.SHADOW_STEP, Set.of(THIEF), 9, 2, 3, "shock_powder");
		ClassTrees.moved(Perk.MUTILATE, Set.of(THIEF), 13, 3, 1, "slice_and_dice");
		ClassTrees.moved(Perk.VANISH, Set.of(THIEF), 13, 3, 3, "shadow_step");
	}
}
