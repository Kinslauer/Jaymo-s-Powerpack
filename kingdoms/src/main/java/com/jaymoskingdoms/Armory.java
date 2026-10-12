package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.27) The realm's armouries: four original armour sets with raised 3D pieces (drawn by the client's
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
 */
public final class Armory {
	/** Each set's numbers: durability multiplier, defence (boots, leggings, chest, helmet), enchantability, toughness, knockback resistance. */
	static final ArmorMaterial PALADIN = material("paladin", 22, 2, 6, 7, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.05F, ItemTags.REPAIRS_IRON_ARMOR);
	static final ArmorMaterial GLADIATOR = material("gladiator", 14, 1, 4, 6, 3, 14, SoundEvents.ARMOR_EQUIP_COPPER, 0.0F, 0.0F, ItemTags.REPAIRS_COPPER_ARMOR);
	static final ArmorMaterial RONIN = material("ronin", 18, 2, 5, 6, 2, 12, SoundEvents.ARMOR_EQUIP_CHAIN, 0.5F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR);
	static final ArmorMaterial WARLORD = material("warlord", 16, 2, 5, 6, 3, 12, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.1F, ItemTags.REPAIRS_LEATHER_ARMOR);

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
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Items.NETHERITE_BOOTS;
			for (Item item : all) {
				output.insertAfter(after, item);
				after = item;
			}
		});
	}

	private static void register(String name, ArmorMaterial material, List<Item> all) {
		Map<ArmorType, Item> set = new LinkedHashMap<>();
		for (ArmorType type : PIECES) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_" + type.getName()));
			Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(material, type)));
			set.put(type, item);
			all.add(item);
		}
		SETS.put(name, set);
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
