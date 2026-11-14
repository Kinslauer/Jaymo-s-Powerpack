package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.jspecify.annotations.Nullable;

/**
 * The armour sets of cleannrooster's Frontier Armaments (MIT, licenses/frontier-armaments-MIT.txt), Andy's picks: the
 * Templar plate, the Hedge Mage's garb, the Primalist wrappings, the Mercenary-Ranger's harness, the settler's gear, the
 * Juggernaut plate, the Scoundrel's wrappings, the Sanguine coat, the Despot's tusked plate and the Foxshade mantle. Each
 * is drawn from its baked model by the client's {@code ReverentLayer} (kingdoms/tools/gen_frontier_armour.py makes them),
 * so they hang on anything humanoid and take every pose vanilla armour does.
 * <p>
 * A <b>look</b> is one colouring of a set ({@link #LOOKS}): the Templar and the Ranger have a blackened one forged from
 * steel, the Hedge Mage a wine-red and an ashen one (the ashen sewn over steel), the Sanguine coat a blue and a purple.
 * What each set does when worn whole is {@link FrontierPerks}; who wears and sells them is {@link FrontierWear}.
 */
public final class FrontierArmour {
	/** One colouring of a set: its name (item ids are {@code <name>_<piece>}), the set it belongs to, its baked model and its pieces. */
	public record Look(String name, String set, String model, Map<ArmorType, Item> pieces) {
		/** Whether {@code wearer} has every piece of this look's set on (any colouring of it, mixed or not). */
		public boolean worn(LivingEntity wearer) {
			return FrontierArmour.pieces(wearer, this.set) >= this.pieces.size();
		}
	}

	/** Each set's numbers: durability multiplier, defence (boots, leggings, chest, helmet), enchantability, toughness, knockback resistance a piece. */
	static final ArmorMaterial TEMPLAR = material("templar", 26, 3, 6, 8, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 1.5F, 0.025F, ItemTags.REPAIRS_IRON_ARMOR);
	static final ArmorMaterial TEMPLAR_STEEL = material("templar_steel", 30, 3, 6, 8, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 2.5F, 0.1F, Armory.STEEL_INGOTS);
	static final ArmorMaterial HEDGE = material("hedge", 20, 3, 5, 7, 3, 22, SoundEvents.ARMOR_EQUIP_LEATHER, 1.5F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);
	static final ArmorMaterial HEDGE_ELITE = material("hedge_elite", 22, 3, 5, 7, 3, 25, SoundEvents.ARMOR_EQUIP_LEATHER, 2.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);
	static final ArmorMaterial HEDGE_ASHEN = material("hedge_ashen", 28, 3, 6, 8, 3, 22, SoundEvents.ARMOR_EQUIP_LEATHER, 2.5F, 0.05F, Armory.STEEL_INGOTS);
	static final ArmorMaterial PRIMALIST = material("primalist", 22, 3, 5, 7, 3, 14, SoundEvents.ARMOR_EQUIP_LEATHER, 1.5F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);
	static final ArmorMaterial RANGER = material("ranger", 24, 3, 5, 7, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 2.0F, 0.025F, ItemTags.REPAIRS_IRON_ARMOR);
	static final ArmorMaterial RANGER_STEEL = material("ranger_steel", 30, 3, 6, 8, 3, 10, SoundEvents.ARMOR_EQUIP_IRON, 3.0F, 0.1F, Armory.STEEL_INGOTS);
	static final ArmorMaterial SETTLER = material("settler", 15, 3, 4, 5, 2, 14, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR);
	/** The settler's hat without its helm: a hat's worth of defence. */
	static final ArmorMaterial SETTLER_HAT_CLOTH = material("settler_hat", 10, 1, 2, 3, 1, 14, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);
	static final ArmorMaterial JUGGERNAUT = material("juggernaut", 34, 3, 7, 8, 4, 6, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.15F, ItemTags.REPAIRS_IRON_ARMOR);
	static final ArmorMaterial SCOUNDREL = material("scoundrel", 18, 3, 5, 6, 2, 18, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);
	static final ArmorMaterial SANGUINE = material("sanguine", 22, 3, 4, 5, 2, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);
	static final ArmorMaterial SANGUINE_BLUE = material("sanguine_blue", 22, 3, 4, 5, 2, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);
	static final ArmorMaterial SANGUINE_PURPLE = material("sanguine_purple", 22, 3, 4, 5, 2, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);
	static final ArmorMaterial DESPOT = material("despot", 30, 3, 6, 8, 3, 12, SoundEvents.ARMOR_EQUIP_GOLD, 2.0F, 0.1F, ItemTags.REPAIRS_GOLD_ARMOR);
	static final ArmorMaterial FOXSHADE = material("foxshade", 18, 2, 4, 5, 2, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.5F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR);

	private static final ArmorType[] ALL = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
	private static final ArmorType[] CHEST = {ArmorType.CHESTPLATE};

	/** Every look by name, in the order they were registered. */
	public static final Map<String, Look> LOOKS = new LinkedHashMap<>();
	/** The settler's wide hat alone ({@code settler_hat_helmet}): the full helm under it is the guards' ({@code settler_helmet}). */
	public static Item SETTLER_HAT;

	private FrontierArmour() {
	}

	private static ArmorMaterial material(String name, int durability, int boots, int legs, int chest, int helm, int enchant, Holder<SoundEvent> sound,
		float toughness, float knockback, TagKey<Item> repair) {
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id(name));
		return new ArmorMaterial(durability, ArmorMaterials.makeDefense(boots, legs, chest, helm, chest), enchant, sound, toughness, knockback, repair, asset);
	}

	static void register() {
		List<Item> all = new ArrayList<>();
		look("templar", "templar", "templar", TEMPLAR, ALL, all);
		look("templar_steel", "templar", "templar", TEMPLAR_STEEL, ALL, all);
		look("hedge", "hedge", "hedge", HEDGE, ALL, all);
		look("hedge_elite", "hedge", "hedge", HEDGE_ELITE, ALL, all);
		look("hedge_ashen", "hedge", "hedge", HEDGE_ASHEN, ALL, all);
		look("primalist", "primalist", "primalist", PRIMALIST, ALL, all);
		look("ranger", "ranger", "ranger", RANGER, ALL, all);
		look("ranger_steel", "ranger", "ranger", RANGER_STEEL, ALL, all);
		look("settler", "settler", "settler", SETTLER, ALL, all);
		SETTLER_HAT = piece("settler_hat", "settler", SETTLER_HAT_CLOTH, ArmorType.HELMET);
		all.add(SETTLER_HAT);
		look("juggernaut", "juggernaut", "juggernaut", JUGGERNAUT, ALL, all);
		look("scoundrel", "scoundrel", "scoundrel", SCOUNDREL, ALL, all);
		look("sanguine", "sanguine", "sanguine", SANGUINE, ALL, all);
		look("sanguine_blue", "sanguine", "sanguine", SANGUINE_BLUE, ALL, all);
		look("sanguine_purple", "sanguine", "sanguine", SANGUINE_PURPLE, ALL, all);
		look("despot", "despot", "despot", DESPOT, ALL, all);
		look("foxshade", "foxshade", "foxshade", FOXSHADE, CHEST, all);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Items.NETHERITE_BOOTS;
			for (Item item : all) {
				output.insertAfter(after, item);
				after = item;
			}
		});
	}

	private static void look(String name, String set, String model, ArmorMaterial material, ArmorType[] types, List<Item> all) {
		Map<ArmorType, Item> pieces = new LinkedHashMap<>();
		for (ArmorType type : types) {
			Item item = piece(name, set, material, type);
			pieces.put(type, item);
			all.add(item);
		}
		LOOKS.put(name, new Look(name, set, "frontier_" + model, pieces));
	}

	/** One piece: its tooltip says what its set does worn whole ({@code item.jaymoskingdoms.frontier.<set>}). */
	private static Item piece(String name, String set, ArmorMaterial material, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_" + type.getName()));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(material, type)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.frontier." + set))))));
	}

	/** A look's piece as a stack, or empty if it has none of that type. */
	public static ItemStack stack(String look, ArmorType type) {
		Look l = LOOKS.get(look);
		Item item = l == null ? null : l.pieces().get(type);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	/** The set {@code stack} is a piece of ("templar", "hedge", ...), or null. */
	public static @Nullable String setOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.is(SETTLER_HAT)) {
			return "settler";
		}
		for (Look look : LOOKS.values()) {
			if (look.pieces().containsValue(stack.getItem())) {
				return look.set();
			}
		}
		return null;
	}

	/** How many pieces of {@code set} {@code wearer} has on (any colouring). */
	public static int pieces(LivingEntity wearer, String set) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (set.equals(setOf(wearer.getItemBySlot(slot)))) {
				n++;
			}
		}
		return n;
	}

	/** Whether {@code wearer} has all of {@code set} on: four pieces (the Foxshade mantle is its one). */
	public static boolean whole(LivingEntity wearer, String set) {
		return pieces(wearer, set) >= (set.equals("foxshade") ? 1 : 4);
	}

	/** Dresses {@code mob} in a look, piece by piece (those it has); nothing is dropped when it dies unless {@code drops}. */
	public static void dress(net.minecraft.world.entity.Mob mob, String look, boolean drops) {
		Look l = LOOKS.get(look);
		if (l == null) {
			return;
		}
		l.pieces().forEach((type, item) -> {
			EquipmentSlot slot = type.getSlot();
			mob.setItemSlot(slot, new ItemStack(item));
			mob.setDropChance(slot, drops ? 0.085F : 0.0F);
		});
	}
}
