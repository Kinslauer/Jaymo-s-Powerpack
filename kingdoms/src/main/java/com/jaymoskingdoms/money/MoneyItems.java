package com.jaymoskingdoms.money;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import com.jaymoskingdoms.JaymosKingdoms;

/**
 * (0.7.498) What the money package registers: the copper bit (the small change every trader takes, the coin
 * {@code jaymoskingdoms:copper_coin} of the first coinage, renamed in the lang file) and the coin purse with its
 * {@code jaymoskingdoms:coins} component. The realms' own coins are {@link com.jaymoskingdoms.Currencies}'; the
 * economy itself is {@link Money}.
 */
public final class MoneyItems {
	/** The copper bit, {@link com.jaymoskingdoms.Currency#BIT}'s coin. */
	public static Item COPPER_BIT;
	public static Item COIN_PURSE;
	/** What a purse holds. */
	public static DataComponentType<PurseContents> COINS;

	private MoneyItems() {
	}

	public static void register() {
		COINS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("coins"),
			DataComponentType.<PurseContents>builder().persistent(PurseContents.CODEC).networkSynchronized(PurseContents.STREAM_CODEC).build());
		COPPER_BIT = item("copper_coin", Item::new, new Item.Properties());
		COIN_PURSE = item("coin_purse", CoinPurseItem::new, new Item.Properties().stacksTo(1).component(COINS, PurseContents.EMPTY));
		Money.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(COPPER_BIT));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(COIN_PURSE));
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}
}
