package com.jaymoskitchen.butchery;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The fat of a monster (Hunter's Road, patch 7, "D4"): a hunter's blade oils are rendered from it. A grown monster of one of the four
 * kinds the oils bite (Jaymo's Kingdoms' {@code jaymoskingdoms:kind/<kind>} entity tags: the dead, beasts, brutes, people) gives one
 * lump of its kind's fat when it is gutted with a knife ({@code corpse/Gutting}: carved, never dropped by a kill), and the fat
 * rendered down on a campfire, in a furnace or in a cooking pot is its kind's grease (as animal fat is rendered into tallow,
 * {@link com.jaymoskitchen.tanning.Tanning}). Jaymo's Powers' crucible turns the grease, with essences, into the oils. A creature
 * of {@link #NEVER} (villagers, players) gives none.
 */
public final class MonsterFat {
	/** Creatures that are never carved for fat (a data tag). */
	public static final TagKey<EntityType<?>> NEVER = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("no_fat"));
	/** The four kinds, in the order that decides a creature of two (the heaviest first). */
	private static final List<String> KINDS = List.of("brutes", "beasts", "dead", "people");

	public static Item DEAD_FAT, BEAST_FAT, BRUTE_FAT, FOLK_FAT;
	public static Item DEAD_GREASE, BEAST_GREASE, BRUTE_GREASE, FOLK_GREASE;

	private MonsterFat() {
	}

	static void register() {
		DEAD_FAT = item("dead_fat");
		BEAST_FAT = item("beast_fat");
		BRUTE_FAT = item("brute_fat");
		FOLK_FAT = item("folk_fat");
		DEAD_GREASE = item("dead_grease");
		BEAST_GREASE = item("beast_grease");
		BRUTE_GREASE = item("brute_grease");
		FOLK_GREASE = item("folk_grease");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(DEAD_FAT, BEAST_FAT, BRUTE_FAT, FOLK_FAT, DEAD_GREASE, BEAST_GREASE, BRUTE_GREASE, FOLK_GREASE)) {
				output.accept(new ItemStack(item));
			}
		});
	}

	private static Item item(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	/** The fat carved from a monster of {@code type}, or null if it is of none of the four kinds (or is never carved). */
	public static @Nullable Item of(@Nullable EntityType<?> type) {
		if (type == null || DEAD_FAT == null || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(NEVER)) {
			return null;
		}
		for (String kind : KINDS) {
			TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("jaymoskingdoms", "kind/" + kind));
			if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(tag)) {
				return switch (kind) {
					case "brutes" -> BRUTE_FAT;
					case "beasts" -> BEAST_FAT;
					case "dead" -> DEAD_FAT;
					default -> FOLK_FAT;
				};
			}
		}
		return null;
	}
}
