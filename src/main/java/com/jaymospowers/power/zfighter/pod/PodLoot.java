package com.jaymospowers.power.zfighter.pod;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What the crossing gives a pilot: the ore a rock broke off a world holds (the rock keeps its world, so a rock field in the
 * dark between Cinder and Home gives Cinder's iron and diamond and Home's coal), what stripping a derelict pod gets (a keel,
 * plates, a tank: keels are found, never made, so a derelict is another place to find one), and what a beaten raider leaves.
 * Everything goes straight to the pilot's pack (or at their feet when it is full).
 */
public final class PodLoot {
	// ---- tuning ----
	/** The odds a stripped derelict gives its keel (the others always give plates and a tank). */
	static final float KEEL_CHANCE = 0.4F;
	/** A stripped derelict's plates (how many), and the share of its parts that are worn. */
	static final int PLATES_MIN = 1, PLATES_MAX = 3;
	static final float WORN_SHARE = 0.6F;
	/** What a beaten raider leaves: scrap, and blaze powder (a chief leaves more). */
	static final int RAIDER_SCRAP = 3, RAIDER_FUEL = 1, CHIEF_FUEL = 3;

	/** A world's ores as item ids, the common ones first; the last is the rare one. A name that is not an item (a mod missing) is left out. */
	private static final List<List<String>> ORES = List.of(
		// the moon
		List.of("minecraft:raw_iron", "minecraft:quartz", "jaymospowers:moon_rock"),
		// Rustfall
		List.of("minecraft:raw_iron", "minecraft:raw_copper", "minecraft:diamond"),
		// Glacia
		List.of("minecraft:lapis_lazuli", "jaymospowers:glacia_salt", "minecraft:emerald"),
		// Cinder
		List.of("minecraft:raw_iron", "minecraft:redstone", "minecraft:diamond", "minecraft:emerald", "jaymospowers:cinder_ember"),
		// the Maw
		List.of("minecraft:iron_nugget", "minecraft:raw_gold", "minecraft:netherite_scrap"),
		// the Verdant World
		List.of("minecraft:coal", "minecraft:raw_copper", "minecraft:emerald"),
		// the future
		List.of("minecraft:raw_iron", "minecraft:raw_copper"),
		// the giant's ring
		List.of("minecraft:amethyst_shard", "jaymospowers:gravilite_shards"));
	/** Home's own ores. */
	private static final List<String> HOME = List.of("minecraft:coal", "minecraft:raw_iron", "minecraft:raw_copper", "minecraft:raw_gold", "minecraft:diamond");

	private PodLoot() {
	}

	private static List<String> oresOf(int world) {
		return world >= 0 && world < ORES.size() ? ORES.get(world) : HOME;
	}

	private static @Nullable Item item(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
	}

	/** {@code count} ores from the world {@code world}'s rock: weighted so the first listed are common and the last is rare. */
	static void ore(ServerPlayer player, int world, int count, RandomSource random) {
		List<String> ores = oresOf(world);
		for (int i = 0; i < count; i++) {
			// squared, so the low indexes come up most
			int pick = (int)Math.floor(random.nextFloat() * random.nextFloat() * ores.size());
			Item item = item(ores.get(Math.min(ores.size() - 1, pick)));
			give(player, new ItemStack(item == null ? Items.RAW_IRON : item));
		}
	}

	/** A derelict pod stripped: its keel (sometimes), plates and its tank, mostly worn. */
	static void strip(ServerPlayer player, RandomSource random) {
		if (random.nextFloat() < KEEL_CHANCE) {
			give(player, PodParts.stackOf(PodPart.KEEL, true, 0, 0, PodWrecks.WRECK_SOCKETS));
		}
		int plates = PLATES_MIN + random.nextInt(PLATES_MAX - PLATES_MIN + 1);
		for (int i = 0; i < plates; i++) {
			give(player, PodParts.itemOf(PodPart.PLATE, random.nextFloat() < WORN_SHARE));
		}
		give(player, PodParts.itemOf(PodPart.TANK, random.nextFloat() < WORN_SHARE));
	}

	/** A beaten raider's wreckage: scrap and blaze powder. */
	static void raider(ServerPlayer player, boolean chief) {
		give(player, PodParts.scrapOf(null, RAIDER_SCRAP));
		give(player, new ItemStack(Items.BLAZE_POWDER, chief ? CHIEF_FUEL : RAIDER_FUEL));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}
}
