package com.jaymoskitchen.butchery;

import com.jaymoskitchen.JaymosKitchen;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Andy's Butchery picks (Butchery mod, All Rights Reserved: ideas only, our own code and art): the butcher's shop.
 * <ul>
 * <li>The {@link MincerBlock}: raw meat (any kind, or offal) turned through it comes out as {@link #MINCE}, which does
 * for meat in the kitchen's dishes (it's in the red meat, pork, mutton, poultry, small game and venison cooking groups;
 * Andy: "mince should apply as an alternative for meat in most recipes that require it").</li>
 * <li>A barrel of bones ({@link #BONE_BARREL}), a {@link HeadMountBlock} for a trophy head (Jaymo's Powers puts the head
 * on it), and the {@link ButcherDisplayBlock}, a counter that shows the meat laid on it.</li>
 * </ul>
 */
public final class ButcherShop {
	/** What the mincer takes ({@code jaymoskitchen:minceable}). */
	public static final TagKey<Item> MINCEABLE = TagKey.create(Registries.ITEM, JaymosKitchen.id("minceable"));

	public static Item MINCE;
	public static Block MINCER, BONE_BARREL, HEAD_MOUNT;
	public static ButcherDisplayBlock BUTCHER_DISPLAY;
	public static BlockEntityType<ButcherDisplayEntity> BUTCHER_DISPLAY_ENTITY;

	private ButcherShop() {
	}

	public static void register() {
		ResourceKey<Item> minceKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("mince"));
		MINCE = Registry.register(BuiltInRegistries.ITEM, minceKey, new Item(new Item.Properties()
			.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build()).setId(minceKey)));
		MINCER = block("mincer", new MincerBlock(props("mincer").mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.IRON).noOcclusion()));
		BONE_BARREL = block("bone_barrel", new Block(props("bone_barrel").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.BONE_BLOCK)
			.ignitedByLava()));
		HEAD_MOUNT = block("head_mount", new HeadMountBlock(props("head_mount").mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD)
			.noOcclusion().ignitedByLava()));
		BUTCHER_DISPLAY = block("butcher_display", new ButcherDisplayBlock(props("butcher_display").mapColor(MapColor.WOOD).strength(2.0F)
			.sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
		BUTCHER_DISPLAY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("butcher_display"),
			new BlockEntityType<>(ButcherDisplayEntity::new, Set.of(BUTCHER_DISPLAY)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(new ItemStack(MINCER));
			output.accept(new ItemStack(BUTCHER_DISPLAY));
			output.accept(new ItemStack(HEAD_MOUNT));
			output.accept(new ItemStack(BONE_BARREL));
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(new ItemStack(MINCE)));
	}

	private static BlockBehaviour.Properties props(String id) {
		return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(id)));
	}

	private static <B extends Block> B block(String id, B block) {
		Registry.register(BuiltInRegistries.BLOCK, JaymosKitchen.id(id), block);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
		return block;
	}
}
