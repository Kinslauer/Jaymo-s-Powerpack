package com.jaymoskingdoms.money;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import com.jaymoskingdoms.JaymosKingdoms;

/**
 * (0.7.487) Everything the coinage registers: the three coins and their coin stacks (and the layered piles those place),
 * the coin purse with its {@code jaymoskingdoms:coins} component, the Coin Press and its menu, and the Counting Table the
 * money changer works at ({@link Changer}). The economy itself is {@link Money}.
 */
public final class MoneyItems {
	/** The coins and the stacks of ten, indexed by {@link Coin#ordinal()}. */
	public static final Item[] COIN_ITEMS = new Item[Coin.values().length], STACK_ITEMS = new Item[Coin.values().length];
	/** The layered piles the stacks place, indexed the same way. */
	public static final Block[] PILES = new Block[Coin.values().length];
	public static Item COIN_PURSE;
	/** What a purse holds. */
	public static DataComponentType<PurseContents> COINS;
	public static Block COIN_PRESS, COUNTING_TABLE;
	public static Item COIN_PRESS_ITEM, COUNTING_TABLE_ITEM;
	public static MenuType<CoinPressMenu> PRESS_MENU;

	private MoneyItems() {
	}

	public static void register() {
		COINS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("coins"),
			DataComponentType.<PurseContents>builder().persistent(PurseContents.CODEC).networkSynchronized(PurseContents.STREAM_CODEC).build());
		MapColor[] colors = {MapColor.COLOR_ORANGE, MapColor.METAL, MapColor.GOLD};
		for (Coin c : Coin.values()) {
			COIN_ITEMS[c.ordinal()] = item(c.id() + "_coin", Item::new, new Item.Properties());
			String stack = c.id() + "_coin_stack";
			PILES[c.ordinal()] = block(stack, CoinPileBlock::new, BlockBehaviour.Properties.of().mapColor(colors[c.ordinal()]).strength(0.3F).sound(SoundType.METAL)
				.pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));
			STACK_ITEMS[c.ordinal()] = blockItem(stack, PILES[c.ordinal()]);
		}
		COIN_PURSE = item("coin_purse", CoinPurseItem::new, new Item.Properties().stacksTo(1).component(COINS, PurseContents.EMPTY));
		COIN_PRESS = block("coin_press", CoinPressBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F)
			.requiresCorrectToolForDrops().sound(SoundType.ANVIL));
		COIN_PRESS_ITEM = blockItem("coin_press", COIN_PRESS);
		COUNTING_TABLE = block("counting_table", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD)
			.ignitedByLava());
		COUNTING_TABLE_ITEM = blockItem("counting_table", COUNTING_TABLE);
		PRESS_MENU = Registry.register(BuiltInRegistries.MENU, JaymosKingdoms.id("coin_press"), new MenuType<>(CoinPressMenu::new, FeatureFlags.VANILLA_SET));
		Changer.register();
		CoinPress.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Coin c : Coin.values()) {
				output.accept(COIN_ITEMS[c.ordinal()]);
			}
			for (Coin c : Coin.values()) {
				output.accept(STACK_ITEMS[c.ordinal()]);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(COIN_PURSE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(COIN_PRESS_ITEM);
			output.accept(COUNTING_TABLE_ITEM);
		});
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}
}
