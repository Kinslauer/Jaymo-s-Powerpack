package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.19) What a fiefdom is for: it lives to feed and supply the kingdom it serves, not to hold out against the world.
 * A <b>mining</b> fiefdom (in the hills and mountains, or by its founding) works its mine with more miners and sends
 * stone and ore; a <b>farming</b> one (on the plains, the savanna, by rivers) lays out more fields and pens, keeps more
 * farmers and sends food. Either way it builds no stone wall, towers or barracks, and keeps fewer guards; its surplus
 * goes to its overlord by caravan ({@link Caravans}). Drawn from its land and id, so nothing is saved.
 */
final class Focus {
	enum Kind {
		MINING, FARMING;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Buildings a fiefdom doesn't put up (its defences: the wall round it, its towers and gatehouses, the barracks). */
	static final Set<String> NO_DEFENCES = Set.of("wall", "tower", "gatehouse", "barracks");
	/** A farming fiefdom's fields and pens, at most (its growth order's own counts raised to these, as it grows). */
	static final int FARMING_FARMS = 6, FARMING_PENS = 3;
	/** A mining fiefdom keeps only this many fields and pens (it's fed from its overlord's surplus). */
	static final int MINING_FARMS = 1, MINING_PENS = 1;
	/** A mining fiefdom's miners per mine (others: {@link Gatherers#MINERS_PER_MINE}). */
	static final int MINING_MINERS = 5;
	/** A fiefdom keeps at most this many guards; the rest of its hands work. */
	static final int FIEF_GUARDS = 4;
	/** Production, in percent: a mining fiefdom's stone and logs, a farming one's food (and the most food it can make a day). */
	static final int MINING_MATERIALS = 160, FARMING_FOOD = 200, FARMING_FOOD_MOST = 256;
	/** A mining fiefdom digs one ore for every this many stone it makes in a day. */
	static final int ORE_ONE_IN = 6;

	/** The goods each kind sends its overlord. */
	static final Set<Item> MINING_GOODS = Set.of(Items.COBBLESTONE, Items.STONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
		Items.TUFF, Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD, Items.COAL, Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT, Items.REDSTONE,
		Items.LAPIS_LAZULI, Items.DIAMOND, Items.FLINT, Items.GRAVEL);
	/** The ores a mining fiefdom's production turns up (weighted by repeats). */
	static final Item[] ORES = {Items.COAL, Items.COAL, Items.COAL, Items.RAW_IRON, Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD, Items.REDSTONE,
		Items.LAPIS_LAZULI};

	private static final Map<Integer, Kind> KNOWN = new HashMap<>();

	private Focus() {
	}

	static void clear() {
		KNOWN.clear();
	}

	/** {@code f}'s focus; null for anything but a fiefdom. */
	static @Nullable Kind of(MinecraftServer server, @Nullable Faction f) {
		if (f == null || f.type() != FactionType.FIEFDOM) {
			return null;
		}
		return KNOWN.computeIfAbsent(f.id(), id -> draw(Factions.level(server), f));
	}

	/** From its land: hills, mountains and badlands mine, open grassland farms; elsewhere its id decides. */
	private static Kind draw(ServerLevel level, Faction f) {
		Holder<Biome> biome = level.getBiome(f.seat());
		if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || biome.is(BiomeTags.IS_BADLANDS)) {
			return Kind.MINING;
		}
		if (biome.is(BiomeTags.IS_SAVANNA) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.HAS_VILLAGE_PLAINS)) {
			return Kind.FARMING;
		}
		long mix = f.id() * 0x9E3779B97F4A7C15L ^ level.getSeed();
		return Kind.values()[(int)Math.floorMod(mix ^ (mix >>> 31), 2L)];
	}

	static Component describe(Kind k) {
		return Component.translatable("focus.jaymoskingdoms." + k.key());
	}

	// ---- its growth --------------------------------------------------------------------------------------------------

	/** The most of {@code entry} {@code f} builds (the growth order's {@code max}, or its focus' own count). */
	static int max(MinecraftServer server, Faction f, String entry, int max) {
		Kind k = of(server, f);
		if (k == null) {
			return max;
		}
		if (NO_DEFENCES.contains(entry)) {
			return 0;
		}
		return switch (entry) {
			case "farm" -> k == Kind.FARMING ? Math.max(max, Math.min(FARMING_FARMS, max * 2 + 1)) : Math.min(max, MINING_FARMS);
			case "pen" -> k == Kind.FARMING ? Math.max(max, Math.min(FARMING_PENS, max + 1)) : Math.min(max, MINING_PENS);
			default -> max;
		};
	}

	/** It never walls itself in. */
	static boolean walled(MinecraftServer server, Faction f) {
		return of(server, f) == null;
	}

	/** Miners its mine takes. */
	static int miners(MinecraftServer server, Faction f, int usual) {
		return of(server, f) == Kind.MINING ? MINING_MINERS : usual;
	}

	/** A fiefdom with its guards already: its next hand works instead (the role given, or a guard if there's no work for him). */
	static boolean enoughGuards(MinecraftServer server, Faction f) {
		return of(server, f) != null && f.holdings().count(Folk.GUARD) >= FIEF_GUARDS;
	}

	// ---- its production ----------------------------------------------------------------------------------------------

	static int materials(MinecraftServer server, Faction f, int materials) {
		return of(server, f) == Kind.MINING ? materials * MINING_MATERIALS / 100 : materials;
	}

	static int food(MinecraftServer server, Faction f, int food) {
		return of(server, f) == Kind.FARMING ? food * FARMING_FOOD / 100 : food;
	}

	static int foodMost(MinecraftServer server, Faction f, int most) {
		return of(server, f) == Kind.FARMING ? FARMING_FOOD_MOST : most;
	}

	/** The ores a mining fiefdom turns up with {@code stone} of rock: into its storage; returns what didn't fit. */
	static int ores(ServerLevel level, Faction f, int stone) {
		if (of(level.getServer(), f) != Kind.MINING) {
			return 0;
		}
		int lost = 0;
		for (int i = 0; i < stone / ORE_ONE_IN; i++) {
			lost += Storage.put(level, f, ORES[level.getRandom().nextInt(ORES.length)], 1);
		}
		return lost;
	}

	/** The goods {@code k} sends its overlord (and the return goods it wants: the other kind's). */
	static boolean goods(Kind k, net.minecraft.world.item.ItemStack stack) {
		return k == Kind.MINING ? MINING_GOODS.contains(stack.getItem()) : Storage.isFood(stack) || stack.is(Items.WHEAT) || stack.is(Items.LEATHER)
			|| stack.is(net.minecraft.tags.ItemTags.WOOL) || stack.is(Items.EGG) || stack.is(Items.SUGAR_CANE);
	}
}
