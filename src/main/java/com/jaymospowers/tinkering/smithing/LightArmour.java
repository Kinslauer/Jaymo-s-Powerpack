package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.kiln.Kiln;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Light armour: leather worked up into something worth wearing, both sets made at the crafting table (the smithing
 * table stays locked). Mailed leather (cap, jack, chausses, boots) is a leather piece laced with mail sheets and string;
 * studded leather (cap, brigandine, leggings, boots) is a leather piece hammered with iron nuggets. Both stay dyeable
 * (a dyed leather layer under an undyed mail or stud layer) and keep what the leather piece had ({@link #carryOver}).
 * The recipes are data ({@link LightArmourRecipe}, tools/gen_light_armour.py); how the blows meet them is
 * {@code ArmourKind.padded} and {@code WeaponKind.Blow}.
 */
public final class LightArmour {
	// ===== the knobs: armour points (head, chest, legs, feet) ========================================================
	/** Mailed leather: between iron and diamond in durability, iron's points and more toughness than iron. */
	static final int MAILED_DURABILITY = 22, MAILED_ENCHANT = 12;
	static final int[] MAILED_DEFENSE = {2, 6, 5, 2};
	static final float MAILED_TOUGHNESS = 1.0F;
	/** Studded leather: between leather (5) and iron (15) in durability, a step over leather's points. */
	static final int STUDDED_DURABILITY = 10, STUDDED_ENCHANT = 12;
	static final int[] STUDDED_DEFENSE = {1, 4, 3, 1};
	static final float STUDDED_TOUGHNESS = 0.5F;
	// ================================================================================================================

	private static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
	private static final String[] MAILED_NAMES = {"mailed_cap", "mailed_jack", "mailed_chausses", "mailed_boots"},
		STUDDED_NAMES = {"studded_cap", "studded_brigandine", "studded_leggings", "studded_boots"};
	/** What a piece keeps from the leather it was made of, besides the Kitchen's hide stamp and trait modifiers. */
	private static final Set<DataComponentType<?>> KEPT = Set.of(DataComponents.DYED_COLOR, DataComponents.CUSTOM_NAME, DataComponents.ENCHANTMENTS);
	private static final String KITCHEN = "jaymoskitchen";

	/** Head, chest, legs, feet. */
	public static final Item[] MAILED = new Item[4], STUDDED = new Item[4];

	private LightArmour() {
	}

	static void register() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, JaymosPowers.id("light_armour_crafting"), LightArmourRecipe.SERIALIZER);
		make(MAILED, MAILED_NAMES, "mailed_leather", MAILED_DURABILITY, MAILED_DEFENSE, MAILED_ENCHANT, MAILED_TOUGHNESS, SoundEvents.ARMOR_EQUIP_CHAIN);
		make(STUDDED, STUDDED_NAMES, "studded_leather", STUDDED_DURABILITY, STUDDED_DEFENSE, STUDDED_ENCHANT, STUDDED_TOUGHNESS,
			SoundEvents.ARMOR_EQUIP_LEATHER);
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			for (int i = 0; i < 4; i++) {
				output.accept(MAILED[i]);
				output.accept(STUDDED[i]);
			}
		});
	}

	private static void make(Item[] into, String[] names, String set, int durability, int[] points, int enchant, float toughness,
		Holder<SoundEvent> sound) {
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		for (int i = 0; i < 4; i++) {
			defense.put(TYPES[i], points[i]);
		}
		defense.put(ArmorType.BODY, points[1]);
		TagKey<Item> repairs = TagKey.create(Registries.ITEM, JaymosPowers.id("repairs_" + set + "_armor"));
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id(set));
		ArmorMaterial material = new ArmorMaterial(durability, defense, enchant, sound, toughness, 0.0F, repairs, asset);
		for (int i = 0; i < 4; i++) {
			ArmorType type = TYPES[i];
			into[i] = Smithing.item(names[i], Item::new, p -> p.humanoidArmor(material, type));
		}
	}

	/**
	 * Hands what the leather piece {@code from} had on to the piece {@code to} made of it: its dye, name, enchantments, the
	 * Kitchen's hide stamp, its hide's hidden trait modifiers and its wear, as the same share of the new piece's durability.
	 */
	public static void carryOver(ItemStack from, ItemStack to) {
		for (TypedDataComponent<?> component : from.getComponents()) {
			Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
			if (KEPT.contains(component.type()) || key != null && key.getNamespace().equals(KITCHEN)) {
				to.set(component);
			}
		}
		ItemAttributeModifiers theirs = from.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		List<ItemAttributeModifiers.Entry> traits = new ArrayList<>();
		for (ItemAttributeModifiers.Entry entry : theirs.modifiers()) {
			if (entry.modifier().id().getNamespace().equals(KITCHEN)) {
				traits.add(entry);
			}
		}
		if (!traits.isEmpty()) {
			List<ItemAttributeModifiers.Entry> all = new ArrayList<>(to.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers());
			all.addAll(traits);
			to.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(all));
		}
		int plain = new ItemStack(from.getItem()).getMaxDamage();
		if (plain > 0 && from.getMaxDamage() != plain) {
			// (a sturdier hide makes a sturdier piece)
			to.set(DataComponents.MAX_DAMAGE, Math.round(to.getMaxDamage() * (float) from.getMaxDamage() / plain));
		}
		if (from.isDamaged() && from.getMaxDamage() > 0) {
			float worn = (float) from.getDamageValue() / from.getMaxDamage();
			to.setDamageValue(Math.min(to.getMaxDamage() - 1, Math.round(worn * to.getMaxDamage())));
		}
	}
}
