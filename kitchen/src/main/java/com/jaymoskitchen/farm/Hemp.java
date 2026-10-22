package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (Better With Mods' hemp, MIT; our own code and art.) A two-block-tall crop: hemp seeds turn up now and then when you
 * break grass (not shearing it), and grow on farmland like wheat (they can be scattered, {@link Sowing}). Grown, it puts
 * up a top half; both halves give hemp fibre. Fibre twists into string, or weaves into hemp cloth (which makes
 * bandages, if Jaymo's Powers is there).
 */
public final class Hemp {
	/** The chance a grown hemp puts up its top half on a random tick (wheat-like timing for the bottom). */
	static final float TOP_CHANCE = 0.25F;

	public static HempCropBlock CROP;
	public static HempTopBlock TOP;
	public static Item SEEDS;
	public static Item FIBRE;
	public static Item CLOTH;

	private Hemp() {
	}

	public static void register() {
		CROP = block("hemp", HempCropBlock::new, plant());
		TOP = block("hemp_top", HempTopBlock::new, plant());
		ResourceKey<Item> seedsKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("hemp_seeds"));
		SEEDS = Registry.register(BuiltInRegistries.ITEM, seedsKey,
			new BlockItem(CROP, new Item.Properties().useItemDescriptionPrefix().setId(seedsKey)));
		FIBRE = item("hemp_fibre", Item::new, new Item.Properties());
		CLOTH = item("hemp_cloth", Item::new, new Item.Properties());

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.accept(new ItemStack(SEEDS)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(FIBRE, CLOTH)) {
				output.accept(new ItemStack(item));
			}
		});
	}

	/** As vanilla's wheat: no collision, broken at a touch, ticks to grow, pushed pistons break it. */
	private static BlockBehaviour.Properties plant() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
			.sound(SoundType.CROP).pushReaction(PushReaction.POPPED);
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
