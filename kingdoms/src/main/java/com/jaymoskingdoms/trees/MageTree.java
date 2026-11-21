package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.MAGE;

import com.jaymoskingdoms.ArcaneExtras;
import com.jaymoskingdoms.ClassForks;
import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Skills.Perk;
import java.util.List;
import java.util.Set;

/**
 * The Mage's tree: Spellcraft, moved off Survival (Arcane, Fire and Frost, a column each), its crown, Enchanted Storage,
 * and a crossroads of its own at 10 across the top: Evoker or Warder ({@link ClassForks}).
 */
final class MageTree {
	private MageTree() {
	}

	static void build() {
		column(0, Perk.ARCANE_MISSILE, Perk.ARCANE_EXPLOSION, Perk.ARCANE_BEAM, Perk.ARCANE_BARRAGE, Perk.ARCANE_BLINK, Perk.ARCANE_EVOCATION);
		column(2, Perk.FIRE_BREATH, Perk.FIRE_SLASH, Perk.FIRE_METEOR, Perk.FIRE_STORM, Perk.FIRE_WALL, Perk.FIRE_HYDRA);
		column(4, Perk.FROST_NOVA, Perk.FROST_SPIKES, Perk.FROST_SHIELD, Perk.FROST_LANCE, Perk.FROST_BLIZZARD, Perk.FROST_ELEMENTAL);
		ClassTrees.moved(Perk.ENCHANTED_STORAGE, Set.of(MAGE), 15, 7, 1, "arcane_evocation");
		// the crossroads at 10: Evoker or Warder
		ClassTrees.add(ClassForks.EVOKER, Set.of(MAGE), 1, 3, 10, 0, 1, List.of(), List.of(ClassForks.WARDER));
		ClassTrees.add(ClassForks.WARDER, Set.of(MAGE), 1, 3, 10, 0, 3, List.of(), List.of(ClassForks.EVOKER));
		// patch 8: the extras
		ClassTrees.add(ArcaneExtras.SCHOLAR_KEY, Set.of(MAGE), 1, 3, 18, 8, 1, List.of(), List.of(ArcaneExtras.BATTLEMAGE));
		ClassTrees.add(ArcaneExtras.BATTLEMAGE, Set.of(MAGE), 1, 3, 18, 8, 3, List.of(), List.of(ArcaneExtras.SCHOLAR_KEY));
	}

	/** A school's six spells down one column, each needing the one above, at the levels they needed on Survival. */
	private static void column(int col, Perk... spells) {
		for (int i = 0; i < spells.length; i++) {
			if (i == 0) {
				ClassTrees.moved(spells[i], Set.of(MAGE), spells[i].minLevel, 1, col);
			} else {
				ClassTrees.moved(spells[i], Set.of(MAGE), spells[i].minLevel, 1 + i, col, spells[i - 1].key());
			}
		}
	}
}
