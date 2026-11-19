package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.JaymosKitchen;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Farm, field and water (Andy's Medieval Deco and Let's Do picks): a water barrel ({@link WaterBarrelBlock}), a fish barrel
 * that keeps fish ({@link FishBarrelBlock}), a feeding trough ({@link TroughBlock}, {@link Troughs}), a watering can
 * ({@link WateringCanItem}), a wicker fish trap that draws on a water stock ({@link FishTrapBlock}, {@link WaterStock}) and
 * the scarecrow ({@link ScarecrowBlock}, {@link Scarecrows}).
 */
public final class FarmWater {
	public static Block WATER_BARREL, FISH_BARREL, TROUGH, FISH_TRAP, SCARECROW;
	public static Item WATER_BARREL_ITEM, FISH_BARREL_ITEM, TROUGH_ITEM, FISH_TRAP_ITEM, SCARECROW_ITEM, WATERING_CAN;
	public static BlockEntityType<FishBarrelEntity> FISH_BARREL_ENTITY;
	public static BlockEntityType<FishTrapEntity> FISH_TRAP_ENTITY;

	private FarmWater() {
	}

	public static void register() {
		WATER_BARREL = block("water_barrel", WaterBarrelBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));
		FISH_BARREL = block("fish_barrel", FishBarrelBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));
		TROUGH = block("feeding_trough", TroughBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).noOcclusion());
		FISH_TRAP = block("fish_trap", FishTrapBlock::new, BlockBehaviour.Properties.of().strength(0.8F).sound(SoundType.BAMBOO_WOOD).noOcclusion());
		SCARECROW = block("scarecrow", ScarecrowBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK).strength(0.6F).noOcclusion());
		FISH_BARREL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("fish_barrel"),
			new BlockEntityType<>(FishBarrelEntity::new, Set.of(FISH_BARREL)));
		FISH_TRAP_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("fish_trap"),
			new BlockEntityType<>(FishTrapEntity::new, Set.of(FISH_TRAP)));
		WATER_BARREL_ITEM = item("water_barrel", p -> new BlockItem(WATER_BARREL, p), new Item.Properties());
		FISH_BARREL_ITEM = item("fish_barrel", p -> new BlockItem(FISH_BARREL, p), new Item.Properties());
		TROUGH_ITEM = item("feeding_trough", p -> new BlockItem(TROUGH, p), new Item.Properties());
		FISH_TRAP_ITEM = item("fish_trap", p -> new BlockItem(FISH_TRAP, p), new Item.Properties());
		SCARECROW_ITEM = item("scarecrow", p -> new BlockItem(SCARECROW, p), new Item.Properties());
		WATERING_CAN = item("watering_can", WateringCanItem::new, new Item.Properties().durability(WateringCanItem.USES));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(WATER_BARREL_ITEM);
			output.accept(FISH_BARREL_ITEM);
			output.accept(TROUGH_ITEM);
			output.accept(FISH_TRAP_ITEM);
			output.accept(SCARECROW_ITEM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(WATERING_CAN));

		Troughs.register();
		Scarecrows.register();
		WaterStock.register();
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
