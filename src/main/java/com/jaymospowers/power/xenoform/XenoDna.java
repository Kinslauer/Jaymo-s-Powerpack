package com.jaymospowers.power.xenoform;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.99, Scan A of {@code docs/ScanGaps-Plan.md}) Who carries which alien's DNA beyond what each form's own
 * {@link XenoForm#scans} knows, and who carries none. Mobs from other mods (Naturalist, Friends & Foes, Enderscape, MCA)
 * and from Kingdoms are matched by entity id, so a mod that isn't loaded simply never matches. Checked before the forms,
 * so a mob built on another mob's class (a Kingdoms redstone golem is an iron golem underneath) can't hand out the wrong
 * alien, as the clockwork golem once gave Bulwark.
 */
public final class XenoDna {
	/** Extra DNA: entity id to alien id. Andy's ticks on the scan-gaps list (2026-10-31). */
	private static final Map<String, String> EXTRA = Map.ofEntries(
		// Wyrm: dragons and a winged knight
		Map.entry("jaymoskingdoms:aeolus_dragon", "wyrm"), Map.entry("jaymoskingdoms:boreas_dragon", "wyrm"),
		Map.entry("jaymoskingdoms:eurus_dragon", "wyrm"), Map.entry("jaymoskingdoms:notus_dragon", "wyrm"),
		Map.entry("jaymoskingdoms:zephyrus_dragon", "wyrm"), Map.entry("naturalist:komodo_dragon", "wyrm"),
		Map.entry("jaymospowers:visage_of_war", "wyrm"),
		// Bulwark: bears
		Map.entry("naturalist:bear", "bulwark"), Map.entry("naturalist:black_bear", "bulwark"),
		// Velox: a runner bird
		Map.entry("naturalist:ostrich", "velox"),
		// Gremlin: small tricksters
		Map.entry("naturalist:lizard", "gremlin"), Map.entry("friendsandfoes:rascal", "gremlin"),
		Map.entry("jaymoskingdoms:loot_goblin", "gremlin"),
		// Duplex: the other copper golem
		Map.entry("friendsandfoes:copper_golem", "duplex"),
		// Prism: stone
		Map.entry("friendsandfoes:tuff_golem", "prism"), Map.entry("jaymoskingdoms:gorgon", "prism"),
		// Pyre: blaze kin
		Map.entry("friendsandfoes:wildfire", "pyre"),
		// Gorger: shelled things that roll and dash
		Map.entry("enderscape:rubblemite", "gorger"), Map.entry("naturalist:giant_isopod", "gorger"),
		// Slipfin: fish
		Map.entry("naturalist:bass", "slipfin"), Map.entry("naturalist:catfish", "slipfin"),
		Map.entry("naturalist:blobfish", "slipfin"),
		// Wisp: spirits
		Map.entry("mca:grim_reaper", "wisp"), Map.entry("mca:ancient_cultist", "wisp"),
		Map.entry("jaymoskingdoms:wraith", "wisp"), Map.entry("jaymoskingdoms:flame_wraith", "wisp"),
		Map.entry("jaymoskingdoms:haunted_stand", "wisp"),
		// Knell: the deep dark's golem
		Map.entry("jaymoskingdoms:skulk_golem", "knell"),
		// Rivet: machines
		Map.entry("jaymoskingdoms:redstone_golem", "rivet"), Map.entry("jaymoskingdoms:mimic", "rivet"),
		Map.entry("jaymospowers:android_mech", "rivet"), Map.entry("jaymospowers:future_mech", "rivet"),
		Map.entry("jaymospowers:omen_blue", "rivet"), Map.entry("jaymospowers:omen_red", "rivet"),
		Map.entry("jaymospowers:future_android", "rivet"), Map.entry("jaymospowers:worky_drone", "rivet"),
		Map.entry("jaymospowers:test_unit", "rivet"));

	/**
	 * No alien DNA at all: people, the walking dead, and plain farm animals. None of these is scanned today, but writing
	 * them down keeps a later alien's {@code scans} (a class check, say) from picking them up by accident.
	 */
	private static final Set<String> NONE = Set.of(
		// farm animals
		"minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:rabbit", "naturalist:turkey", "naturalist:duck",
		"naturalist:capybara",
		// the walking dead
		"minecraft:zombie", "minecraft:drowned", "minecraft:zombie_villager", "minecraft:zombified_piglin",
		"minecraft:giant", "mca:female_zombie_villager", "mca:male_zombie_villager", "jaymoskingdoms:zombified_hornkin",
		// people: illagers, piglins, villagers, folk
		"minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:witch", "minecraft:vex",
		"minecraft:piglin", "minecraft:piglin_brute", "minecraft:villager", "minecraft:wandering_trader",
		"mca:female_villager", "mca:male_villager", "jaymoskingdoms:folk", "jaymoskingdoms:stranger",
		"jaymoskingdoms:hornkin", "jaymoskingdoms:hornkin_brute", "jaymoskingdoms:hornkin_hurler", "jaymoskingdoms:hornkin_warlord",
		"jaymoskingdoms:piglin_king", "jaymoskingdoms:night_bleeder",
		// Andy's "no scan" calls
		"jaymospowers:demon_soldier", "jaymospowers:demon_captain", "jaymospowers:aerbunny");

	/** Kingdoms' named folk all end in these ({@code guard_of}, {@code king_of}, {@code ruler_titled}...): people, never scanned. */
	private static final String[] FOLK_SUFFIXES = {"_of", "_titled"};

	/** Our own people (the Judge, the Keeper, the Hermit, patrolmen, raiders...): never scanned. */
	private static final Set<String> OUR_PEOPLE = Set.of("afterlife_judge", "afterlife_master", "keeper", "hermit", "overseer",
		"fortune_teller", "scientist", "raider", "rogue_fighter", "spirit_fighter", "sparring_partner", "practice_dummy");

	private XenoDna() {
	}

	/** What this table says {@code creature} carries: an alien id, {@link #NONE_ID} for no DNA, or null to ask the forms. */
	static @Nullable String lookup(Entity creature) {
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType());
		String id = key.toString();
		String extra = EXTRA.get(id);
		if (extra != null) {
			return extra;
		}
		return NONE.contains(id) || people(key.getNamespace(), key.getPath()) ? NONE_ID : null;
	}

	/** {@link #lookup}'s answer for a creature with no alien DNA. */
	static final String NONE_ID = "";

	private static boolean people(String namespace, String path) {
		if ("jaymoskingdoms".equals(namespace)) {
			for (String suffix : FOLK_SUFFIXES) {
				if (path.endsWith(suffix)) {
					return true;
				}
			}
			return path.startsWith("caravan_");
		}
		return "jaymospowers".equals(namespace) && OUR_PEOPLE.contains(path);
	}
}
