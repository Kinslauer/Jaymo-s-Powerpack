package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Skills.Perk;
import com.jaymoskingdoms.forms.Forms;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.516, RPG Series patch 2) Andy: the class skills of Zsolt Molnar's Wizards, Archers and Rogues &amp; Warriors and the
 * Weapon Forms are learned only on the skill web or from a trainer ("only through perks or the guards that teach you
 * moves"; "you can still ask for drills to learn under a trainer"). Their spell books, scrolls and Spell Binding Table
 * teach nothing.
 * <ul>
 * <li>Each spell and each form is one perk ({@link Skills.Perk}: Archery and Spellcraft in Survival, the Rogue's Arts and
 * Knife Arts in Stealth, Warfare and Weapon Forms in Strength). A spell's perk is only for the classes that cast it
 * ({@link RpgSeriesGate#casters}); a form's is anyone's.</li>
 * <li>A perk learned puts its spell on Spell Engine's spell bar ({@link #knowsSpell}, read by {@link RpgSeriesGate}), or
 * teaches its form ({@link Forms#teach}).</li>
 * <li>A trainer's drill (a veteran guard's, {@code GuardDrills}; a court wizard's trial for a Mage, {@code WizardTrials})
 * teaches the next skill free ({@link #teachNext}): the perk is marked taught ({@link #taughtKey}), costs no points, and
 * stays through a respec. A form taught any other way (a school, a command) is marked taught the same way.</li>
 * <li>The wands' and staffs' own bolts (tier 0 and 1 spells, {@link #INNATE}) need no perk: any Mage casts them.</li>
 * </ul>
 */
public final class Learnables {
	/** Spells that come with the weapon (wands' and staffs' own): no perk needed. */
	static final Set<String> INNATE = Set.of("wizards:arcane_bolt", "wizards:arcane_blast", "wizards:fire_scorch", "wizards:fireball",
		"wizards:fire_blast", "wizards:frost_shard", "wizards:frostbolt");

	private static final Map<Perk, String> SPELLS = new EnumMap<>(Perk.class);
	private static final Map<Perk, String> FORMS = new EnumMap<>(Perk.class);
	private static final Map<String, Perk> BY_SPELL = new HashMap<>();
	private static final Map<String, Perk> BY_FORM = new HashMap<>();

	static {
		for (Perk perk : List.of(Perk.POWER_SHOT, Perk.ENTANGLING_ROOTS, Perk.BARRAGE, Perk.SPIRIT_WOLF, Perk.MAGIC_ARROW, Perk.RAIN_OF_ARROWS)) {
			spell(perk, "archers:" + perk.key());
		}
		for (Perk perk : List.of(Perk.ARCANE_MISSILE, Perk.ARCANE_EXPLOSION, Perk.ARCANE_BEAM, Perk.ARCANE_BARRAGE, Perk.ARCANE_BLINK,
			Perk.ARCANE_EVOCATION, Perk.FIRE_BREATH, Perk.FIRE_SLASH, Perk.FIRE_METEOR, Perk.FIRE_STORM, Perk.FIRE_WALL, Perk.FIRE_HYDRA,
			Perk.FROST_NOVA, Perk.FROST_SPIKES, Perk.FROST_SHIELD, Perk.FROST_LANCE, Perk.FROST_BLIZZARD, Perk.FROST_ELEMENTAL)) {
			spell(perk, "wizards:" + perk.key());
		}
		for (Perk perk : List.of(Perk.SLICE_AND_DICE, Perk.SHOCK_POWDER, Perk.MUTILATE, Perk.BEAR_TRAP, Perk.SHADOW_STEP, Perk.VANISH,
			Perk.THROW, Perk.SHOUT, Perk.THROW_NET, Perk.MORTAL_STRIKE)) {
			spell(perk, "rogues:" + perk.key());
		}
		spell(Perk.WARRIOR_CHARGE, "rogues:charge");
		spell(Perk.WARRIOR_LAST_STAND, "rogues:last_stand");
		for (Perk perk : List.of(Perk.MARKED_QUARRY, Perk.TANGLE_CORD, Perk.CAST_OFF_CLOAK, Perk.SHEATH_DRAW, Perk.CRESCENT_SWEEP,
			Perk.RAIN_OF_CUTS, Perk.GROUND_STOMP, Perk.FALLING_FIST, Perk.RENDING_CLAW, Perk.FERAL_POUNCE, Perk.SPEAR_LUNGE,
			Perk.COUCHED_LANCE, Perk.PINNING_THROW)) {
			FORMS.put(perk, perk.key());
			BY_FORM.put(perk.key(), perk);
		}
	}

	private Learnables() {
	}

	private static void spell(Perk perk, String id) {
		SPELLS.put(perk, id);
		BY_SPELL.put(id, perk);
	}

	/** The spell {@code perk} teaches ("namespace:id"), or null. */
	public static @Nullable String spellOf(Perk perk) {
		return SPELLS.get(perk);
	}

	/** The form {@code perk} teaches, or null. */
	public static @Nullable String formOf(Perk perk) {
		return FORMS.get(perk);
	}

	/** The key that marks a rank of {@code perk} as a trainer's gift (free, kept through a respec). */
	public static String taughtKey(Perk perk) {
		return perk.key() + "@taught";
	}

	/** The classes that may learn {@code perk}, or null when anyone may. */
	public static @Nullable Set<PlayerClass> classesFor(Perk perk) {
		if (perk == Perk.ENCHANTED_STORAGE) {
			return Set.of(PlayerClass.MAGE);
		}
		String spell = SPELLS.get(perk);
		return spell == null ? null : RpgSeriesGate.casters(Identifier.parse(spell));
	}

	/** Whether {@code perk} is another class's skill for {@code player} (creative players may learn anything). */
	public static boolean classLocked(Player player, Perk perk) {
		Set<PlayerClass> classes = classesFor(perk);
		if (classes == null || player.isCreative()) {
			return false;
		}
		PlayerClass cls = Classes.classOf(player);
		return cls == null || !classes.contains(cls);
	}

	/** Whether {@code player} knows {@code spell} (a perk learned or taught), or needs not (a weapon's own, any other mod's). */
	public static boolean knowsSpell(Player player, Identifier spell) {
		String id = spell.toString();
		if (INNATE.contains(id)) {
			return true;
		}
		Perk perk = BY_SPELL.get(id);
		return perk == null || Skills.of(player).rank(perk) > 0;
	}

	/** The RPG Series spells {@code player} has learned, for Spell Engine's spell bar. */
	public static List<String> spells(Player player) {
		Skills.Progress progress = Skills.of(player);
		List<String> out = new ArrayList<>();
		for (Map.Entry<Perk, String> e : SPELLS.entrySet()) {
			if (progress.rank(e.getKey()) > 0) {
				out.add(e.getValue());
			}
		}
		return out;
	}

	/** A perk was bought: its form is taught. */
	static void spent(ServerPlayer player, Perk perk) {
		String form = FORMS.get(perk);
		if (form != null) {
			Forms.teach(player, form);
		}
	}

	/** Gives {@code player} a rank of {@code perk} for free, as a trainer's gift. False if they had it. */
	static boolean giveFree(ServerPlayer player, Perk perk) {
		Skills.Progress p = Skills.of(player);
		if (p.rank(perk) > 0) {
			return false;
		}
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		ranks.put(perk.key(), 1);
		ranks.put(taughtKey(perk), 1);
		player.setAttached(Skills.PROGRESS, new Skills.Progress(p.xp(), Map.copyOf(ranks)));
		return true;
	}

	/** (Forms) A form was taught some way other than its perk: its perk is marked taught too, free. */
	public static void formTaught(ServerPlayer player, String formId) {
		Perk perk = BY_FORM.get(formId);
		if (perk != null) {
			giveFree(player, perk);
		}
	}

	/** Forms {@code player} knows that their perks don't show yet (taught before the web had them) get marked taught. */
	static void sync(ServerPlayer player) {
		for (Map.Entry<String, Perk> e : BY_FORM.entrySet()) {
			if (Forms.knows(player, e.getKey()) && Skills.of(player).rank(e.getValue()) == 0) {
				giveFree(player, e.getValue());
			}
		}
	}

	/** Before a respec: forms bought with points (not taught) are unlearned. */
	static void unlearnBought(ServerPlayer player) {
		Skills.Progress p = Skills.of(player);
		for (Map.Entry<Perk, String> e : FORMS.entrySet()) {
			if (p.rank(e.getKey()) > 0 && !p.ranks().containsKey(taughtKey(e.getKey()))) {
				Forms.forget(player, e.getValue());
			}
		}
	}

	/** Of {@code ranks}, just the taught ranks and their markers (what a respec keeps). */
	static Map<String, Integer> taughtOnly(Map<String, Integer> ranks) {
		Map<String, Integer> out = new HashMap<>();
		for (Perk perk : Perk.values()) {
			if (ranks.containsKey(taughtKey(perk))) {
				out.put(perk.key(), 1);
				out.put(taughtKey(perk), 1);
			}
		}
		return Map.copyOf(out);
	}

	/**
	 * A trainer teaches {@code player} the next class skill they can: the first spell perk (in the web's order) of their
	 * class they don't know whose needs they have, in the pillar's branch {@code mage} picks (Spellcraft for a court
	 * wizard, the others for a veteran guard). Returns the spell's name key, or null when there's nothing to teach.
	 */
	public static @Nullable String teachNext(ServerPlayer player, boolean mage) {
		Skills.Progress p = Skills.of(player);
		for (Map.Entry<Perk, String> e : SPELLS.entrySet()) {
			Perk perk = e.getKey();
			boolean wizards = e.getValue().startsWith("wizards:");
			if (wizards != mage || p.rank(perk) > 0 || classLocked(player, perk)) {
				continue;
			}
			if (perk.requires.stream().allMatch(need -> p.rank(need) > 0) && giveFree(player, perk)) {
				return "skill.jaymoskingdoms.perk." + perk.key();
			}
		}
		return null;
	}
}
