package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.BRUTE;
import static com.jaymoskingdoms.Classes.PlayerClass.RANGER;
import static com.jaymoskingdoms.Classes.PlayerClass.WARLORD;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Skills.Perk;
import java.util.Set;

/**
 * Warfare, moved off Strength and shared out (docs/ClassTrees-Plan.md): Shout (level 5) and Throw Net (8) to the Warlord;
 * Throw (5), Warrior's Charge (8) and Warrior's Last Stand (20) to the Brute; the Rider's ranks came back as points. Throw Net
 * stays the Ranger's too (it was the Survivalist's), and Mortal Strike, which the plan doesn't place, stays both warriors'.
 */
final class WarfareTrees {
	private WarfareTrees() {
	}

	static void build() {
		ClassTrees.moved(Perk.SHOUT, Set.of(WARLORD), 5, 2, 1);
		ClassTrees.moved(Perk.THROW_NET, Set.of(WARLORD, RANGER), 8, 2, 0);
		ClassTrees.moved(Perk.THROW, Set.of(BRUTE), 5, 2, 3);
		ClassTrees.moved(Perk.WARRIOR_CHARGE, Set.of(BRUTE), 8, 2, 4);
		ClassTrees.moved(Perk.MORTAL_STRIKE, Set.of(WARLORD, BRUTE), 13, 4, 2);
		ClassTrees.moved(Perk.WARRIOR_LAST_STAND, Set.of(BRUTE), 20, 6, 4);
	}
}
