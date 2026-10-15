package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.Tinkering;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.69) Andy: "all weapons should be forged". Metal weapons (Armory's, Balkon's WeaponMod's, vanilla spears) have
 * no crafting recipe; they're cast instead. Pour gold over any weapon of a kind on a casting table (a wooden or stone
 * one will do) to make a Weapon Cast of that kind, then pour iron, gold or copper over the cast for that weapon in that
 * metal. Their crafting recipes are switched off in data (tools/armory_assets.py, tools/tinkering_assets.py) and
 * Balkon's by id ({@code tinkering/Uncraftable}). Kinds of mods that aren't loaded just never match.
 */
public final class WeaponCasts {
	/** How a kind names its tiers: Armory {@code iron_dagger}, Balkon {@code knife.iron}, or one item cast in iron only. */
	public enum Scheme { PREFIX, SUFFIX, SINGLE }

	/** A kind of weapon: its key (namespace:base), naming, metal it takes (mB) and how many one pour makes. */
	public record Kind(String key, Scheme scheme, int mb, int count) {
		public String namespace() {
			return key.substring(0, key.indexOf(':'));
		}

		public String base() {
			return key.substring(key.indexOf(':') + 1);
		}

		/** This weapon in this metal, or null if there's none (or the metal isn't one weapons are cast in). */
		public @Nullable Item in(Molten molten) {
			String path = switch (scheme) {
				case PREFIX -> tier(molten, true) == null ? null : tier(molten, true) + "_" + base();
				case SUFFIX -> tier(molten, false) == null ? null : base() + "." + tier(molten, false);
				case SINGLE -> molten == Molten.IRON ? base() : null;
			};
			if (path == null) {
				return null;
			}
			return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(namespace(), path)).orElse(null);
		}
	}

	private static final int INGOT = Molten.INGOT;
	/** The tier words in weapon ids (so a Tinkering cleaver isn't taken for an Armory one). */
	private static final Set<String> PREFIX_TIERS = Set.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite");
	private static final Set<String> SUFFIX_TIERS = Set.of("wood", "stone", "copper", "iron", "gold", "diamond", "netherite");
	/** Every kind, by key; costs are the ingots their old crafting recipes took. */
	public static final Map<String, Kind> KINDS = new LinkedHashMap<>();

	static {
		// Jaymo's Armory (round shields stay craftable, like iron armour)
		prefix("jaymospowers", "dagger", 1);
		prefix("jaymospowers", "hatchet", 2);
		prefix("jaymospowers", "skewer", 1);
		prefix("jaymospowers", "longsword", 2);
		prefix("jaymospowers", "glaive", 2);
		prefix("jaymospowers", "lance", 1);
		prefix("jaymospowers", "war_axe", 2);
		prefix("jaymospowers", "war_scythe", 2);
		prefix("jaymospowers", "flanged_mace", 4);
		prefix("jaymospowers", "broadsword", 4);
		prefix("jaymospowers", "claymore", 5);
		prefix("jaymospowers", "cleaver", 4);
		prefix("jaymospowers", "ninjato", 2);
		prefix("jaymospowers", "saber", 2);
		prefix("jaymospowers", "machete", 2);
		single("kunai", INGOT, 4);
		single("throwing_star", 4 * Molten.NUGGET, 2);
		single("harpoon", INGOT, 1);
		single("iron_quarterstaff", 2 * INGOT, 1);
		// vanilla spears
		prefix("minecraft", "spear", 1);
		// Balkon's WeaponMod (the musket bayonet is still a musket and a knife put together)
		suffix("knife", 1);
		suffix("spear", 1);
		suffix("halberd", 3);
		suffix("battleaxe", 5);
		suffix("warhammer", 4);
		suffix("katana", 2);
		suffix("flail", 1);
		suffix("boomerang", 1);
	}

	private WeaponCasts() {
	}

	private static void prefix(String namespace, String base, int ingots) {
		add(new Kind(namespace + ":" + base, Scheme.PREFIX, ingots * INGOT, 1));
	}

	private static void suffix(String base, int ingots) {
		add(new Kind("weaponmod:" + base, Scheme.SUFFIX, ingots * INGOT, 1));
	}

	private static void single(String item, int mb, int count) {
		add(new Kind("jaymospowers:" + item, Scheme.SINGLE, mb, count));
	}

	private static void add(Kind kind) {
		KINDS.put(kind.key(), kind);
	}

	/** The tier word a weapon's id uses for this metal ({@code golden_} or {@code .gold}), or null. */
	private static @Nullable String tier(Molten molten, boolean prefix) {
		return switch (molten) {
			case IRON -> "iron";
			case GOLD -> prefix ? "golden" : "gold";
			case COPPER -> "copper";
			default -> null;
		};
	}

	/** The kind of weapon this is, or null. */
	public static @Nullable Kind of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String path = id.getPath();
		Kind single = KINDS.get(id.getNamespace() + ":" + path);
		if (single != null && single.scheme() == Scheme.SINGLE) {
			return single;
		}
		int underscore = path.indexOf('_');
		if (underscore > 0 && PREFIX_TIERS.contains(path.substring(0, underscore))) {
			Kind kind = KINDS.get(id.getNamespace() + ":" + path.substring(underscore + 1));
			if (kind != null && kind.scheme() == Scheme.PREFIX) {
				return kind;
			}
		}
		int dot = path.lastIndexOf('.');
		if (dot > 0 && SUFFIX_TIERS.contains(path.substring(dot + 1))) {
			Kind kind = KINDS.get(id.getNamespace() + ":" + path.substring(0, dot));
			if (kind != null && kind.scheme() == Scheme.SUFFIX) {
				return kind;
			}
		}
		return null;
	}

	/** The kind a Weapon Cast casts, or null for a blank one. */
	public static @Nullable Kind ofCast(ItemStack cast) {
		String key = cast.get(Tinkering.WEAPON_CAST);
		return key == null ? null : KINDS.get(key);
	}

	/** A Weapon Cast for this kind. */
	public static ItemStack cast(Kind kind) {
		ItemStack stack = new ItemStack(Tinkering.CASTS.get(CastItem.Shape.WEAPON));
		stack.set(Tinkering.WEAPON_CAST, kind.key());
		return stack;
	}

	/** What pouring this metal over a cast of this kind makes, or null. */
	public static @Nullable ItemStack result(Kind kind, Molten molten) {
		Item item = kind.in(molten);
		return item == null ? null : new ItemStack(item, kind.count());
	}

	/** The weapons a cast of this kind makes, in iron, gold and copper (those that exist). */
	public static List<ItemStack> results(Kind kind) {
		List<ItemStack> out = new ArrayList<>();
		for (Molten molten : List.of(Molten.IRON, Molten.GOLD, Molten.COPPER)) {
			ItemStack result = result(kind, molten);
			if (result != null) {
				out.add(result);
			}
		}
		return out;
	}

	/** Every loaded weapon of this kind (any tier), each of which gold turns into its cast. */
	public static List<ItemStack> members(Kind kind) {
		List<ItemStack> out = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(kind.namespace())) {
				ItemStack stack = new ItemStack(item);
				if (of(stack) == kind) {
					out.add(stack);
				}
			}
		}
		return out;
	}
}
