package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.MONK;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.KiArts;
import java.util.List;
import java.util.Set;

/**
 * The Monk's way ({@link KiArts}): Deep Breath, Stunning Blow and Deflect, Flurry, Step of the Wind, Open Hand or Iron Body
 * at 10, Still Mind, and Quivering Palm or Empty Body at the top. Catch an Arrow (the class key) is the Monk's from the start;
 * the stunning blow and the wall slide (now Step of the Wind's) are kept by Monks who had them.
 */
final class MonkTree {
	private MonkTree() {
	}

	static void build() {
		Set<PlayerClass> monk = Set.of(MONK);
		ClassTrees.add(KiArts.BREATH, monk, 3, 2, 0, 0, 2, List.of(), List.of());
		ClassTrees.add(KiArts.STUN, monk, 1, 2, 3, 1, 1, List.of(), List.of());
		ClassTrees.add(KiArts.DEFLECT, monk, 1, 2, 3, 1, 3, List.of(), List.of());
		ClassTrees.add(KiArts.FLURRY, monk, 1, 2, 5, 2, 2, List.of(), List.of());
		ClassTrees.add(KiArts.WIND, monk, 1, 2, 8, 3, 2, List.of(), List.of());
		ClassTrees.add(KiArts.OPEN_HAND, monk, 1, 3, 10, 4, 1, List.of(KiArts.FLURRY), List.of(KiArts.IRON_BODY));
		ClassTrees.add(KiArts.IRON_BODY, monk, 1, 3, 10, 4, 3, List.of(), List.of(KiArts.OPEN_HAND));
		ClassTrees.add(KiArts.STILL, monk, 1, 2, 15, 5, 2, List.of(), List.of());
		ClassTrees.add(KiArts.QUIVERING, monk, 1, 3, 20, 6, 1, List.of(KiArts.STUN), List.of(KiArts.EMPTY));
		ClassTrees.add(KiArts.EMPTY, monk, 1, 3, 20, 6, 3, List.of(), List.of(KiArts.QUIVERING));
		ClassTrees.kept(MONK, KiArts.STUN, KiArts.WIND);
	}
}
