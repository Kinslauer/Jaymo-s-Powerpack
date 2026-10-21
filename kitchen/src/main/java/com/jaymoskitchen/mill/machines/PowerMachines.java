package com.jaymoskitchen.mill.machines;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Machines a shaft can turn besides the horse machines (after Better With Mods' turntable and saw, MIT; our own code
 * and art): the {@link PottersWheelBlock potter's wheel}, which throws a clay block on it into a pot, and the
 * {@link ChopperBlock bloody chopper}, which saws logs into planks and anything that walks into it.
 */
public final class PowerMachines {
	public static PottersWheelBlock POTTERS_WHEEL;
	public static BlockEntityType<PottersWheelEntity> POTTERS_WHEEL_ENTITY;
	public static Item POTTERS_WHEEL_ITEM;

	private PowerMachines() {
	}

	public static void register() {
		POTTERS_WHEEL = block("potters_wheel", PottersWheelBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
		POTTERS_WHEEL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("potters_wheel"),
			new BlockEntityType<>(PottersWheelEntity::new, Set.of(POTTERS_WHEEL)));
		POTTERS_WHEEL_ITEM = blockItem("potters_wheel", POTTERS_WHEEL);

		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosKitchen.id("potters_wheel"), new PottersWheelRecipes());

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Item item : List.of(POTTERS_WHEEL_ITEM)) {
				output.accept(new ItemStack(item));
			}
		});
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
	}
}
