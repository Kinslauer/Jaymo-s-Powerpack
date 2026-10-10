package com.jaymoskingdoms;

import net.minecraft.util.RandomSource;

/** Kingdom names: an old-sounding first half and a second, like Ashmere or Valdhollow. */
final class Names {
	private static final String[] FIRST = {"Ash", "Bram", "Cael", "Dun", "Eld", "Fen", "Gar", "Hal", "Iver", "Kel", "Lorn", "Mar", "Nor", "Os",
		"Ravn", "Stor", "Thal", "Ul", "Vald", "Wyr", "Arden", "Brin", "Corv", "Dra", "Fal", "Gwyn", "Hed", "Mor", "Sel", "Tor"};
	private static final String[] SECOND = {"mere", "hollow", "gard", "wick", "ford", "holm", "vale", "reach", "march", "haven", "keep", "moor",
		"crest", "fell", "wold", "stead", "burgh", "mark", "shire", "dale"};

	private Names() {
	}

	static String kingdom(RandomSource random) {
		return FIRST[random.nextInt(FIRST.length)] + SECOND[random.nextInt(SECOND.length)];
	}
}
