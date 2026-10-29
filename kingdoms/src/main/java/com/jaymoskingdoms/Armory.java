package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.30) The realm's armouries: four original armour sets with raised 3D pieces (drawn by the client's
 * {@code ArmoryLayer} from {@code assets/jaymoskingdoms/armory/<set>.json}; shapes and art from
 * {@code tools/gen_armory.py}). Their cloth (plumes, tabards, lacquered lames, hide kilts) takes dye like leather, so a
 * realm can wear its colour.
 * <ul>
 * <li><b>Paladin</b>: a knight's harness. Great helm with a gilt comb and plume, round pauldrons, a tabard with a gilt
 * cross. Heaviest of the four (iron and gold).</li>
 * <li><b>Gladiator</b>: bronze arena kit. A broad-brimmed grille helm with a crest, a banded sword arm, a studded belt and
 * greaves; light and quick to enchant (copper and leather).</li>
 * <li><b>Ronin</b>: lacquered lamellar. A horned kabuto with a neck guard, hanging shoulder plates and a plated skirt
 * (iron and string).</li>
 * <li><b>Warlord</b>: a raider chief's furs. A horned iron cap, fur mantle and pelt, a bone pauldron and a hide kilt;
 * hard to knock back (leather and bone).</li>
 * </ul>
 * (Smithing) And plain <b>steel plate</b>, flat vanilla-style layers with no raised pieces: the toughest of them, made
 * from {@code #c:ingots/steel} (Jaymo's Powers' smeltery or kiln) buckled with {@code #c:leather_straps} (Jaymo's
 * Kitchen's), or with plain leather for more steel. Not one of {@link #SETS} (it has no 3D shape).
 * <p>
 * (0.7.326) And the <b>Reverent Warplate</b>, the Aether's paladins' harness: white enamelled plate edged in gold, a
 * winged helm, a red tabard and cape. eanncrod's model (Blockbench Workshop, CC BY 4.0), drawn whole over the wearer by
 * the client's {@code ReverentLayer} (no flat layer under it). Extraordinary: better than netherite, fireproof, and
 * never made. The only set in the world is the Visage of War's, the Aether guardian tree's boss in Jaymo's Powers,
 * which drops all four pieces when it falls. Mended with gold. Not one of {@link #SETS} either.
 * <p>
 * (0.7.339) And the <b>Heavenly Mantle</b>, the Reverent Warplate's pair: white and gold plate under a crown of horns,
 * feathered wings folded down the back. eanncrod's model (Blockbench Workshop, CC BY 4.0), drawn by {@code ReverentLayer}
 * the same way. Never made: one piece is given to each champion of the tournament of the dead in the afterlife (Jaymo's
 * Powers' Other World), the crown first, then the mantle, the greaves and the sabatons. A little lighter than the
 * warplate, quicker to take enchantments. Mended with gold.
 * <p>
 * (0.7.351) And the <b>Kintsugi Armour</b>: dark lacquered plate whose cracks are mended in gold seams. eanncrod's model
 * (Blockbench Workshop, CC BY 4.0), drawn by {@code ReverentLayer}. A broken Ronin piece made whole: any Ronin piece
 * and four gold ingots at a crafting table ({@link #KINTSUGI_GOLD}). A step past the Ronin's lamellar, quick to take
 * enchantments. Mended with gold. Not one of {@link #SETS} (the Ronin stays as it is).
 * <p>
 * (0.7.351) And the <b>Primal Visage</b>: a skull mask under horns, hide and bone over netherite. eanncrod's model, drawn
 * by {@code ReverentLayer}. Never made: each Nether war fort's warlord carries a piece of it ({@code NetherForts}), and
 * it falls with him. Netherite's toughness, fireproof, hard to knock back. Mended with netherite.
 */
public final class Armory {
	/** Each set's numbers: durability multiplier, defence (boots, leggings, chest, helmet), enchantability, toughness, knockback resistance. */
	static final ArmorMaterial PALADIN = material("paladin", 22, 2, 6, 7, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.05F, ItemTags.REPAIRS_IRON_ARMOR);
	static final ArmorMaterial GLADIATOR = material("gladiator", 14, 1, 4, 6, 3, 14, SoundEvents.ARMOR_EQUIP_COPPER, 0.0F, 0.0F, ItemTags.REPAIRS_COPPER_ARMOR);
	static final ArmorMaterial RONIN = material("ronin", 18, 2, 5, 6, 2, 12, SoundEvents.ARMOR_EQUIP_CHAIN, 0.5F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR);
	static final ArmorMaterial WARLORD = material("warlord", 16, 2, 5, 6, 3, 12, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.1F, ItemTags.REPAIRS_LEATHER_ARMOR);
	/** Steel ingots by the common tag (Jaymo's Powers makes them; no code dependency). */
	static final TagKey<Item> STEEL_INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/steel"));
	static final ArmorMaterial STEEL_PLATE = material("steel_plate", 30, 3, 6, 7, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 2.5F, 0.05F, STEEL_INGOTS);

	/** (0.7.326) The Reverent Warplate: a point over netherite in every piece, tougher, longer-lasting, quick to take enchantments. */
	static final ArmorMaterial REVERENT = material("reverent", 45, 4, 7, 9, 4, 22, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.15F, ItemTags.REPAIRS_GOLD_ARMOR);
	/** The Reverent Warplate's pieces by {@link ArmorType}. */
	public static final Map<ArmorType, Item> REVERENT_PIECES = new LinkedHashMap<>();
	/** (0.7.339) The Heavenly Mantle: netherite's defence, tougher, longer-lasting, the quickest to take enchantments. */
	static final ArmorMaterial HEAVENLY = material("heavenly", 42, 3, 6, 8, 3, 25, SoundEvents.ARMOR_EQUIP_GOLD, 3.5F, 0.1F, ItemTags.REPAIRS_GOLD_ARMOR);
	/** The Heavenly Mantle's pieces by {@link ArmorType}. */
	public static final Map<ArmorType, Item> HEAVENLY_PIECES = new LinkedHashMap<>();
	/** (0.7.351) The Kintsugi Armour: a Ronin piece mended with gold, a little better than iron, quick to take enchantments. */
	static final ArmorMaterial KINTSUGI = material("kintsugi", 26, 2, 5, 7, 3, 20, SoundEvents.ARMOR_EQUIP_GOLD, 1.5F, 0.05F, ItemTags.REPAIRS_GOLD_ARMOR);
	/** Gold ingots it takes to mend a Ronin piece into its Kintsugi piece (the recipes in data/jaymoskingdoms/recipe/kintsugi_*.json). */
	public static final int KINTSUGI_GOLD = 4;
	/** The Kintsugi Armour's pieces by {@link ArmorType}. */
	public static final Map<ArmorType, Item> KINTSUGI_PIECES = new LinkedHashMap<>();
	/** (0.7.351) The Primal Visage: netherite's defence and toughness, hard to knock back. */
	static final ArmorMaterial PRIMAL = material("primal", 37, 3, 6, 8, 3, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.15F, ItemTags.REPAIRS_NETHERITE_ARMOR);
	/** The Primal Visage's pieces by {@link ArmorType}. */
	public static final Map<ArmorType, Item> PRIMAL_PIECES = new LinkedHashMap<>();
	/** (Smithing) The steel plate pieces by {@link ArmorType}. */
	public static final Map<ArmorType, Item> STEEL_PLATE_PIECES = new LinkedHashMap<>();
	/** The sets by name, each its pieces by {@link ArmorType} (helmet, chestplate, leggings, boots). */
	public static final Map<String, Map<ArmorType, Item>> SETS = new LinkedHashMap<>();
	private static final ArmorType[] PIECES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

	private Armory() {
	}

	private static ArmorMaterial material(
		String name, int durability, int boots, int legs, int chest, int helm, int enchant, Holder<SoundEvent> sound, float toughness, float knockback,
		TagKey<Item> repair
	) {
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id(name));
		return new ArmorMaterial(durability, ArmorMaterials.makeDefense(boots, legs, chest, helm, chest), enchant, sound, toughness, knockback, repair, asset);
	}

	static void register() {
		List<Item> all = new ArrayList<>();
		register("paladin", PALADIN, all);
		register("gladiator", GLADIATOR, all);
		register("ronin", RONIN, all);
		register("warlord", WARLORD, all);
		STEEL_PLATE_PIECES.putAll(pieces("steel_plate", STEEL_PLATE, all));
		REVERENT_PIECES.putAll(pieces("reverent", REVERENT, all, properties -> properties.rarity(Rarity.EPIC).fireResistant()));
		HEAVENLY_PIECES.putAll(pieces("heavenly", HEAVENLY, all, properties -> properties.rarity(Rarity.EPIC).fireResistant()));
		KINTSUGI_PIECES.putAll(pieces("kintsugi", KINTSUGI, all, properties -> properties.rarity(Rarity.RARE)));
		PRIMAL_PIECES.putAll(pieces("primal", PRIMAL, all, properties -> properties.rarity(Rarity.EPIC).fireResistant()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Items.NETHERITE_BOOTS;
			for (Item item : all) {
				output.insertAfter(after, item);
				after = item;
			}
		});
	}

	private static void register(String name, ArmorMaterial material, List<Item> all) {
		SETS.put(name, pieces(name, material, all));
	}

	private static Map<ArmorType, Item> pieces(String name, ArmorMaterial material, List<Item> all) {
		return pieces(name, material, all, properties -> properties);
	}

	/** {@code extra} adds to each piece's properties (the Reverent Warplate's rarity and fireproofing). */
	private static Map<ArmorType, Item> pieces(String name, ArmorMaterial material, List<Item> all, UnaryOperator<Item.Properties> extra) {
		Map<ArmorType, Item> set = new LinkedHashMap<>();
		for (ArmorType type : PIECES) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_" + type.getName()));
			Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(extra.apply(new Item.Properties().setId(key).humanoidArmor(material, type))));
			set.put(type, item);
			all.add(item);
		}
		return set;
	}

	/** The set an item belongs to, or null. */
	public static @Nullable String setOf(Item item) {
		for (Map.Entry<String, Map<ArmorType, Item>> set : SETS.entrySet()) {
			if (set.getValue().containsValue(item)) {
				return set.getKey();
			}
		}
		return null;
	}

	/** A piece of a set, or null if there's no such set. */
	public static @Nullable Item piece(String set, ArmorType type) {
		Map<ArmorType, Item> pieces = SETS.get(set);
		return pieces == null ? null : pieces.get(type);
	}
}
