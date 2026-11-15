package com.jaymoskitchen.stomach;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Eat widely, grow stronger (the GT New Horizons picks, patch 2, after Hunger Overhaul's and Spice of Life's food
 * variety): each kind of food eaten for the first time adds points as many as it fills; every {@link #POINTS_PER_HEART}
 * points is a heart more for good ({@link Stomach#apply} adds it), up to {@link #MOST_HEARTS}. Kept through death,
 * and synced to its owner for the food journal ({@link FoodJournalItem}), which lists what's been eaten and what not.
 */
public final class Variety {
	public static final int POINTS_PER_HEART = 50, MOST_HEARTS = 10;

	/** The kinds of food eaten, in the order they were first eaten, and the points they came to. */
	public record Eaten(List<Item> foods, int points) {
		static final Codec<Eaten> CODEC = RecordCodecBuilder.create(i -> i.group(
			// ids, not items: a food whose mod was taken out is dropped from the list, not the whole list lost
			Identifier.CODEC.listOf().xmap(
				ids -> ids.stream().flatMap(id -> BuiltInRegistries.ITEM.getOptional(id).stream()).toList(),
				items -> items.stream().map(BuiltInRegistries.ITEM::getKey).toList()).fieldOf("foods").forGetter(Eaten::foods),
			Codec.INT.fieldOf("points").forGetter(Eaten::points)
		).apply(i, Eaten::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Eaten> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list()), Eaten::foods,
			ByteBufCodecs.VAR_INT, Eaten::points,
			Eaten::new);
	}

	public static final AttachmentType<Eaten> EATEN = AttachmentRegistry.create(JaymosKitchen.id("foods_eaten"),
		builder -> builder.persistent(Eaten.CODEC).copyOnDeath().syncWith(Eaten.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private Variety() {
	}

	/** The food journal ({@link FoodJournalItem}). */
	public static Item FOOD_JOURNAL;

	/** At init: the journal, and the attachment type before any world loads (dedicated servers need it). */
	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("food_journal"));
		FOOD_JOURNAL = Registry.register(BuiltInRegistries.ITEM, key, new FoodJournalItem(new Item.Properties().stacksTo(1).setId(key)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(new ItemStack(FOOD_JOURNAL)));
	}

	/** {@code stack} (still whole) was just eaten: a kind not eaten before counts. */
	static void eaten(ServerPlayer player, ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		Eaten was = player.getAttached(EATEN);
		List<Item> foods = was == null ? List.of() : was.foods();
		if (food == null || foods.contains(stack.getItem())) {
			return;
		}
		List<Item> now = new ArrayList<>(foods);
		now.add(stack.getItem());
		player.setAttached(EATEN, new Eaten(List.copyOf(now), (was == null ? 0 : was.points()) + Math.max(0, food.nutrition())));
	}

	/** The kinds of food {@code player} has eaten (either side, for themselves). */
	public static List<Item> foods(Player player) {
		Eaten eaten = player.getAttached(EATEN);
		return eaten == null ? List.of() : eaten.foods();
	}

	/** The hearts a varied diet has given {@code player}. */
	public static int hearts(Player player) {
		Eaten eaten = player.getAttached(EATEN);
		return eaten == null ? 0 : Math.min(MOST_HEARTS, eaten.points() / POINTS_PER_HEART);
	}
}
