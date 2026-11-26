package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
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

	/** How many draws a name no other settlement has is looked for, before the realm's number is put on the last one. */
	static final int UNIQUE_TRIES = 40;

	/**
	 * (Andy, 2026-11-26: the realm page showed two Wyrmarchs and two Ardengards) A kingdom name no other settlement in the
	 * world has, risen or fallen.
	 */
	static String kingdom(MinecraftServer server, RandomSource random) {
		String name = kingdom(random);
		for (int tries = 1; tries < UNIQUE_TRIES && Factions.byName(server, name) != null; tries++) {
			name = kingdom(random);
		}
		return Factions.byName(server, name) == null ? name : name + " " + Factions.nextId(server);
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(Names::renameTwins);
	}

	/**
	 * Realms named before names were kept apart: the younger of two standing realms of one name is named again, once, so no
	 * two realms on the realm page (or the map) share one.
	 */
	private static void renameTwins(MinecraftServer server) {
		Set<String> seen = new HashSet<>();
		java.util.List<Faction> realms = new java.util.ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen()) {
				realms.add(f);
			}
		}
		realms.sort(java.util.Comparator.comparingInt(Faction::id));
		RandomSource random = RandomSource.create(server.overworld().getSeed());
		for (Faction f : realms) {
			if (!seen.add(f.name().toLowerCase(Locale.ROOT))) {
				String name = kingdom(server, random);
				seen.add(name.toLowerCase(Locale.ROOT));
				JaymosKingdoms.LOGGER.info("Realm {} shared the name {} with an older realm; it is now {}", f.id(), f.name(), name);
				Factions.update(server, f.id(), x -> x.withName(name));
			}
		}
	}

	/** Village names: a homelier second half, like Brinbrook or Ashley. */
	private static final String[] HAMLET = {"brook", "ley", "ton", "by", "thorpe", "field", "well", "combe", "stow", "hurst", "worth", "bury",
		"ham", "wood", "green", "cross"};

	static String village(RandomSource random) {
		return FIRST[random.nextInt(FIRST.length)] + HAMLET[random.nextInt(HAMLET.length)];
	}

	/**
	 * (0.5.9) What a fiefdom's ruler is called: a baron, a count or a lord (the same every time for its fiefdom). (0.7.72:
	 * a count where he was a "vassal", which read as if he served his own fiefdom.)
	 */
	private static final String[] FIEF_TITLES = {"baron", "count", "lord"};

	static String fiefTitle(long seed) {
		return FIEF_TITLES[RandomSource.create(seed ^ 0xF1EFL).nextInt(FIEF_TITLES.length)];
	}

	/** (0.5.9) Barbarian chiefs' names: Slavic and Norse, now and then with a byname. */
	private static final String[] CHIEFS = {"Bogdan", "Borislav", "Dobromir", "Gorazd", "Jaromir", "Kazimir", "Mstislav", "Ratibor",
		"Radomir", "Svyatoslav", "Vratislav", "Zbigniew", "Miroslav", "Velimir", "Drahomir", "Stanimir", "Yaropolk", "Boleslav", "Vseslav",
		"Ragnar", "Bjorn", "Halvard", "Ivar", "Sigurd", "Thorvald", "Ulfric", "Eirik", "Gunnar", "Hakon", "Ketil", "Leif", "Orm", "Sten",
		"Torbjorn", "Vidar", "Asmund", "Egil", "Hrolf", "Skarde", "Grimolf"};
	/** How often a chief has a byname (one in this many), and the bynames. */
	static final int BYNAME_ONE_IN = 8;
	private static final String[] BYNAMES = {"the Terrible", "the Cruel", "the Red", "the Grim", "the Black", "Ironhand", "Bloodaxe",
		"the Wolf", "Skullsplitter", "the Unbowed", "the Burner", "Bonebreaker"};

	static String chief(long seed) {
		RandomSource random = RandomSource.create(seed ^ 0xC41EFL);
		String name = CHIEFS[random.nextInt(CHIEFS.length)];
		return random.nextInt(BYNAME_ONE_IN) == 0 ? name + " " + BYNAMES[random.nextInt(BYNAMES.length)] : name;
	}
}
