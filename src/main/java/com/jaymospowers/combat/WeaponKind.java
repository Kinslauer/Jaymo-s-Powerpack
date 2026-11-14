package com.jaymospowers.combat;

import com.jaymospowers.compat.BetterCombatWeapons;
import com.jaymospowers.core.ArmourKind;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Fractures;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What kind of weapon is in the hand, for the two things that depend on it ({@link Blows}): the distance it hits hardest
 * at, and how its blow meets armour. One answer for both, the same on both sides.
 * <p>
 * A weapon's kind is read from Better Combat's category for it when that mod is loaded and knows the weapon (the pack
 * files every weapon there: {@link BetterCombatWeapons#category}), else from what the rest of the mod already says of
 * it: a polearm by {@link Armed#kind}, a bone-breaker by {@link Fractures#blunt}, a blade by {@link Dismember#blade},
 * and its name for the rest. Tools you dig with and bare hands have no kind.
 * <ul>
 * <li><b>Its distance</b>: {@link #near} to {@link #far}, as shares of the weapon's own reach. Daggers, claws and fist
 * weapons want to be close; swords, axes and maces the middle; staves, polearms and spears want room.</li>
 * <li><b>Its blow</b> ({@link Blow}): a cut, a thrust or a blunt blow, or two of them at once (an axe cuts and crushes, a
 * halberd cuts and thrusts), each meeting padding, mail and plate its own way.</li>
 * </ul>
 */
public enum WeaponKind {
	// ===== the knobs: each kind's best distance (shares of its reach), and its blow =================================
	DAGGER(0.0F, 0.6F, Blow.THRUST),
	CLAW(0.0F, 0.6F, Blow.CUT),
	FIST(0.0F, 0.6F, Blow.BLUNT),
	SWORD(0.35F, 0.85F, Blow.CUT),
	RAPIER(0.4F, 0.9F, Blow.THRUST),
	GREATSWORD(0.45F, 0.9F, Blow.CUT),
	AXE(0.3F, 0.8F, Blow.CUT, Blow.BLUNT),
	MACE(0.3F, 0.8F, Blow.BLUNT),
	HAMMER(0.4F, 0.88F, Blow.BLUNT),
	STAFF(0.5F, 0.92F, Blow.BLUNT),
	POLEARM(0.55F, 0.95F, Blow.CUT, Blow.THRUST),
	SPEAR(0.6F, 0.97F, Blow.THRUST);
	// ================================================================================================================

	/** How a blow lands, and what each armour makes of it: the damage it does through padding, mail and plate. */
	public enum Blow {
		// ===== the knobs: against padding, mail, plate ==============================================================
		/** Mail turns it, plate mostly does, padding parts for it. */
		CUT(1.15F, 0.6F, 0.7F),
		/** Finds the gaps: best against mail, some against plate. */
		THRUST(1.0F, 1.25F, 1.1F),
		/** Crushes plate, bruises through mail, and is soaked up by padding. */
		BLUNT(0.75F, 1.1F, 1.35F);
		// ============================================================================================================

		private final float cloth, mail, plate;

		Blow(float cloth, float mail, float plate) {
			this.cloth = cloth;
			this.mail = mail;
			this.plate = plate;
		}

		/** The share of its damage this blow does against {@code armour} (1 against none). */
		public float against(ArmourKind armour) {
			return switch (armour) {
				case CLOTH -> cloth;
				case MAIL -> mail;
				case PLATE -> plate;
				case NONE -> 1.0F;
			};
		}
	}

	/** Words in an item's id for the kinds the other classifiers don't tell apart. */
	private static final String[] DAGGER_WORDS = {"dagger", "knife", "kunai", "tanto", "stiletto", "dirk"},
		CLAW_WORDS = {"claw", "fang_gauntlets", "talon", "sickle", "kama"},
		FIST_WORDS = {"cestus", "knuckle", "gauntlet"},
		RAPIER_WORDS = {"rapier", "estoc", "skewer", "foil"},
		GREAT_WORDS = {"claymore", "greatsword", "great_sword", "zweihander", "longsword", "bastardsword"},
		SPEAR_WORDS = {"spear", "lance", "javelin", "trident", "harpoon"},
		HAMMER_WORDS = {"hammer", "maul", "sledge", "anchor"},
		AXE_WORDS = {"axe", "hatchet", "cleaver"};

	/** The band it hits hardest in: from this share of its reach to this one. */
	public final float near, far;
	private final Blow[] blows;

	WeaponKind(float near, float far, Blow... blows) {
		this.near = near;
		this.far = far;
		this.blows = blows;
	}

	/** The share of its damage this kind's blow does against {@code armour}: its blows' shares, evened out. */
	public float against(ArmourKind armour) {
		float sum = 0.0F;
		for (Blow blow : blows) {
			sum += blow.against(armour);
		}
		return sum / blows.length;
	}

	/** {@code weapon_kind.jaymospowers.<name>}: its blow and where it wants to be, on a weapon's tooltip. */
	public String key() {
		return "weapon_kind.jaymospowers." + name().toLowerCase(Locale.ROOT);
	}

	/** The kind of weapon {@code stack} is, or null if it isn't one. The same answer on both sides. */
	public static @Nullable WeaponKind of(ItemStack stack) {
		if (stack.isEmpty() || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
			return null;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
		if (path.contains("pickaxe")) {
			return null;
		}
		String category = BetterCombatWeapons.category(stack);
		if (category != null) {
			WeaponKind kind = ofCategory(category, stack, path);
			if (kind != null) {
				return kind;
			}
		}
		if (!stack.has(DataComponents.WEAPON) && !stack.has(DataComponents.PIERCING_WEAPON) && !stack.is(ItemTags.SWORDS) && !stack.is(ItemTags.AXES)) {
			return null;
		}
		if (has(path, DAGGER_WORDS)) {
			return DAGGER;
		}
		if (has(path, CLAW_WORDS)) {
			return CLAW;
		}
		if (has(path, FIST_WORDS)) {
			return FIST;
		}
		if (has(path, SPEAR_WORDS)) {
			return SPEAR;
		}
		boolean blunt = Fractures.blunt(stack);
		if (Armed.kind(stack) == Armed.Kind.POLE) {
			return blunt ? STAFF : POLEARM;
		}
		if (blunt) {
			return has(path, HAMMER_WORDS) ? HAMMER : MACE;
		}
		if (has(path, RAPIER_WORDS)) {
			return RAPIER;
		}
		if (stack.is(ItemTags.AXES) || has(path, AXE_WORDS)) {
			return AXE;
		}
		if (Dismember.blade(stack)) {
			return has(path, GREAT_WORDS) ? GREATSWORD : SWORD;
		}
		return null;
	}

	/** Better Combat's {@code category} as one of ours, or null for one that says nothing of the weapon (a pickaxe, a bow). */
	private static @Nullable WeaponKind ofCategory(String category, ItemStack stack, String path) {
		return switch (category) {
			case "dagger", "soul_knife" -> has(path, CLAW_WORDS) ? CLAW : DAGGER;
			case "claw", "sickle" -> CLAW;
			case "fist" -> FIST;
			case "sword", "cutlass", "katana", "coral_blade" -> has(path, RAPIER_WORDS) ? RAPIER : SWORD;
			case "rapier" -> RAPIER;
			case "claymore" -> GREATSWORD;
			case "axe", "heavy_axe", "double_axe" -> AXE;
			case "mace" -> MACE;
			case "hammer", "anchor" -> HAMMER;
			// (a staff with a blade on it, as the twinblade is filed, is a polearm)
			case "staff", "battlestaff", "wand" -> Dismember.blade(stack) ? POLEARM : STAFF;
			case "glaive", "halberd", "scythe", "twin_blade" -> POLEARM;
			case "spear", "lance", "trident" -> SPEAR;
			default -> null;
		};
	}

	private static boolean has(String path, String[] words) {
		for (String word : words) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}
}
