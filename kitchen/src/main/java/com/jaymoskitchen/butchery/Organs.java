package com.jaymoskitchen.butchery;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The organs a body really has (Chest Cavity's per-mob organ sets, CC0; its organ art, our own code and uses: none of its
 * chest-opening surgery or organ swapping). Gutting a carcass with a blade ({@code corpse/Gutting}), or a butcher
 * dressing it on the hook, takes out the insides a piece at a time, and each piece now brings out the organs of that
 * kind of animal ({@link #of}) along with its gut pile: a cow or a sheep its heart, liver, two kidneys, two lungs and the
 * tripe of its four-chambered stomach, a chicken its heart, liver and gizzard, a spider its two silk glands, a creeper
 * the gland its powder comes from. Mobs nothing here lists go by their size ({@link #bySize}); people are never gutted.
 * <p>
 * What they're for: hearts, livers, kidneys and tripe are eaten (raw they're raw meat, with its sickness and its week to
 * keep; cooked on a fire, in a furnace or a smoker they're a good meal), lungs and livers bait a hook or a fish trap
 * ({@link #BAIT}), a butcher buys hearts and livers and a cleric the glands (data/jaymoskitchen/villager_trade), and an
 * apothecary reads every one of them for its essences (Jaymo's Powers' alchemy, data/jaymoskitchen/alchemy/essences).
 * A silk gland draws out into string, a powder gland crumbles into gunpowder (crafting).
 */
public final class Organs {
	/** Organs that bait a hook or a fish trap (lungs, livers, offal). */
	public static final TagKey<Item> BAIT = TagKey.create(Registries.ITEM, JaymosKitchen.id("organ_bait"));

	public static Item HEART, LIVER, KIDNEY, LUNG, TRIPE;
	public static Item COOKED_HEART, COOKED_LIVER, COOKED_KIDNEY, COOKED_TRIPE;
	/** The odd ones: a spider's silk, a venomous spider's or bee's sting, a ghast's floats, a creeper's powder. */
	public static Item SILK_GLAND, VENOM_GLAND, GAS_BLADDER, POWDER_GLAND;

	/** What each kind of body holds (looked up when gutted, since the items are registered after this class loads). */
	private static @Nullable Map<EntityType<?>, List<Supplier<Item>>> sets;

	private Organs() {
	}

	static void register() {
		HEART = item("heart", 3, 0.3F);
		LIVER = item("liver", 3, 0.4F);
		KIDNEY = item("kidney", 2, 0.3F);
		LUNG = item("lung", 1, 0.1F);
		TRIPE = item("tripe", 2, 0.2F);
		COOKED_HEART = item("cooked_heart", 7, 0.8F);
		COOKED_LIVER = item("cooked_liver", 6, 0.9F);
		COOKED_KIDNEY = item("cooked_kidney", 5, 0.7F);
		COOKED_TRIPE = item("cooked_tripe", 5, 0.6F);
		SILK_GLAND = item("silk_gland", 0, 0.0F);
		VENOM_GLAND = item("venom_gland", 0, 0.0F);
		GAS_BLADDER = item("gas_bladder", 0, 0.0F);
		POWDER_GLAND = item("powder_gland", 0, 0.0F);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Item item : List.of(HEART, LIVER, KIDNEY, LUNG, TRIPE, COOKED_HEART, COOKED_LIVER, COOKED_KIDNEY, COOKED_TRIPE)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(SILK_GLAND, VENOM_GLAND, GAS_BLADDER, POWDER_GLAND)) {
				output.accept(new ItemStack(item));
			}
		});
	}

	private static Item item(String id, int nutrition, float saturation) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		Item.Properties properties = new Item.Properties().setId(key);
		if (nutrition > 0) {
			properties.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build());
		}
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
	}

	private static Map<EntityType<?>, List<Supplier<Item>>> sets() {
		if (sets == null) {
			Supplier<Item> heart = () -> HEART, liver = () -> LIVER, kidney = () -> KIDNEY, lung = () -> LUNG, tripe = () -> TRIPE;
			Supplier<Item> silk = () -> SILK_GLAND, venom = () -> VENOM_GLAND, gas = () -> GAS_BLADDER, powder = () -> POWDER_GLAND;
			// a cud-chewer's stomach is four chambers: two lots of tripe
			List<Supplier<Item>> ruminant = List.of(heart, liver, kidney, kidney, lung, lung, tripe, tripe);
			List<Supplier<Item>> beast = List.of(heart, liver, kidney, kidney, lung, lung, tripe);
			List<Supplier<Item>> small = List.of(heart, liver, kidney, kidney);
			sets = Map.ofEntries(
				Map.entry(EntityTypes.COW, ruminant), Map.entry(EntityTypes.MOOSHROOM, ruminant), Map.entry(EntityTypes.SHEEP, ruminant),
				Map.entry(EntityTypes.GOAT, ruminant), Map.entry(EntityTypes.LLAMA, ruminant), Map.entry(EntityTypes.TRADER_LLAMA, ruminant),
				Map.entry(EntityTypes.CAMEL, ruminant),
				Map.entry(EntityTypes.HORSE, beast), Map.entry(EntityTypes.DONKEY, beast), Map.entry(EntityTypes.MULE, beast),
				Map.entry(EntityTypes.PIG, beast), Map.entry(EntityTypes.HOGLIN, beast), Map.entry(EntityTypes.WOLF, beast),
				Map.entry(EntityTypes.POLAR_BEAR, beast), Map.entry(EntityTypes.PANDA, beast), Map.entry(EntityTypes.STRIDER, beast),
				Map.entry(EntityTypes.FOX, small), Map.entry(EntityTypes.CAT, small), Map.entry(EntityTypes.OCELOT, small),
				Map.entry(EntityTypes.RABBIT, small), Map.entry(EntityTypes.ARMADILLO, small),
				// a bird's stomach is its gizzard
				Map.entry(EntityTypes.CHICKEN, List.of(heart, liver, tripe)), Map.entry(EntityTypes.PARROT, List.of(heart, liver)),
				Map.entry(EntityTypes.TURTLE, List.of(heart, liver, lung, lung)), Map.entry(EntityTypes.FROG, List.of(heart, liver)),
				Map.entry(EntityTypes.SPIDER, List.of(silk, silk)),
				Map.entry(EntityTypes.CAVE_SPIDER, List.of(silk, venom)),
				Map.entry(EntityTypes.BEE, List.of(venom)),
				Map.entry(EntityTypes.CREEPER, List.of(powder)),
				Map.entry(EntityTypes.GHAST, List.of(gas, gas)));
		}
		return sets;
	}

	/** Whether {@code type} is a monster with insides worth taking ({@code corpse/Gutting} guts these too, not only animals). */
	public static boolean gutsMonster(@Nullable EntityType<?> type) {
		return type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER || type == EntityTypes.CREEPER || type == EntityTypes.GHAST;
	}

	/** The organs of a body of {@code type} (by its size if nothing lists it: {@code small} one-handed, {@code big} too big to lift). */
	public static List<Item> of(@Nullable EntityType<?> type, boolean small, boolean big) {
		List<Supplier<Item>> set = type == null ? null : sets().get(type);
		List<Item> out = new ArrayList<>();
		for (Supplier<Item> organ : set != null ? set : bySize(small, big)) {
			out.add(organ.get());
		}
		return out;
	}

	private static List<Supplier<Item>> bySize(boolean small, boolean big) {
		Supplier<Item> heart = () -> HEART, liver = () -> LIVER, kidney = () -> KIDNEY, lung = () -> LUNG, tripe = () -> TRIPE;
		return small ? List.of(heart, liver) : big ? List.of(heart, liver, kidney, kidney, lung, lung, tripe, tripe) : List.of(heart, liver, kidney, kidney, lung, lung, tripe);
	}

	/** The share of {@code organs} that comes out with piece {@code n} of {@code pieces} (the last piece takes what's left). */
	public static List<Item> piece(List<Item> organs, int n, int pieces) {
		int each = Math.max(1, (organs.size() + pieces - 1) / pieces);
		int from = Math.min(organs.size(), n * each), to = n >= pieces - 1 ? organs.size() : Math.min(organs.size(), from + each);
		return organs.subList(from, to);
	}
}
