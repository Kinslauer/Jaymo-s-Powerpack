package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.BRUTE;
import static com.jaymoskingdoms.Classes.PlayerClass.RANGER;
import static com.jaymoskingdoms.Classes.PlayerClass.WARLORD;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Discipline;
import com.jaymoskingdoms.Fury;
import com.jaymoskingdoms.Skills.Perk;
import com.jaymoskingdoms.WarExtras;
import java.util.List;
import java.util.Set;

/**
 * The Warlord's and the Brute's mirrored trees, Discipline ({@link Discipline}, cols 0-2) and Fury ({@link Fury}, cols 2-4),
 * row for row each answering the other, with Warfare moved off Strength and shared out between them (docs/ClassTrees-Plan.md):
 * Shout (level 5) and Throw Net (8) to the Warlord; Throw (5), Warrior's Charge (8) and Warrior's Last Stand (20) to the
 * Brute; the Rider's ranks came back as points. Throw Net stays the Ranger's too (it was the Survivalist's), and Mortal
 * Strike, which the plan doesn't place, stays both warriors'.
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
		ClassTrees.moved(Perk.WARRIOR_LAST_STAND, Set.of(BRUTE), 20, 5, 4);
		Set<com.jaymoskingdoms.Classes.PlayerClass> warlord = Set.of(WARLORD), brute = Set.of(BRUTE);
		// Discipline
		ClassTrees.add(Discipline.MEASURED, warlord, 3, 2, 0, 0, 1, List.of(), List.of());
		ClassTrees.add(Discipline.RIPOSTE_KEY, warlord, 1, 2, 3, 1, 1, List.of(), List.of());
		ClassTrees.add(Discipline.BOARD, warlord, 1, 3, 10, 3, 0, List.of(), List.of(Discipline.TWO_HANDED));
		ClassTrees.add(Discipline.TWO_HANDED, warlord, 1, 3, 10, 3, 1, List.of(), List.of(Discipline.BOARD));
		ClassTrees.add(Discipline.READ, warlord, 1, 2, 15, 5, 1, List.of(Discipline.RIPOSTE_KEY), List.of());
		ClassTrees.add(Discipline.COMMANDER, warlord, 1, 3, 20, 6, 0, List.of(), List.of(Discipline.DUELIST));
		ClassTrees.add(Discipline.DUELIST, warlord, 1, 3, 20, 6, 1, List.of(), List.of(Discipline.COMMANDER));
		ClassTrees.add(Discipline.PERFECT_FORM, warlord, 1, 3, 24, 7, 1, List.of(Discipline.MEASURED), List.of());
		// Fury
		ClassTrees.add(Fury.BLOOD_UP, brute, 3, 2, 0, 0, 3, List.of(), List.of());
		ClassTrees.add(Fury.PAIN_FEEDS_KEY, brute, 1, 2, 3, 1, 3, List.of(), List.of());
		ClassTrees.add(Fury.RECKLESS_KEY, brute, 1, 3, 10, 3, 3, List.of(), List.of(Fury.JUGGERNAUT));
		ClassTrees.add(Fury.JUGGERNAUT, brute, 1, 3, 10, 3, 4, List.of(), List.of(Fury.RECKLESS_KEY));
		ClassTrees.add(Fury.SHRUG, brute, 1, 2, 15, 5, 3, List.of(Fury.PAIN_FEEDS_KEY), List.of());
		ClassTrees.add(Fury.BLACKOUT, brute, 1, 3, 20, 6, 3, List.of(), List.of(Fury.BLOODLUST));
		ClassTrees.add(Fury.BLOODLUST, brute, 1, 3, 20, 6, 4, List.of(), List.of(Fury.BLACKOUT));
		ClassTrees.add(Fury.RAMPAGE, brute, 1, 3, 24, 7, 3, List.of(Fury.BLOOD_UP), List.of());
		// patch 8: the extras
		ClassTrees.add(WarExtras.DRILLED, warlord, 1, 2, 5, 2, 2, List.of(), List.of());
		ClassTrees.add(WarExtras.WALL, warlord, 1, 2, 12, 4, 0, List.of(), List.of());
		ClassTrees.add(WarExtras.FAR, warlord, 1, 3, 18, 5, 0, List.of(), List.of());
		ClassTrees.add(WarExtras.SKULL_KEY, brute, 1, 2, 5, 1, 4, List.of(), List.of());
		ClassTrees.add(WarExtras.SCAR, brute, 1, 2, 12, 4, 3, List.of(), List.of());
	}
}
