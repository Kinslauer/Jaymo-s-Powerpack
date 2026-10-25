package com.jaymoskitchen;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.herb.JointItem;
import com.jaymoskitchen.herb.JointQuality;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ModItems {
	private static final Map<Dish, Item> DISHES = new EnumMap<>(Dish.class);

	/** Pressed from seeds; poured into dough. */
	public static Item COOKING_OIL;
	/** Made on a prep surface; the base of a pizza. */
	public static Item DOUGH;
	/** A herb picked from wild Pmeh (jungles and savannas, {@link com.jaymoskitchen.herb.PmehFarming}); rolled into joints on a prep surface. */
	public static Item PMEH;
	/** Rolled from Pmeh and paper; smoked a puff at a time. */
	public static Item JOINT;
	/** How a joint was rolled (decides which effects its puffs give). */
	public static DataComponentType<JointQuality> JOINT_QUALITY;

	private ModItems() {
	}

	public static void register() {
		// the bottle comes back when it's used in crafting (fries)
		COOKING_OIL = register("cooking_oil", new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE));
		DOUGH = register("dough", new Item.Properties());
		PMEH = register("pmeh", new Item.Properties());
		JOINT_QUALITY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("joint_quality"),
			DataComponentType.<JointQuality>builder()
				.persistent(JointQuality.CODEC)
				.networkSynchronized(JointQuality.STREAM_CODEC)
				.build());
		JOINT = register("joint", JointItem::new, new Item.Properties().durability(JointItem.PUFFS));
		for (Dish dish : Dish.values()) {
			if (dish.feast()) {
				// (0.5.28) a feast is a block to set down, not something to eat (see delight/Feast)
				DISHES.put(dish, com.jaymoskitchen.delight.Delight.feast(dish,
					ResourceKey.create(Registries.ITEM, JaymosKitchen.id(dish.id()))));
				continue;
			}
			// default stats are Good quality; cooking stamps the real quality onto each stack
			Item.Properties properties = new Item.Properties()
				.stacksTo(16)
				.food(new FoodProperties.Builder().nutrition(dish.nutrition()).saturationModifier(dish.saturation()).build());
			if (dish.bowlFood()) {
				properties.usingConvertsTo(Items.BOWL);
			}
			DISHES.put(dish, register(dish.id(), properties));
		}

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(COOKING_OIL);
			output.accept(DOUGH);
			output.accept(PMEH);
			output.accept(JOINT);
			for (Dish dish : Dish.values()) {
				// (feasts are listed with the rest of delight/Delight)
				if (dish.available() && !dish.feast()) {
					output.accept(DISHES.get(dish));
				}
			}
		});
	}

	private static Item register(String id, Item.Properties properties) {
		return register(id, Item::new, properties);
	}

	private static Item register(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** The dish an item is, or null. */
	public static @org.jspecify.annotations.Nullable Dish dishOf(Item item) {
		for (Map.Entry<Dish, Item> entry : DISHES.entrySet()) {
			if (entry.getValue() == item) {
				return entry.getKey();
			}
		}
		return null;
	}

	public static Item dish(Dish dish) {
		return DISHES.get(dish);
	}
}
