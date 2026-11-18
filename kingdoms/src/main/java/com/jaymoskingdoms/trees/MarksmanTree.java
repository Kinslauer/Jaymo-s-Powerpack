package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.MARKSMAN;
import static com.jaymoskingdoms.Classes.PlayerClass.TINKERER;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.WarExtras;
import com.jaymoskingdoms.Workshop;
import java.util.List;
import java.util.Set;

/**
 * The Workshop's left and middle (the Marksman and Tinkerer share it): the Marksman's column (Steady Aim, Speed Load,
 * Called Shot or Quick Shot at 10, Precision at the top) and the bench both may take (Gunsmith, gun upgrades, Trick Shot
 * bolts, Field Kit). The Tinkerer's column is {@link TinkererTree}.
 */
final class MarksmanTree {
	private MarksmanTree() {
	}

	static void build() {
		Set<PlayerClass> marksman = Set.of(MARKSMAN), bench = Set.of(MARKSMAN, TINKERER);
		ClassTrees.add(Workshop.AIM, marksman, 3, 2, 0, 0, 0, List.of(), List.of());
		ClassTrees.add(Workshop.LOAD, marksman, 1, 2, 5, 1, 0, List.of(), List.of());
		ClassTrees.add(Workshop.CALLED, marksman, 1, 3, 10, 2, 0, List.of(Workshop.AIM), List.of(Workshop.QUICK));
		ClassTrees.add(Workshop.QUICK, marksman, 1, 3, 10, 2, 1, List.of(Workshop.AIM), List.of(Workshop.CALLED));
		ClassTrees.add(Workshop.PRECISE, marksman, 1, 3, 20, 5, 0, List.of(Workshop.AIM), List.of());
		ClassTrees.add(Workshop.SMITH, bench, 1, 2, 0, 0, 2, List.of(), List.of());
		ClassTrees.add(Workshop.PARTS, bench, 3, 2, 3, 1, 2, List.of(Workshop.SMITH), List.of());
		ClassTrees.add(Workshop.TRICK, bench, 1, 2, 5, 2, 2, List.of(), List.of());
		ClassTrees.add(Workshop.KIT, bench, 1, 2, 8, 3, 2, List.of(), List.of());
		// patch 8: the extras
		ClassTrees.add(WarExtras.POWDER, marksman, 1, 2, 12, 3, 0, List.of(), List.of());
		ClassTrees.add(WarExtras.SHARP, marksman, 1, 3, 15, 4, 0, List.of(), List.of(WarExtras.SLINGER));
		ClassTrees.add(WarExtras.SLINGER, marksman, 1, 3, 15, 4, 1, List.of(), List.of(WarExtras.SHARP));
	}
}
