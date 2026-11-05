package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.ClassPerks;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.600) Andy: "is battleaxe supposed to not parry? and it also has no moves, neither does the ninjato, they should
 * all integrate with the weapon arts". The RPG Series' weapon skills (Swift Strikes, Whirlwind, Cleave, Smash...) sit on
 * Spell Engine's skill bar beside the Weapon Forms ({@link FormSkills}), but Spell Engine hands them out by matching item
 * ids against a short list (sword|blade, war_axe, spear, dagger|knife...), and only to items that carry the vanilla weapon
 * component. So a battleaxe ({@code weaponmod:battleaxe.iron}), a ninjato, a saber, a machete, a cleaver, a katana, a
 * halberd, a rapier, a scythe and more got no skill at all.
 * <p>
 * After Spell Engine has handed out its own ({@code SpellAssignmentsMixin}, as the server starts), every melee weapon
 * still without a skill gets the one its kind fits ({@link #skillFor}): the first matching row of {@link #ROWS}, or Swift
 * Strikes for any other blade. Tools, ranged and thrown-only weapons, and items with a spell container of their own
 * (wands, staves, the Rogues' weapons) are left alone. Spell Engine syncs the result to every client.
 */
public final class WeaponSkills {
	/** The RPG Series' weapon skills, by Spell Engine's own ids ({@code rpg_series:<name>}). */
	public static final String NAMESPACE = "rpg_series";
	static final String SWIFT_STRIKES = "swift_strikes", FLURRY = "flurry", WHIRLWIND = "whirlwind", GROUND_SLAM = "ground_slam",
		THRUST = "thrust", IMPALE = "impale", SWIPE = "swipe", FAN_OF_KNIVES = "fan_of_knives", SMASH = "smash", CLEAVE = "cleave";

	/** Never a melee weapon here: tools, ranged and thrown-only things (matched against the item's id path). */
	private static final Pattern NOT_MELEE = Pattern.compile("pickaxe|shovel|(^|_)hoe$|excavator|mattock|pickadze|vein_hammer|tongs"
		+ "|smithing_hammer|(^|_)saw$|brush|shears|fishing|boomerang|throwing_star|shuriken|throwing_axe|dynamite|bomb|musket"
		+ "|blunderbuss|bow$|blowgun|mortar|firerod|dart|arrow|bolas");

	/** A kind of weapon and its skill; the first row whose pattern the id path finds wins. */
	private record Row(Pattern pattern, String skill) {
		Row(String regex, String skill) {
			this(Pattern.compile(regex), skill);
		}
	}

	private static final List<Row> ROWS = List.of(
		new Row("claymore|great_?sword|zweihander", FLURRY),
		new Row("battle_?axe|double_?axe|war_?axe|great_?axe|broad_axe|minotaur_axe", WHIRLWIND),
		new Row("war_?hammer|great_?hammer|maul|sledge|anvil_fathers_hammer|(^|_)hammer$", GROUND_SLAM),
		new Row("halberd|glaive|poleaxe|partisan|tidecaller|rapier|skewer|estoc", THRUST),
		new Row("spear|lance|javelin|harpoon|pike|trident", IMPALE),
		new Row("scythe|sickle|kama|hook|claw|fang_gauntlets", SWIPE),
		new Row("dagger|knife|kunai|tanto|stiletto|hidden_blade|ashfang", FAN_OF_KNIVES),
		new Row("mace|flail|cudgel|club|morning_?star|hearthguard|battlesign|quarterstaff|throwing_stick", SMASH),
		new Row("cleaver|hatchet|axe", CLEAVE));

	/** WeaponMod's melee weapons, which carry no weapon component of their own (Jaymo's Powers adds it when present). */
	private static final Pattern WEAPONMOD_MELEE = Pattern.compile("^(knife|katana|battleaxe|halberd|spear|warhammer|flail)\\.");

	private WeaponSkills() {
	}

	/**
	 * The weapon skill (a spell id) {@code item} should carry when Spell Engine gave it none, or null when it isn't a melee
	 * weapon this fills in. {@code ownContainer}: the item has a spell container of its own.
	 */
	public static @Nullable Identifier skillFor(Item item, boolean ownContainer) {
		if (ownContainer || item instanceof ProjectileWeaponItem) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		String path = id.getPath().toLowerCase(Locale.ROOT);
		DataComponentMap components = item.components();
		boolean weaponMod = id.getNamespace().equals("weaponmod") && WEAPONMOD_MELEE.matcher(path).find();
		boolean melee = weaponMod || components.has(DataComponents.WEAPON) || item.builtInRegistryHolder().is(ItemTags.AXES);
		if (!melee || NOT_MELEE.matcher(path).find() || components.has(DataComponents.CONSUMABLE) || components.has(DataComponents.EQUIPPABLE)
			|| item.builtInRegistryHolder().is(ClassPerks.GUNS)) {
			return null;
		}
		for (Row row : ROWS) {
			if (row.pattern().matcher(path).find()) {
				return Identifier.fromNamespaceAndPath(NAMESPACE, row.skill());
			}
		}
		return Identifier.fromNamespaceAndPath(NAMESPACE, SWIFT_STRIKES);
	}
}
