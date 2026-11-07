package com.jaymoskingdoms.props;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.7.440, Motion patch 9) Props, ideas from Actions & Stuff (our own models, on the game's own textures; models from
 * {@code kingdoms/tools/gen_props.py}): a scarecrow that keeps birds and rabbits off the crops, a gravestone, a lamp post,
 * an iron candle stand, four plushies that squeak (gifts MCA children and the married love: {@code data/mca/gifts/plushies.json}),
 * and a window box and a pot planter that grow small flowers.
 */
public final class Props {
	/** The lamp post's light. */
	static final int LAMP_LIGHT = 15;
	/** The plushies, by id. */
	public static final List<String> PLUSHIES = List.of("bear_plush", "bunny_plush", "cat_plush", "frog_plush");
	/** Every prop, by id, as the creative tab shows them. */
	public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

	private Props() {
	}

	public static void register() {
		List<Item> items = new ArrayList<>();
		items.add(block("scarecrow", p -> new PropBlock(p, PropBlock.Kind.SCARECROW),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.0F).sound(SoundType.GRASS).ignitedByLava().noOcclusion(), false));
		items.add(block("gravestone", p -> new PropBlock(p, PropBlock.Kind.GRAVESTONE),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion(), false));
		items.add(block("lamp_post", p -> new PropBlock(p, PropBlock.Kind.LAMP_POST),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.LANTERN).lightLevel(s -> LAMP_LIGHT).noOcclusion(), false));
		items.add(block("candle_stand", CandleStandBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion(), false));
		for (String plush : PLUSHIES) {
			items.add(block(plush, p -> new PropBlock(p, PropBlock.Kind.PLUSH),
				BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.5F).sound(SoundType.WOOL).ignitedByLava().noOcclusion(), true));
		}
		items.add(block("window_box", p -> new PlanterBlock(p, PropBlock.Kind.WINDOW_BOX),
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion(), false));
		items.add(block("pot_planter", p -> new PlanterBlock(p, PropBlock.Kind.POT_PLANTER),
			BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.0F).sound(SoundType.DECORATED_POT).noOcclusion(), false));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> items.forEach(output::accept));
	}

	private static Item block(String name, java.util.function.Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties props, boolean plush) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(props.setId(key)));
		BLOCKS.put(name, block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item.Properties itemProps = new Item.Properties().setId(itemKey).useBlockDescriptionPrefix();
		BiFunction<Block, Item.Properties, Item> item = plush ? PlushItem::new : BlockItem::new;
		return Registry.register(BuiltInRegistries.ITEM, itemKey, item.apply(block, itemProps));
	}
}
