package com.jaymoskingdoms.trees;

import static com.jaymoskingdoms.Classes.PlayerClass.RIDER;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.equestrian.Horsemanship;
import java.util.List;
import java.util.Set;

/**
 * The Rider's saddle ({@link Horsemanship}): Long Wind and Horse Whisperer, Lancer or Horse Archer at 5, Stay in the Saddle,
 * Trick Riding, Cavalier or Outrider at 15, Herd Master, Banner Bearer, and Bonded or Beast of Legend at the top. Spur (the
 * class key at the gallop) is the Rider's from the start and costs no points.
 */
final class RiderTree {
	private RiderTree() {
	}

	static void build() {
		Set<PlayerClass> rider = Set.of(RIDER);
		ClassTrees.add(Horsemanship.WIND, rider, 3, 2, 0, 0, 2, List.of(), List.of());
		ClassTrees.add(Horsemanship.WHISPER, rider, 1, 2, 3, 1, 2, List.of(), List.of());
		ClassTrees.add(Horsemanship.LANCER_KEY, rider, 1, 3, 5, 2, 1, List.of(), List.of(Horsemanship.ARCHER));
		ClassTrees.add(Horsemanship.ARCHER, rider, 1, 3, 5, 2, 3, List.of(), List.of(Horsemanship.LANCER_KEY));
		ClassTrees.add(Horsemanship.STAY, rider, 1, 2, 8, 3, 2, List.of(), List.of());
		ClassTrees.add(Horsemanship.TRICK, rider, 1, 2, 10, 4, 2, List.of(Horsemanship.STAY), List.of());
		ClassTrees.add(Horsemanship.CAVALIER, rider, 1, 3, 15, 5, 1, List.of(), List.of(Horsemanship.OUTRIDER));
		ClassTrees.add(Horsemanship.OUTRIDER, rider, 1, 3, 15, 5, 3, List.of(), List.of(Horsemanship.CAVALIER));
		ClassTrees.add(Horsemanship.HERD, rider, 1, 2, 18, 6, 2, List.of(), List.of());
		ClassTrees.add(Horsemanship.BANNER, rider, 1, 3, 20, 7, 2, List.of(), List.of());
		ClassTrees.add(Horsemanship.BONDED, rider, 1, 3, 24, 8, 1, List.of(), List.of(Horsemanship.LEGEND));
		ClassTrees.add(Horsemanship.LEGEND, rider, 1, 3, 24, 8, 3, List.of(), List.of(Horsemanship.BONDED));
	}
}
