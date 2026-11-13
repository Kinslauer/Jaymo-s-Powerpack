package com.jaymospowers.gear;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.gear.arrow.ArcheryMoves;
import com.jaymospowers.gear.arrow.ArrowHead;
import com.jaymospowers.gear.arrow.ArrowHeadItem;
import com.jaymospowers.gear.arrow.HeadFx;
import com.jaymospowers.gear.arrow.HeadedArrowItem;
import com.jaymospowers.gear.belt.Belt;
import com.jaymospowers.gear.belt.BeltContents;
import com.jaymospowers.gear.belt.Throwables;
import com.jaymospowers.gear.belt.ThrowerBeltItem;
import com.jaymospowers.gear.shield.ShieldMoves;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;

/**
 * Gear for everyone: arrowheads fitted at the Fletching Station ({@code gear/arrow}), a belt of throwables ({@code gear/belt}) and
 * shield moves ({@code gear/shield}). This registers the items, the belt's component and the thrown-gear entity, and starts
 * each part. Recipes, models and lang are data; the art comes from {@code tools/gen_gear_picks.py}.
 */
public final class GearPicks {
	/** What a belt of throwables carries. */
	public static DataComponentType<BeltContents> BELT;
	public static EntityType<ThrownGear> THROWN;
	public static final Map<ArrowHead, Item> HEADS = new EnumMap<>(ArrowHead.class);
	public static Item LEATHER_BELT, IRON_BELT, STEEL_BELT;

	/** Slots of each belt tier. */
	public static final int LEATHER_SLOTS = 3, IRON_SLOTS = 5, STEEL_SLOTS = 7;

	private GearPicks() {
	}

	public static void init() {
		BELT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("thrower_belt"),
			DataComponentType.<BeltContents>builder().persistent(BeltContents.CODEC).networkSynchronized(BeltContents.STREAM_CODEC).build());
		ResourceKey<EntityType<?>> thrownKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("thrown_gear"));
		THROWN = Registry.register(BuiltInRegistries.ENTITY_TYPE, thrownKey, EntityType.Builder.<ThrownGear>of(ThrownGear::new, MobCategory.MISC)
			.noLootTable().sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1).build(thrownKey));

		for (ArrowHead head : ArrowHead.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(head.itemId()));
			HEADS.put(head, Registry.register(BuiltInRegistries.ITEM, key, new ArrowHeadItem(new Item.Properties().setId(key), head)));
		}
		ResourceKey<Item> arrow = ResourceKey.create(Registries.ITEM, JaymosPowers.id("headed_arrow"));
		HeadedArrowItem.ITEM = Registry.register(BuiltInRegistries.ITEM, arrow, new HeadedArrowItem(new Item.Properties().setId(arrow)));

		LEATHER_BELT = belt("leather_thrower_belt", LEATHER_SLOTS, Rarity.COMMON);
		IRON_BELT = belt("iron_thrower_belt", IRON_SLOTS, Rarity.COMMON);
		STEEL_BELT = belt("steel_thrower_belt", STEEL_SLOTS, Rarity.UNCOMMON);
		Throwables.register();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			HEADS.values().forEach(output::accept);
			for (ArrowHead head : ArrowHead.values()) {
				output.accept(HeadedArrowItem.of(head, 1));
			}
			output.accept(LEATHER_BELT);
			output.accept(IRON_BELT);
			output.accept(STEEL_BELT);
			output.accept(new ItemStack(Throwables.SMOKE_POT));
			output.accept(new ItemStack(Throwables.FLASH_POWDER));
			output.accept(new ItemStack(Throwables.SNEEZING_POWDER));
			output.accept(new ItemStack(Throwables.BLEEDING_DART));
			output.accept(new ItemStack(Throwables.ACID_VIAL));
			output.accept(new ItemStack(Throwables.CHOKING_VIAL));
			output.accept(new ItemStack(Throwables.CALTROPS));
			output.accept(new ItemStack(Throwables.BEAR_TRAP));
		});
		GearNet.init();
		GroundFx.init();
		HeadFx.init();
		ArcheryMoves.init();
		Belt.init();
		ShieldMoves.init();
	}

	private static Item belt(String id, int slots, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new ThrowerBeltItem(new Item.Properties().setId(key).stacksTo(1).rarity(rarity)
			.component(BELT, BeltContents.EMPTY), slots));
	}
}
