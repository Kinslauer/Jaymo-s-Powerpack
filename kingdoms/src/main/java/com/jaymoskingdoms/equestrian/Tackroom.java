package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Blocks;

/**
 * (0.5.38) Tack and care, the items of {@link Bonding}: the war and racing saddles (the vanilla saddle is the riding
 * saddle), the bridle, the blanket (dyeable), chain and plate barding, horseshoes, the hoof pick, and oats (from tall
 * grass). The brush and the water bucket are vanilla's. Tuning numbers live at the top.
 */
public final class Tackroom {
	/** The barding: durability, armour, toughness, knockback resistance (iron horse armour is 5, diamond 11). */
	static final ArmorMaterial CHAIN = material("chain_barding", 15, 4, 0.0F, 0.0F);
	static final ArmorMaterial PLATE = material("plate_barding", 25, 9, 1.0F, 0.1F);
	/** A hoof pick's uses; oats' chance to drop from short and tall grass. */
	static final int HOOF_PICK_USES = 96;
	static final float OATS_CHANCE = 0.08F;
	/** A blanket's colour before it's dyed (a deep red). */
	public static final int BLANKET_COLOR = 0x8E2A2A;

	public static Item WAR_SADDLE, RACING_SADDLE, BRIDLE, HORSE_BLANKET, CHAIN_BARDING, PLATE_BARDING, HORSESHOES, HOOF_PICK, OATS;

	private Tackroom() {
	}

	private static ArmorMaterial material(String name, int durability, int armour, float toughness, float knockback) {
		ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id(name));
		return new ArmorMaterial(durability, ArmorMaterials.makeDefense(0, 0, 0, 0, armour), 10, SoundEvents.ARMOR_EQUIP_CHAIN, toughness, knockback,
			ItemTags.REPAIRS_IRON_ARMOR, asset);
	}

	static void register() {
		List<Item> all = new ArrayList<>();
		WAR_SADDLE = item("war_saddle", Item::new, saddle(), all);
		RACING_SADDLE = item("racing_saddle", Item::new, saddle(), all);
		BRIDLE = item("bridle", Item::new, new Item.Properties().stacksTo(1), all);
		HORSE_BLANKET = item("horse_blanket", Item::new, new Item.Properties().stacksTo(1).component(DataComponents.DYED_COLOR, new DyedItemColor(BLANKET_COLOR)), all);
		CHAIN_BARDING = item("chain_barding", Item::new, new Item.Properties().stacksTo(1).horseArmor(CHAIN), all);
		PLATE_BARDING = item("plate_barding", Item::new, new Item.Properties().stacksTo(1).horseArmor(PLATE), all);
		HORSESHOES = item("horseshoes", Item::new, new Item.Properties().stacksTo(16), all);
		HOOF_PICK = item("hoof_pick", Item::new, new Item.Properties().durability(HOOF_PICK_USES), all);
		OATS = item("oats", Item::new, new Item.Properties(), all);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			Item after = Items.SADDLE;
			for (Item item : all) {
				output.insertAfter(after, item);
				after = item;
			}
		});
		// oats grow wild in the grass
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.unwrapKey().filter(k -> k.equals(Blocks.SHORT_GRASS.getLootTable().orElse(null))
				|| k.equals(Blocks.TALL_GRASS.getLootTable().orElse(null))).isPresent() && context.getRandom().nextFloat() < OATS_CHANCE) {
				drops.add(new ItemStack(OATS));
			}
		});
	}

	/** A saddle for horses (and elephants) only; it draws its own 3D shape ({@code TackLayer}), so it names no equipment asset. */
	private static Item.Properties saddle() {
		return new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.SADDLE)
			.setEquipSound(SoundEvents.HORSE_SADDLE)
			.setAllowedEntities(EntityTypes.HORSE, EntityTypes.SKELETON_HORSE, EntityTypes.ZOMBIE_HORSE, Equestrian.WAR_ELEPHANT)
			.setEquipOnInteract(true)
			.setCanBeSheared(true)
			.setShearingSound(SoundEvents.SADDLE_UNEQUIP)
			.build());
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties properties, List<Item> all) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
		all.add(item);
		return item;
	}

	/** One of the saddles a rider can steer with (vanilla's, the war saddle, the racing saddle). */
	public static boolean saddle(ItemStack stack) {
		return stack.is(Items.SADDLE) || stack.is(WAR_SADDLE) || stack.is(RACING_SADDLE);
	}
}
