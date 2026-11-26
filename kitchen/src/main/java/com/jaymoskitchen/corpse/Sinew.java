package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

/**
 * Sinew: the tough cord a butcher pulls from a big animal's legs and back when he dresses it properly (on the block, on
 * the hook, or by a town butcher; {@link ButcheringBonus#roll}). It does the work of string: it is in the item tag
 * {@code c:strings} (so a recipe that takes {@code #c:strings} takes it) and a 1-to-1 crafting recipe turns it into
 * string for the ones that ask for string itself, bows and the loom's thread.
 */
public final class Sinew {
	/** The chance a carcass gives sinew, and at most how many pieces (the big beasts more than the small). */
	private record Yield(float chance, int max) {
	}

	/** Which animals give sinew: the hoofed beasts and the hog, never a bird or a rabbit. */
	private static final java.util.Map<EntityType<?>, Yield> YIELDS = java.util.Map.ofEntries(
		java.util.Map.entry(EntityTypes.COW, new Yield(0.5F, 2)),
		java.util.Map.entry(EntityTypes.MOOSHROOM, new Yield(0.5F, 2)),
		java.util.Map.entry(EntityTypes.HORSE, new Yield(0.6F, 3)),
		java.util.Map.entry(EntityTypes.DONKEY, new Yield(0.5F, 2)),
		java.util.Map.entry(EntityTypes.MULE, new Yield(0.5F, 2)),
		java.util.Map.entry(EntityTypes.LLAMA, new Yield(0.5F, 2)),
		java.util.Map.entry(EntityTypes.CAMEL, new Yield(0.6F, 3)),
		java.util.Map.entry(EntityTypes.GOAT, new Yield(0.4F, 1)),
		java.util.Map.entry(EntityTypes.SHEEP, new Yield(0.4F, 1)),
		java.util.Map.entry(EntityTypes.PIG, new Yield(0.3F, 1)),
		java.util.Map.entry(EntityTypes.HOGLIN, new Yield(0.4F, 2)));

	/** Strings of every kind (the conventional tag, which minecraft:string is in). */
	public static final TagKey<Item> STRINGS = TagKey.create(Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "strings"));

	public static Item SINEW;

	private Sinew() {
	}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("sinew"));
		SINEW = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(SINEW));
	}

	/** The sinew a carcass of {@code type} gives, or nothing (it comes only sometimes). */
	public static ItemStack roll(@Nullable EntityType<?> type, RandomSource random) {
		Yield yield = type == null ? null : YIELDS.get(type);
		if (yield == null || SINEW == null || random.nextFloat() >= yield.chance()) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(SINEW, 1 + random.nextInt(yield.max()));
	}
}
