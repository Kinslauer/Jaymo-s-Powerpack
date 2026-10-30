package com.jaymospowers.tinkering;

import com.jaymospowers.tinkering.smeltery.CastItem;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.77) Andy: "all weapons should be forged", the Tinkers way. Every stone and metal weapon (Armory's, Balkon's
 * WeaponMod's, vanilla spears) is put together at the Tool Station from its own head or blade (a Claymore Blade) and
 * the parts its {@link Assembly} names (mostly a Tool Handle and a Tool Binding). Stone heads are shaped at the Part
 * Builder; pour gold over any head on a casting table for its cast, then iron, gold or copper over the cast for a metal
 * head. Their crafting recipes are switched off in data
 * (tools/armory_assets.py, tools/tinkering_assets.py) and Balkon's by id ({@link Uncraftable}). Textures:
 * tools/weapon_parts_assets.py.
 */
public final class WeaponParts {
	/**
	 * How a kind names its tiers: Armory {@code iron_dagger}, Balkon {@code knife.iron}, or one item made in iron only
	 * (or, {@code SINGLE_COPPER}, copper only).
	 */
	public enum Scheme { PREFIX, SUFFIX, SINGLE, SINGLE_COPPER, SINGLE_STEEL }

	/**
	 * What else the Tool Station needs besides the head, any material. (0.11.77) Andy: the big weapons take a Tough
	 * Handle, swords a Crossguard.
	 */
	public enum Assembly {
		ROD_BINDING(PartType.TOOL_HANDLE, PartType.TOOL_BINDING), TOUGH_BINDING(PartType.TOUGH_HANDLE, PartType.TOOL_BINDING),
		GUARD_ROD(PartType.CROSSGUARD, PartType.TOOL_HANDLE), GUARD_TOUGH(PartType.CROSSGUARD, PartType.TOUGH_HANDLE),
		BINDING(PartType.TOOL_BINDING), ALONE(),
		/** (0.18.20) The Sealed Helm: its seal and a plain glass block, nothing else. */
		GLASS(Items.GLASS);

		public final List<PartType> parts;
		/** (0.18.20) A plain item that goes in beside the head and the parts (a glass block), or null. */
		public final @Nullable Item extra;

		Assembly(PartType... parts) {
			this.parts = List.of(parts);
			this.extra = null;
		}

		Assembly(Item extra) {
			this.parts = List.of();
			this.extra = extra;
		}
	}

	/** What a head is made of: stone (Part Builder) or a cast metal. */
	public enum Metal {
		STONE("stone", "stone", "stone", null), IRON("iron", "iron", "iron", Molten.IRON),
		GOLD("gold", "golden", "gold", Molten.GOLD), COPPER("copper", "copper", "copper", Molten.COPPER),
		/** (Smithing) Only for the steel smithing tools (tongs, hammer). */
		STEEL("steel", "steel", "steel", Molten.STEEL);

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
				case SINGLE_COPPER -> metal == Metal.COPPER ? base() : null;
				case SINGLE_STEEL -> metal == Metal.STEEL ? base() : null;
			};
			return path == null ? null : BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(namespace(), path)).orElse(null);
		}

		/** Whether its weapons are in the game at all (Balkon's need WeaponMod). */
		public boolean loaded() {
			for (Metal metal : Metal.values()) {
				if (metal.molten != null && in(metal) != null) {
					return true;
				}
			}
			return false;
		}

		public Item item() {
			return PARTS.get(this);
		}
	}

	private static final int INGOT = Molten.INGOT;
	public static final String SORAS_DAGGER_BLADE = "soras_dagger_blade";
	/**
	 * (0.17.110) Heads the Part Builder doesn't shape: their stone head is found, not made (Sora's Dagger Blade: a
	 * village child has it, {@code power/wind/SorasBladeChild}). They still cast and assemble like any other.
	 */
	public static final Set<String> FOUND = Set.of(SORAS_DAGGER_BLADE);
	/** Every kind, by its head's id; costs are the ingots their old crafting recipes took. */
	public static final Map<String, Kind> KINDS = new LinkedHashMap<>();
	public static final Map<Kind, Item> PARTS = new LinkedHashMap<>();

	static {
		// Jaymo's Armory (round shields stay craftable, like iron armour)
		armory("dagger_blade", "dagger", 1, Assembly.ROD_BINDING);
		armory("hatchet_head", "hatchet", 2, Assembly.ROD_BINDING);
		armory("skewer_head", "skewer", 1, Assembly.ROD_BINDING);
		armory("longsword_blade", "longsword", 2, Assembly.GUARD_ROD);
		armory("glaive_blade", "glaive", 2, Assembly.ROD_BINDING);
		armory("lance_tip", "lance", 1, Assembly.ROD_BINDING);
		armory("war_axe_head", "war_axe", 2, Assembly.ROD_BINDING);
		armory("war_scythe_blade", "war_scythe", 2, Assembly.ROD_BINDING);
		armory("flanged_mace_head", "flanged_mace", 4, Assembly.ROD_BINDING);
		armory("broadsword_blade", "broadsword", 4, Assembly.GUARD_ROD);
		armory("claymore_blade", "claymore", 5, Assembly.GUARD_TOUGH);
		armory("cleaver_blade", "cleaver", 4, Assembly.ROD_BINDING);
		armory("ninjato_blade", "ninjato", 2, Assembly.GUARD_ROD);
		armory("saber_blade", "saber", 2, Assembly.GUARD_ROD);
		armory("machete_blade", "machete", 2, Assembly.ROD_BINDING);
		armory("rapier_blade", "rapier", 1, Assembly.GUARD_ROD);
		add(new Kind("kunai_blade", "jaymospowers:kunai", Scheme.SINGLE, INGOT, Assembly.BINDING, 4));
		add(new Kind("throwing_star_blank", "jaymospowers:throwing_star", Scheme.SINGLE, 4 * Molten.NUGGET, Assembly.ALONE, 2));
		add(new Kind("harpoon_head", "jaymospowers:harpoon", Scheme.SINGLE, INGOT, Assembly.ROD_BINDING, 1));
		add(new Kind("quarterstaff_caps", "jaymospowers:iron_quarterstaff", Scheme.SINGLE, 2 * INGOT, Assembly.ROD_BINDING, 1));
		// (0.17.110) Andy: "the dagger needs a way to be crafted with tinkers": Wind's Sora's Dagger, iron stage (its
		// crafting recipe is gone); gilding and the later stages stay as they were (casting table, Tool Station)
		add(new Kind(SORAS_DAGGER_BLADE, "jaymospowers:soras_dagger", Scheme.SINGLE, 2 * INGOT, Assembly.ROD_BINDING, 1));
		// vanilla spears
		add(new Kind("spear_tip", "minecraft:spear", Scheme.PREFIX, INGOT, Assembly.ROD_BINDING, 1));
		// Balkon's WeaponMod (the musket bayonet is still a musket and a knife put together)
		balkon("knife_blade", "knife", 1, Assembly.ROD_BINDING);
		balkon("spear_head", "spear", 1, Assembly.ROD_BINDING);
		balkon("halberd_head", "halberd", 3, Assembly.TOUGH_BINDING);
		balkon("battleaxe_head", "battleaxe", 5, Assembly.TOUGH_BINDING);
		balkon("warhammer_head", "warhammer", 4, Assembly.TOUGH_BINDING);
		balkon("katana_blade", "katana", 2, Assembly.GUARD_ROD);
		balkon("flail_head", "flail", 1, Assembly.ROD_BINDING);
		balkon("boomerang_edge", "boomerang", 1, Assembly.ROD_BINDING);
		// (0.15.36) Andy: "All tools need crafting routes through jaymos": the tools Tinkering doesn't make are forged too
		add(new Kind("fishing_hook", "minecraft:fishing_rod", Scheme.SINGLE, Molten.NUGGET, Assembly.ROD_BINDING, 1));
		add(new Kind("brush_ferrule", "minecraft:brush", Scheme.SINGLE_COPPER, INGOT, Assembly.ROD_BINDING, 1));
		// Pantry for Blockheads' kitchen knife (its own recipe is dropped by id, tinkering/Uncraftable)
		add(new Kind("kitchen_knife_blade", "pantryforblockheads:knife", Scheme.SINGLE, 3 * INGOT, Assembly.ROD_BINDING, 1));
		// (0.17.116) the layer blocks' saw (layers/SawItem): Andy: "it is an iron tool", so it's forged, not crafted
		add(new Kind("saw_blade", "jaymospowers:saw", Scheme.SINGLE, 2 * INGOT, Assembly.ROD_BINDING, 1));
		// (Smithing) the smith's own tools are forged too (Andy's rule: iron tools come through Tinkers): tongs' jaws and
		// hammer heads; the copper hammer's head casts, so there's always a way to make the first hammer
		add(new Kind("tongs_jaws", "jaymospowers:iron_tongs", Scheme.SINGLE, 2 * INGOT, Assembly.ROD_BINDING, 1));
		add(new Kind("steel_tongs_jaws", "jaymospowers:steel_tongs", Scheme.SINGLE_STEEL, 2 * INGOT, Assembly.ROD_BINDING, 1));
		add(new Kind("copper_smithing_hammer_head", "jaymospowers:copper_smithing_hammer", Scheme.SINGLE_COPPER, 3 * INGOT,
			Assembly.ROD_BINDING, 1));
		add(new Kind("steel_smithing_hammer_head", "jaymospowers:steel_smithing_hammer", Scheme.SINGLE_STEEL, 3 * INGOT,
			Assembly.ROD_BINDING, 1));
		// (0.18.14) the space pod's metal parts are forged too (power/zfighter/pod/PodParts): no grid recipes, assembled alone
		add(new Kind("pod_fin_blank", "jaymospowers:pod_fin", Scheme.SINGLE_STEEL, 3 * INGOT, Assembly.ALONE, 1));
		add(new Kind("pod_plate_blank", "jaymospowers:pod_plate", Scheme.SINGLE, 4 * INGOT, Assembly.ALONE, 1));
		add(new Kind("pod_mast_blank", "jaymospowers:pod_mast", Scheme.SINGLE, 2 * INGOT, Assembly.ALONE, 1));
		add(new Kind("pod_tank_blank", "jaymospowers:pod_tank", Scheme.SINGLE_COPPER, 5 * INGOT, Assembly.ALONE, 1));
		// (0.18.20) the Sealed Helm (power/zfighter/breath/Breath): an iron seal, finished with a glass block at the Tool Station
		add(new Kind("helm_seal", "jaymospowers:sealed_helm", Scheme.SINGLE, 2 * INGOT, Assembly.GLASS, 1));
	}

	/** (0.18.20) Whether {@code stack} is the plain item some assembly takes beside its head (the Sealed Helm's glass block). */
	public static boolean isExtra(ItemStack stack) {
		for (Assembly assembly : Assembly.values()) {
			if (assembly.extra != null && stack.is(assembly.extra)) {
				return true;
			}
		}
		return false;
	}

	private WeaponParts() {
	}

	private static void armory(String part, String base, int ingots, Assembly assembly) {
		add(new Kind(part, "jaymospowers:" + base, Scheme.PREFIX, ingots * INGOT, assembly, 1));
	}

	private static void balkon(String part, String base, int ingots, Assembly assembly) {
		add(new Kind(part, "weaponmod:" + base, Scheme.SUFFIX, ingots * INGOT, assembly, 1));
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

	/** The kinds whose weapons are in the game. */
	public static List<Kind> loaded() {
		return KINDS.values().stream().filter(Kind::loaded).toList();
	}

	/** The kinds the Part Builder shapes, in its order: loaded, and not {@link #FOUND}. */
	public static List<Kind> shaped() {
		return loaded().stream().filter(kind -> !FOUND.contains(kind.part())).toList();
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
