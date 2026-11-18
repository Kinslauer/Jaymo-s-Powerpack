package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The new stations of hands-on cooking and what they make: the brewing keg, the tea kettle and the sushi mat (plain blocks:
 * what lies on them and the dish being made are kept by the station's {@link Table}, like any worktop), a cup of tea, and
 * the sandwich. Art by kitchen/tools/gen_cooking_stations.py.
 */
public final class HandsBlocks {
	public static Block KEG, KETTLE, MAT;
	public static Item KEG_ITEM, KETTLE_ITEM, MAT_ITEM, TEA, SANDWICH;

	private static final VoxelShape KEG_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);
	private static final VoxelShape KETTLE_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0);
	private static final VoxelShape MAT_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0);

	private HandsBlocks() {
	}

	/** A block with a fixed shape and nothing else to it. */
	private static final class Station extends Block {
		private final VoxelShape shape;

		Station(BlockBehaviour.Properties properties, VoxelShape shape) {
			super(properties);
			this.shape = shape;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return shape;
		}
	}

	public static void register() {
		KEG = block("brewing_keg", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion(), KEG_SHAPE);
		KETTLE = block("tea_kettle", BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion()
			.requiresCorrectToolForDrops(), KETTLE_SHAPE);
		MAT = block("sushi_mat", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.3F).sound(SoundType.BAMBOO).noOcclusion(), MAT_SHAPE);
		KEG_ITEM = blockItem("brewing_keg", KEG);
		KETTLE_ITEM = blockItem("tea_kettle", KETTLE);
		MAT_ITEM = blockItem("sushi_mat", MAT);
		Item.Properties tea = new Item.Properties();
		tea.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build(), Consumables.DEFAULT_DRINK);
		tea.usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16);
		TEA = item("tea", tea);
		Item.Properties sandwich = new Item.Properties();
		sandwich.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.5F).build());
		SANDWICH = item("sandwich", sandwich);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(new ItemStack(KEG_ITEM));
			output.accept(new ItemStack(KETTLE_ITEM));
			output.accept(new ItemStack(MAT_ITEM));
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(new ItemStack(TEA)));
	}

	/** Whether {@code state} is one of the new stations. */
	public static boolean isStation(BlockState state) {
		return state.is(KEG) || state.is(KETTLE) || state.is(MAT);
	}

	/** The sandwich's food, as the fillings make it (see {@link SandwichRun}). */
	static void stamp(ItemStack stack, int nutrition, float saturation, List<net.minecraft.network.chat.Component> layers) {
		stack.set(net.minecraft.core.component.DataComponents.FOOD,
			new FoodProperties(Math.max(1, Math.min(20, nutrition)), Math.max(0.0F, Math.min(20.0F, saturation)), false));
		stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(layers));
	}

	private static Block block(String id, BlockBehaviour.Properties properties, VoxelShape shape) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, new Station(properties.setId(key), shape));
	}

	private static Item blockItem(String id, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
	}

	private static Item item(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}
}
