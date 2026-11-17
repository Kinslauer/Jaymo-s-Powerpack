package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.TINKERER;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.TinkerPerks;
import com.jaymoskingdoms.WarExtras;
import java.util.List;
import java.util.Set;

/**
 * The Workshop's right (the Tinkerer's column; the bench in the middle is in {@link MarksmanTree}): Spare Hands, Contraption
 * (rank 2 is Educated Guess), Dismantle, the Iron Pup and what it grows into at 18 (Iron Defender or Iron Hound), Imitation at
 * the top. The craftsman's eye (the world tiers opened by level) costs no points ({@link TinkerPerks#eye}).
 */
final class TinkererTree {
	private TinkererTree() {
	}

	static void build() {
		Set<PlayerClass> tinkerer = Set.of(TINKERER);
		ClassTrees.add(TinkerPerks.HANDS, tinkerer, 2, 2, 0, 0, 4, List.of(), List.of());
		ClassTrees.add(TinkerPerks.CONTRAPTION, tinkerer, 2, 2, 5, 1, 4, List.of(), List.of());
		ClassTrees.add(TinkerPerks.DISMANTLE_KEY, tinkerer, 1, 2, 10, 2, 4, List.of(), List.of());
		ClassTrees.add(TinkerPerks.PUP, tinkerer, 1, 3, 12, 3, 3, List.of(), List.of());
		ClassTrees.add(TinkerPerks.DEFENDER, tinkerer, 1, 2, 18, 4, 3, List.of(TinkerPerks.PUP), List.of(TinkerPerks.HOUND));
		ClassTrees.add(TinkerPerks.HOUND, tinkerer, 1, 2, 18, 4, 4, List.of(TinkerPerks.PUP), List.of(TinkerPerks.DEFENDER));
		ClassTrees.add(TinkerPerks.IMITATION, tinkerer, 1, 3, 22, 5, 4, List.of(), List.of());
		// patch 8: the extras
		ClassTrees.add(WarExtras.CLOCK, tinkerer, 1, 2, 8, 1, 3, List.of(), List.of(WarExtras.SCRAP));
		ClassTrees.add(WarExtras.SCRAP, tinkerer, 1, 2, 8, 2, 3, List.of(), List.of(WarExtras.CLOCK));
	}
}
