package com.jaymoskitchen.stomach;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The lunch box (the GT New Horizons picks, patch 2, after Hunger Overhaul's lunch box): a pouch of
 * {@link LunchBoxMenu#SLOT_COUNT} food slots, opened from the hand. Carried anywhere in your pockets, it feeds you when
 * you're hungry enough: the biggest meal in it that your hunger has room for, or, below {@link #HUNGRY}, the smallest
 * when none has. Looked at every {@link #FEED_TICKS}; never while it's open.
 */
public final class LunchBox {
	/** How often a box looks to feed its owner (ticks). */
	static final int FEED_TICKS = 40;
	/** At or below this hunger it feeds you even what won't fit. */
	static final int HUNGRY = 6;
	/** A full hunger bar. */
	private static final int FULL = 20;

	public static Item LUNCH_BOX;
	public static MenuType<LunchBoxMenu> MENU;

	private LunchBox() {
	}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("lunch_box"));
		LUNCH_BOX = Registry.register(BuiltInRegistries.ITEM, key, new LunchBoxItem(new Item.Properties().stacksTo(1)
			.component(DataComponents.CONTAINER, ItemContainerContents.EMPTY).setId(key)));
		MENU = Registry.register(BuiltInRegistries.MENU, JaymosKitchen.id("lunch_box"), new MenuType<>(LunchBoxMenu::new, FeatureFlags.VANILLA_SET));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(new ItemStack(LUNCH_BOX)));
		ServerTickEvents.END_SERVER_TICK.register(LunchBox::tick);
	}

	/** Foods a lunch box never feeds you by itself (chorus fruit, pufferfish, rotten flesh...): you eat those by hand. */
	static final TagKey<Item> REFUSES = TagKey.create(Registries.ITEM, JaymosKitchen.id("lunch_box_refuses"));

	/** What may go in a lunch box: food, and no box in a box. */
	static boolean fits(ItemStack stack) {
		return stack.has(DataComponents.FOOD) && !stack.is(LUNCH_BOX);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % FEED_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.containerMenu instanceof LunchBoxMenu) {
				continue;
			}
			int missing = FULL - player.getFoodData().getFoodLevel();
			if (missing <= 0) {
				continue;
			}
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack box = inventory.getItem(i);
				if (box.is(LUNCH_BOX) && feed(player, box, missing)) {
					break;
				}
			}
		}
	}

	/** Feeds {@code player} the best fit from {@code box}; false if nothing in it would do. */
	private static boolean feed(ServerPlayer player, ItemStack box, int missing) {
		NonNullList<ItemStack> items = NonNullList.withSize(LunchBoxMenu.SLOT_COUNT, ItemStack.EMPTY);
		box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		int best = -1, bestFills = -1, least = -1, leastFills = Integer.MAX_VALUE;
		for (int i = 0; i < items.size(); i++) {
			FoodProperties food = items.get(i).get(DataComponents.FOOD);
			if (food == null || items.get(i).is(REFUSES)) {
				continue;
			}
			int fills = food.nutrition();
			if (fills <= missing && fills > bestFills) {
				best = i;
				bestFills = fills;
			}
			if (fills < leastFills) {
				least = i;
				leastFills = fills;
			}
		}
		FoodData data = player.getFoodData();
		int pick = best >= 0 ? best : data.getFoodLevel() <= HUNGRY ? least : -1;
		if (pick < 0) {
			return false;
		}
		ItemStack meal = items.get(pick).split(1);
		box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		ItemStack left = meal.finishUsingItem(player.level(), player);
		if (!left.isEmpty()) {
			// (the bowl or bottle it came in)
			player.getInventory().placeItemBackInInventory(left, Prediction.SERVER_ONLY);
		}
		return true;
	}
}
