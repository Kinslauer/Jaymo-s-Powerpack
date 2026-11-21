package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.RANGER;
import static com.jaymoskingdoms.Classes.PlayerClass.THIEF;

import com.jaymoskingdoms.ClassForks;
import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.RogueExtras;
import com.jaymoskingdoms.Skills.Perk;
import java.util.List;
import java.util.Set;

/**
 * The Thief's tree: the Rogue's Arts, moved off Stealth (the Bear Trap the Ranger's too, as it was the Survivalist's), and a
 * crossroads of its own at 10: Assassin or Acrobat ({@link ClassForks}).
 */
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
		// the crossroads at 10: Assassin or Acrobat
		ClassTrees.add(ClassForks.ASSASSIN, Set.of(THIEF), 1, 3, 10, 4, 1, List.of(), List.of(ClassForks.ACROBAT));
		ClassTrees.add(ClassForks.ACROBAT, Set.of(THIEF), 1, 3, 10, 4, 3, List.of(), List.of(ClassForks.ASSASSIN));
		// patch 8: the extras
		ClassTrees.add(RogueExtras.FINGERS, Set.of(THIEF), 3, 2, 0, 0, 1, List.of(), List.of());
		ClassTrees.add(RogueExtras.CASE, Set.of(THIEF), 1, 2, 3, 0, 3, List.of(), List.of());
		ClassTrees.add(RogueExtras.FENCE, Set.of(THIEF), 1, 2, 8, 2, 1, List.of(), List.of());
		ClassTrees.add(RogueExtras.STOREY, Set.of(THIEF), 1, 2, 12, 3, 2, List.of(), List.of());
		ClassTrees.add(RogueExtras.CUTPURSE, Set.of(THIEF), 1, 3, 18, 5, 1, List.of(), List.of(RogueExtras.SHADOW_BLADE));
		ClassTrees.add(RogueExtras.SHADOW_BLADE, Set.of(THIEF), 1, 3, 18, 5, 3, List.of(), List.of(RogueExtras.CUTPURSE));
		ClassTrees.add(RogueExtras.GHOST, Set.of(THIEF), 1, 3, 22, 6, 2, List.of("vanish"), List.of());
	}
}
