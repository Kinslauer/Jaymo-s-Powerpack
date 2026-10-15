package com.jaymospowers.tinkering;

import com.jaymospowers.tinkering.smeltery.CastItem;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.76) Andy: "all weapons should be forged", the Tinkers way. Every stone and metal weapon (Armory's, Balkon's
 * WeaponMod's, vanilla spears) is put together at the Tool Station from its own head or blade (a Claymore Blade), a Tool
 * Handle and a Tool Binding. Stone heads are shaped at the Part Builder; pour gold over any head on a casting table for its cast,
 * then iron, gold or copper over the cast for a metal head. Their crafting recipes are switched off in data
 * (tools/armory_assets.py, tools/tinkering_assets.py) and Balkon's by id ({@link Uncraftable}). Textures:
 * tools/weapon_parts_assets.py.
 */
public final class WeaponParts {
	/** How a kind names its tiers: Armory {@code iron_dagger}, Balkon {@code knife.iron}, or one item made in iron only. */
	public enum Scheme { PREFIX, SUFFIX, SINGLE }

	/** What else the Tool Station needs besides the head. */
	public enum Assembly { ROD_BINDING, BINDING, ALONE }

	/** What a head is made of: stone (Part Builder) or a cast metal. */
	public enum Metal {
		STONE("stone", "stone", "stone", null), IRON("iron", "iron", "iron", Molten.IRON),
		GOLD("gold", "golden", "gold", Molten.GOLD), COPPER("copper", "copper", "copper", Molten.COPPER);

		public final String id;
		final String prefixTier, suffixTier;
		public final @Nullable Molten molten;

		Metal(String id, String prefixTier, String suffixTier, @Nullable Molten molten) {
			this.id = id;
			this.prefixTier = prefixTier;
			this.suffixTier = suffixTier;
			this.molten = molten;
		}

		public Component displayName() {
			return Component.translatable("weapon_metal.jaymospowers." + id);
		}

		public static @Nullable Metal byId(@Nullable String id) {
			for (Metal metal : values()) {
				if (metal.id.equals(id)) {
					return metal;
				}
			}
			return null;
		}

		public static @Nullable Metal of(Molten molten) {
			for (Metal metal : values()) {
				if (metal.molten == molten) {
					return metal;
				}
			}
			return null;
		}
	}

	/**
	 * A kind of weapon: its head's item id, the weapon's key (namespace:base) and naming, the metal a head takes (mB; a
	 * stone head takes as many stone as ingots, at least 1), what it's assembled with and how many it makes.
	 */
	public record Kind(String part, String key, Scheme scheme, int mb, Assembly assembly, int count) {
		public String namespace() {
			return key.substring(0, key.indexOf(':'));
		}

		public String base() {
			return key.substring(key.indexOf(':') + 1);
		}

		/** Stone it takes at the Part Builder. */
		public int stoneCost() {
			return Math.max(1, mb / Molten.INGOT);
		}

		/** This weapon in this metal, or null if there's none (or its mod isn't loaded). */
		public @Nullable Item in(Metal metal) {
			String path = switch (scheme) {
				case PREFIX -> metal.prefixTier + "_" + base();
				case SUFFIX -> base() + "." + metal.suffixTier;
				case SINGLE -> metal == Metal.IRON ? base() : null;
			};
			return path == null ? null : BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(namespace(), path)).orElse(null);
		}

		/** Whether its weapons are in the game at all (Balkon's need WeaponMod). */
		public boolean loaded() {
			return in(Metal.IRON) != null;
		}

		public Item item() {
			return PARTS.get(this);
		}
	}

	private static final int INGOT = Molten.INGOT;
	/** Every kind, by its head's id; costs are the ingots their old crafting recipes took. */
	public static final Map<String, Kind> KINDS = new LinkedHashMap<>();
	public static final Map<Kind, Item> PARTS = new LinkedHashMap<>();

	static {
		// Jaymo's Armory (round shields stay craftable, like iron armour)
		armory("dagger_blade", "dagger", 1);
		armory("hatchet_head", "hatchet", 2);
		armory("skewer_head", "skewer", 1);
		armory("longsword_blade", "longsword", 2);
		armory("glaive_blade", "glaive", 2);
		armory("lance_tip", "lance", 1);
		armory("war_axe_head", "war_axe", 2);
		armory("war_scythe_blade", "war_scythe", 2);
		armory("flanged_mace_head", "flanged_mace", 4);
		armory("broadsword_blade", "broadsword", 4);
		armory("claymore_blade", "claymore", 5);
		armory("cleaver_blade", "cleaver", 4);
		armory("ninjato_blade", "ninjato", 2);
		armory("saber_blade", "saber", 2);
		armory("machete_blade", "machete", 2);
		add(new Kind("kunai_blade", "jaymospowers:kunai", Scheme.SINGLE, INGOT, Assembly.BINDING, 4));
		add(new Kind("throwing_star_blank", "jaymospowers:throwing_star", Scheme.SINGLE, 4 * Molten.NUGGET, Assembly.ALONE, 2));
		add(new Kind("harpoon_head", "jaymospowers:harpoon", Scheme.SINGLE, INGOT, Assembly.ROD_BINDING, 1));
		add(new Kind("quarterstaff_caps", "jaymospowers:iron_quarterstaff", Scheme.SINGLE, 2 * INGOT, Assembly.ROD_BINDING, 1));
		// vanilla spears
		add(new Kind("spear_tip", "minecraft:spear", Scheme.PREFIX, INGOT, Assembly.ROD_BINDING, 1));
		// Balkon's WeaponMod (the musket bayonet is still a musket and a knife put together)
		balkon("knife_blade", "knife", 1);
		balkon("spear_head", "spear", 1);
		balkon("halberd_head", "halberd", 3);
		balkon("battleaxe_head", "battleaxe", 5);
		balkon("warhammer_head", "warhammer", 4);
		balkon("katana_blade", "katana", 2);
		balkon("flail_head", "flail", 1);
		balkon("boomerang_edge", "boomerang", 1);
	}

	private WeaponParts() {
	}

	private static void armory(String part, String base, int ingots) {
		add(new Kind(part, "jaymospowers:" + base, Scheme.PREFIX, ingots * INGOT, Assembly.ROD_BINDING, 1));
	}

	private static void balkon(String part, String base, int ingots) {
		add(new Kind(part, "weaponmod:" + base, Scheme.SUFFIX, ingots * INGOT, Assembly.ROD_BINDING, 1));
	}

	private static void add(Kind kind) {
		KINDS.put(kind.part(), kind);
	}

	/** Registers a head item for each kind. */
	static void register() {
		for (Kind kind : KINDS.values()) {
			PARTS.put(kind, Tinkering.item(kind.part(), p -> new WeaponPartItem(p, kind), Function.identity()));
		}
	}

	/** The kinds whose weapons are in the game, in Part Builder order. */
	public static List<Kind> loaded() {
		return KINDS.values().stream().filter(Kind::loaded).toList();
	}

	// --- casts ---

	/** The kind a Weapon Cast casts, or null for a blank one. */
	public static @Nullable Kind ofCast(ItemStack cast) {
		String part = cast.get(Tinkering.WEAPON_CAST);
		return part == null ? null : KINDS.get(part);
	}

	/** A Weapon Cast for this kind. */
	public static ItemStack cast(Kind kind) {
		ItemStack stack = new ItemStack(Tinkering.CASTS.get(CastItem.Shape.WEAPON));
		stack.set(Tinkering.WEAPON_CAST, kind.part());
		return stack;
	}

	/** The head pouring this liquid over a cast of this kind makes, or null. */
	public static @Nullable ItemStack castResult(Kind kind, Molten molten) {
		Metal metal = Metal.of(molten);
		return metal == null || kind.in(metal) == null ? null : WeaponPartItem.of(kind, metal, 1);
	}

	/** The metal heads a cast of this kind makes. */
	public static List<ItemStack> castResults(Kind kind) {
		List<ItemStack> out = new ArrayList<>();
		for (Metal metal : Metal.values()) {
			if (metal.molten != null && kind.in(metal) != null) {
				out.add(WeaponPartItem.of(kind, metal, 1));
			}
		}
		return out;
	}

	/** Every head of this kind (stone and metals), any of which gold turns into its cast. */
	public static List<ItemStack> heads(Kind kind) {
		List<ItemStack> out = new ArrayList<>();
		out.add(WeaponPartItem.of(kind, Metal.STONE, 1));
		out.addAll(castResults(kind));
		return out;
	}
}
