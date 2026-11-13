package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.player.Player;

/**
 * What the three casting classes of the second set (Cleric, Druid, Warlock) may learn and how they cast. Each carries a
 * focus in the hand ({@link CastingFocus}: a holy symbol, a druid's sprig, a pact seal) that casts the spell it is turned to,
 * as a Mage's staff does ({@link Spellcraft}); each reads scrolls of the spells their calling allows and learns them for
 * keeps, and any other scroll is read once like anyone's. Healing prayers and nature's rites are the same spells as
 * everyone's (restoration, sanctuary, moonbeam, plant growth ...), not copies.
 *
 * <p>Other patches add to a class's list with {@link #allow} (the revive prayer of the death patch is a Cleric's spell, id
 * {@link #REVIVE}).
 */
public final class ClassCasters {
	/** The id the revive prayer takes when it exists; a Cleric may learn it. */
	public static final String REVIVE = "calling_back";
	/** How much harder a Cleric's, a Druid's and a Warlock's spells are than a scroll's (the Mage's is {@link Spellcraft#MAGE_POWER}). */
	static final float CLERIC_POWER = 1.4F, DRUID_POWER = 1.4F;
	/** A Warlock's wait between casts as a share of a Mage's: their magic comes back fast. */
	static final float WARLOCK_COOLDOWN = 0.4F;

	private static final Map<PlayerClass, Set<String>> ALLOWED = new EnumMap<>(PlayerClass.class);
	/** What a Druid and a Cleric are born knowing as rituals (cast from the Magic screen without a scroll). */
	private static final Map<PlayerClass, Set<String>> BORN = new EnumMap<>(PlayerClass.class);

	static {
		ALLOWED.put(PlayerClass.CLERIC, new HashSet<>(Set.of("mending", "sanctuary", "restoration", "warding", "bulwark", "dispel", "counterspell", "ward_line",
			"purify_food", "create_food", "heroes_feast", "zone_of_truth", "augury", "find_magic", "identify", "locate_object", "feather_fall", "keeping", REVIVE)));
		ALLOWED.put(PlayerClass.DRUID, new HashSet<>(Set.of("plant_growth", "moonbeam", "control_weather", "speak_animals", "heroes_feast", "mending",
			"feather_fall", "spider_climb", "water_walk", "long_jump", "spike_growth", "web", "grease", "fog", "tide_pearl", "harrying_gale", "purify_food",
			"create_food", "find_magic", "locate_creature", "hut", "rebirth")));
		ALLOWED.put(PlayerClass.WARLOCK, new HashSet<>(Set.of("arcane", "fire", "draining", "darkness", "silence", "web", "fog", "misty_step", "charm", "command",
			"hold", "suggestion", "geas", "counterspell", "dispel", "grease", "find_magic", "identify", "whispers", "blight")));
		BORN.put(PlayerClass.CLERIC, Set.of("dispel", "identify", "create_food", "purify_food"));
		BORN.put(PlayerClass.DRUID, Set.of("speak_animals", "control_weather", "plant_growth", "moonbeam"));
	}

	private ClassCasters() {
	}

	/** Lets {@code cls} learn {@code spell} from a scroll (for other patches' spells). */
	public static void allow(PlayerClass cls, String spell) {
		ALLOWED.computeIfAbsent(cls, c -> new HashSet<>()).add(spell);
	}

	/** Whether {@code player} is a Cleric, a Druid or a Warlock. */
	public static boolean caster(Player player) {
		PlayerClass cls = Classes.classOf(player);
		return cls == PlayerClass.CLERIC || cls == PlayerClass.DRUID || cls == PlayerClass.WARLOCK;
	}

	/** Whether {@code player}'s mana is a natural's (a Cleric's or a Druid's), like a Mage's. */
	public static boolean natural(Player player) {
		PlayerClass cls = Classes.classOf(player);
		return cls == PlayerClass.CLERIC || cls == PlayerClass.DRUID;
	}

	/** The spells {@code cls} begins with; a Warlock's second is the patron's own ({@link Warlocks#patronSpell}). */
	static List<String> starting(PlayerClass cls, String option) {
		List<String> out = new ArrayList<>();
		switch (cls) {
			case CLERIC -> out.add("mending");
			case DRUID -> {
				out.add("plant_growth");
				out.add("moonbeam");
			}
			case WARLOCK -> {
				out.add("arcane");
				out.add(Warlocks.patronSpell(option));
			}
			default -> {
			}
		}
		return List.copyOf(out);
	}

	/** Whether {@code player}'s calling lets them learn {@code spell} from a scroll (anyone else's scroll is read once). */
	public static boolean mayLearn(Player player, String spell) {
		PlayerClass cls = Classes.classOf(player);
		if (cls == null || !ALLOWED.containsKey(cls)) {
			return true;
		}
		return ALLOWED.get(cls).contains(spell);
	}

	/** Whether {@code player} is born knowing the ritual {@code spell} ({@link NewSpells#knows}). */
	public static boolean born(Player player, String spell) {
		PlayerClass cls = Classes.classOf(player);
		return cls != null && BORN.getOrDefault(cls, Set.of()).contains(spell);
	}

	/** How hard {@code player}'s staff and focus spells hit as a share of a scroll's. */
	static float power(Player player, boolean mage, float attuned) {
		PlayerClass cls = Classes.classOf(player);
		if (cls == null || mage) {
			return mage ? Spellcraft.MAGE_POWER : attuned;
		}
		return switch (cls) {
			case CLERIC -> CLERIC_POWER;
			case DRUID -> DRUID_POWER;
			case WARLOCK -> Warlocks.POWER;
			default -> attuned;
		};
	}

	/** How long a cast waits as a share of the spell's own cooldown. */
	static float cooldown(Player player, boolean mage, float attuned) {
		PlayerClass cls = Classes.classOf(player);
		if (mage || cls == PlayerClass.CLERIC || cls == PlayerClass.DRUID) {
			return 1.0F;
		}
		return cls == PlayerClass.WARLOCK ? WARLOCK_COOLDOWN : attuned;
	}
}
